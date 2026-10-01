package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.SquareType;
import fi.bizhop.finanssi2.game.engine.GameCommand.BuyCar;
import fi.bizhop.finanssi2.game.engine.GameCommand.EndTurn;
import fi.bizhop.finanssi2.game.engine.GameCommand.Roll;
import fi.bizhop.finanssi2.game.engine.GameCommand.SellCar;
import fi.bizhop.finanssi2.game.engine.GameEvent.CarBought;
import fi.bizhop.finanssi2.game.engine.GameEvent.CarSold;
import fi.bizhop.finanssi2.game.engine.GameEvent.DiceRolled;
import fi.bizhop.finanssi2.game.engine.GameEvent.LandedOn;
import fi.bizhop.finanssi2.game.engine.GameEvent.MoneyTransferred;
import fi.bizhop.finanssi2.game.engine.GameEvent.NotImplemented;
import fi.bizhop.finanssi2.game.engine.GameEvent.PieceMoved;
import fi.bizhop.finanssi2.game.engine.GameEvent.TurnEnded;
import fi.bizhop.finanssi2.game.engine.GameEvent.TurnStarted;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static fi.bizhop.finanssi2.game.engine.EngineTests.ENGINE;
import static fi.bizhop.finanssi2.game.engine.EngineTests.assertRejected;
import static fi.bizhop.finanssi2.game.engine.EngineTests.roll;
import static fi.bizhop.finanssi2.game.engine.EngineTests.send;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEngineTest {
    @Test
    void testEveryCommandHasATiming() {
        var commands = Arrays.stream(GameCommand.class.getPermittedSubclasses()).collect(Collectors.toSet());
        assertEquals(commands, GameEngine.TIMING.keySet());
        assertEquals(commands, ENGINE.candidateCommands.stream().map(Object::getClass).collect(Collectors.toSet()));
    }

    @Test
    void testOneDieWithoutCar() {
        var state = TestGame.players("a", "b").state();
        state.getFinanceNewsDeck().add("FL-05");

        var events = roll(state, 4);

        assertEquals(List.of(
                new DiceRolled("a", List.of(4)),
                new PieceMoved("a", 1, 5),
                new LandedOn("a", 5),
                new GameEvent.FinanceNewsDrawn("a", "FL-05", null)), events);
        assertEquals(5, state.current().getPosition());
        assertEquals(TurnPhase.AFTER_ROLL, state.getPhase());
    }

    @Test
    void testTwoDiceWithCar() {
        var state = TestGame.players("a", "b").car("a").state();

        var events = roll(state, 3, 4);

        assertEquals(new DiceRolled("a", List.of(3, 4)), events.getFirst());
        assertEquals(8, state.current().getPosition());
    }

    @Test
    void testOneDieInsideBank() {
        for (var start : List.of(34, 37, 45)) {
            var state = TestGame.players("a", "b").car("a").at("a", start).state();
            assertEquals(new DiceRolled("a", List.of(1)), roll(state, 1).getFirst(), "from " + start);
        }
    }

    @Test
    void testStopsOnBankEntrance() {
        var state = TestGame.players("a", "b").car("a").at("a", 30).state();

        var events = roll(state, 6, 5, 1, 1);

        assertEquals(List.of(new DiceRolled("a", List.of(6, 5)), new PieceMoved("a", 30, 34), new LandedOn("a", 34)),
                events.subList(0, 3));
    }

    @Test
    void testStopsOnBankExitWhenLeavingTheBank() {
        var state = TestGame.players("a", "b").at("a", 44).state();

        var events = roll(state, 6);

        assertEquals(List.of(new DiceRolled("a", List.of(6)), new PieceMoved("a", 44, 1), new LandedOn("a", 1)), events);
    }

    @Test
    void testExactRollToLastSquareAndWrap() {
        var state = TestGame.players("a", "b").at("a", 45).state();
        roll(state, 1);
        assertEquals(46, state.current().getPosition());

        var wrap = TestGame.players("a", "b").at("a", 46).state();
        roll(wrap, 3);
        assertEquals(1, wrap.current().getPosition());
    }

    @Test
    void testRollingTwice() {
        assertRejected(RuleViolation.class, TestGame.players("a", "b").afterRoll().state(), "a", new Roll());
    }

    @Test
    void testEndingTurnBeforeRolling() {
        assertRejected(RuleViolation.class, TestGame.players("a", "b").state(), "a", new EndTurn());
    }

    @Test
    void testActingOutOfTurn() {
        var state = TestGame.players("a", "b").state();
        assertRejected(NotYourTurn.class, state, "b", new Roll());
        assertRejected(NotYourTurn.class, state, "b", new SellCar());
        assertRejected(NotYourTurn.class, state, "x", new Roll());
        assertEquals(List.of("Resign"), ENGINE.allowedCommands(state, "b"));
    }

    @Test
    void testEndTurnPassesToNextPlayer() {
        var state = TestGame.players("a", "b", "c").afterRoll().state();

        var events = send(state, "a", new EndTurn());

        assertEquals(List.of(new TurnEnded("a"), new TurnStarted("b")), events);
        assertEquals("b", state.getCurrentPlayer());
        assertEquals(TurnPhase.BEFORE_ROLL, state.getPhase());
    }

    @Test
    void testTurnOrderSkipsPlayersWhoAreOut() {
        var state = TestGame.players("a", "b", "c", "d").out("d").out("a").turn("c").afterRoll().state();

        var events = send(state, "c", new EndTurn());

        assertEquals(List.of(new TurnEnded("c"), new TurnStarted("b")), events);
    }

    @Test
    void testBuyCarBeforeRolling() {
        var state = TestGame.players("a", "b").state();

        var events = send(state, "a", new BuyCar());

        assertEquals(List.of(new MoneyTransferred("a", null, 50_000, MoneyReason.CAR_PURCHASE), new CarBought("a")), events);
        assertTrue(state.current().isCar());
        assertEquals(25_000, state.current().getCash());
        assertEquals(TurnPhase.BEFORE_ROLL, state.getPhase());
        assertEquals(List.of("Resign", "Roll", "SellCar", "TakeLoan"), ENGINE.allowedCommands(state, "a"));
    }

    @Test
    void testBuyCarRejected() {
        assertRejected(RuleViolation.class, TestGame.players("a", "b").afterRoll().state(), "a", new BuyCar());
        assertRejected(RuleViolation.class, TestGame.players("a", "b").car("a").state(), "a", new BuyCar());
        assertRejected(RuleViolation.class, TestGame.players("a", "b").cash("a", 49_500).state(), "a", new BuyCar());

        var exactCash = TestGame.players("a", "b").cash("a", 50_000).state();
        send(exactCash, "a", new BuyCar());
        assertEquals(0, exactCash.current().getCash());
    }

    @Test
    void testSellCarBeforeAndAfterRolling() {
        for (var game : List.of(TestGame.players("a", "b").car("a"), TestGame.players("a", "b").car("a").afterRoll())) {
            var state = game.state();
            var phase = state.getPhase();

            var events = send(state, "a", new SellCar());

            assertEquals(List.of(new CarSold("a"), new MoneyTransferred(null, "a", 25_000, MoneyReason.CAR_SALE)), events);
            assertFalse(state.current().isCar());
            assertEquals(100_000, state.current().getCash());
            assertEquals(phase, state.getPhase());
        }
        assertRejected(RuleViolation.class, TestGame.players("a", "b").state(), "a", new SellCar());
    }

    @Test
    void testAllowedCommands() {
        assertEquals(List.of("BuyCar", "Resign", "Roll", "TakeLoan"), ENGINE.allowedCommands(TestGame.players("a", "b").state(), "a"));
        assertEquals(List.of("Resign", "Roll", "TakeLoan"), ENGINE.allowedCommands(TestGame.players("a", "b").cash("a", 0).state(), "a"));
        assertEquals(List.of("EndTurn", "Resign", "TakeLoan"), ENGINE.allowedCommands(TestGame.players("a", "b").afterRoll().state(), "a"));
        assertEquals(List.of("EndTurn", "RepayLoan", "Resign", "SellCar", "TakeLoan"),
                ENGINE.allowedCommands(TestGame.players("a", "b").car("a").loans("a", 1).afterRoll().state(), "a"));
    }
}
