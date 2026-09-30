package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.data.GameDataConfig;
import fi.bizhop.finanssi2.game.data.SquareType;
import fi.bizhop.finanssi2.game.engine.GameCommand.BuyCar;
import fi.bizhop.finanssi2.game.engine.GameCommand.EndTurn;
import fi.bizhop.finanssi2.game.engine.GameCommand.Roll;
import fi.bizhop.finanssi2.game.engine.GameCommand.SellCar;
import fi.bizhop.finanssi2.game.engine.GameEvent.CarBought;
import fi.bizhop.finanssi2.game.engine.GameEvent.CarSold;
import fi.bizhop.finanssi2.game.engine.GameEvent.DiceRolled;
import fi.bizhop.finanssi2.game.engine.GameEvent.LandedOn;
import fi.bizhop.finanssi2.game.engine.GameEvent.NotImplemented;
import fi.bizhop.finanssi2.game.engine.GameEvent.PieceMoved;
import fi.bizhop.finanssi2.game.engine.GameEvent.TurnEnded;
import fi.bizhop.finanssi2.game.engine.GameEvent.TurnStarted;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEngineTest {
    static GameData gameData;
    static GameEngine engine;

    @BeforeAll
    static void load() throws IOException {
        gameData = new GameDataConfig().gameData();
        engine = new GameEngine(gameData, new Rules(gameData));
    }

    static List<GameEvent> roll(GameState state, Integer... dice) {
        var scripted = new ScriptedDice(dice);
        var events = engine.handle(state, state.getCurrentPlayer(), new Roll(), scripted);
        assertTrue(scripted.isEmpty(), "dice left over");
        return events;
    }

    /** Asserts that the command is rejected and the state is unchanged */
    static void assertRejected(Class<? extends RuntimeException> expected, TestGame game, String uid, GameCommand command) {
        var before = TestGameCopy.of(game.state());
        assertThrows(expected, () -> engine.handle(game.state(), uid, command, new ScriptedDice()));
        assertEquals(before, TestGameCopy.of(game.state()));
    }

    @Test
    void testEveryCommandHasATiming() {
        var commands = Arrays.stream(GameCommand.class.getPermittedSubclasses()).collect(Collectors.toSet());
        assertEquals(commands, GameEngine.TIMING.keySet());
        assertEquals(commands, GameEngine.SIMPLE_COMMANDS.stream().map(Object::getClass).collect(Collectors.toSet()));
    }

    @Test
    void testOneDieWithoutCar() {
        var state = TestGame.players("a", "b").state();

        var events = roll(state, 4);

        assertEquals(List.of(
                new DiceRolled("a", List.of(4)),
                new PieceMoved("a", 1, 5),
                new LandedOn("a", 5),
                new NotImplemented("a", 5, SquareType.FINANCE_NEWS)), events);
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
        for (var start : List.of(34, 35, 45)) {
            var state = TestGame.players("a", "b").car("a").at("a", start).state();
            assertEquals(new DiceRolled("a", List.of(1)), roll(state, 1).getFirst(), "from " + start);
        }
    }

    @Test
    void testStopsOnBankEntrance() {
        var state = TestGame.players("a", "b").car("a").at("a", 30).state();

        var events = roll(state, 6, 5);

        // Square 34 has a handler (a no-op for now), so no NotImplemented
        assertEquals(List.of(new DiceRolled("a", List.of(6, 5)), new PieceMoved("a", 30, 34), new LandedOn("a", 34)), events);
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
        var game = TestGame.players("a", "b").afterRoll();
        assertRejected(RuleViolation.class, game, "a", new Roll());
    }

    @Test
    void testEndingTurnBeforeRolling() {
        assertRejected(RuleViolation.class, TestGame.players("a", "b"), "a", new EndTurn());
    }

    @Test
    void testActingOutOfTurn() {
        var game = TestGame.players("a", "b");
        assertRejected(NotYourTurn.class, game, "b", new Roll());
        assertRejected(NotYourTurn.class, game, "b", new SellCar());
        assertRejected(NotYourTurn.class, game, "x", new Roll());
        assertEquals(List.of(), engine.allowedCommands(game.state(), "b"));
    }

    @Test
    void testEndTurnPassesToNextPlayer() {
        var state = TestGame.players("a", "b", "c").afterRoll().state();

        var events = engine.handle(state, "a", new EndTurn(), new ScriptedDice());

        assertEquals(List.of(new TurnEnded("a"), new TurnStarted("b")), events);
        assertEquals("b", state.getCurrentPlayer());
        assertEquals(TurnPhase.BEFORE_ROLL, state.getPhase());
    }

    @Test
    void testTurnOrderSkipsPlayersWhoAreOut() {
        var state = TestGame.players("a", "b", "c", "d").out("d").out("a").turn("c").afterRoll().state();

        var events = engine.handle(state, "c", new EndTurn(), new ScriptedDice());

        assertEquals(List.of(new TurnEnded("c"), new TurnStarted("b")), events);
    }

    @Test
    void testBuyCarBeforeRolling() {
        var state = TestGame.players("a", "b").state();

        var events = engine.handle(state, "a", new BuyCar(), new ScriptedDice());

        assertEquals(List.of(new CarBought("a", 50_000)), events);
        assertTrue(state.current().isCar());
        assertEquals(25_000, state.current().getCash());
        assertEquals(TurnPhase.BEFORE_ROLL, state.getPhase());
        assertEquals(List.of("Roll", "SellCar"), engine.allowedCommands(state, "a"));
    }

    @Test
    void testBuyCarRejected() {
        assertRejected(RuleViolation.class, TestGame.players("a", "b").afterRoll(), "a", new BuyCar());
        assertRejected(RuleViolation.class, TestGame.players("a", "b").car("a"), "a", new BuyCar());
        assertRejected(RuleViolation.class, TestGame.players("a", "b").cash("a", 49_500), "a", new BuyCar());

        var exactCash = TestGame.players("a", "b").cash("a", 50_000).state();
        engine.handle(exactCash, "a", new BuyCar(), new ScriptedDice());
        assertEquals(0, exactCash.current().getCash());
    }

    @Test
    void testSellCarBeforeAndAfterRolling() {
        for (var game : List.of(TestGame.players("a", "b").car("a"), TestGame.players("a", "b").car("a").afterRoll())) {
            var state = game.state();
            var phase = state.getPhase();

            var events = engine.handle(state, "a", new SellCar(), new ScriptedDice());

            assertEquals(List.of(new CarSold("a", 25_000)), events);
            assertFalse(state.current().isCar());
            assertEquals(100_000, state.current().getCash());
            assertEquals(phase, state.getPhase());
        }
        assertRejected(RuleViolation.class, TestGame.players("a", "b"), "a", new SellCar());
    }

    @Test
    void testAllowedCommands() {
        assertEquals(List.of("BuyCar", "Roll"), engine.allowedCommands(TestGame.players("a", "b").state(), "a"));
        assertEquals(List.of("Roll"), engine.allowedCommands(TestGame.players("a", "b").cash("a", 0).state(), "a"));
        assertEquals(List.of("EndTurn"), engine.allowedCommands(TestGame.players("a", "b").afterRoll().state(), "a"));
        assertEquals(List.of("EndTurn", "SellCar"),
                engine.allowedCommands(TestGame.players("a", "b").car("a").afterRoll().state(), "a"));
    }

    /** A deep copy of the fields a rejected command must not change */
    record TestGameCopy(String currentPlayer, TurnPhase phase, List<String> turnOrder, Set<String> players) {
        static TestGameCopy of(GameState state) {
            return new TestGameCopy(state.getCurrentPlayer(), state.getPhase(), List.copyOf(state.getTurnOrder()),
                    state.getPlayers().stream().map(PlayerState::toString).collect(Collectors.toSet()));
        }
    }
}
