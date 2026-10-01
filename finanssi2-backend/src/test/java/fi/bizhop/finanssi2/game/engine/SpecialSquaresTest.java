package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.SquareType;
import fi.bizhop.finanssi2.game.engine.GameCommand.DeclareBankruptcy;
import fi.bizhop.finanssi2.game.engine.GameCommand.EndTurn;
import fi.bizhop.finanssi2.game.engine.GameCommand.Pay;
import fi.bizhop.finanssi2.game.engine.GameCommand.TakeLoan;
import fi.bizhop.finanssi2.game.engine.GameEvent.BankDividend;
import fi.bizhop.finanssi2.game.engine.GameEvent.DiceRolled;
import fi.bizhop.finanssi2.game.engine.GameEvent.GoToJailRoll;
import fi.bizhop.finanssi2.game.engine.GameEvent.JailExempt;
import fi.bizhop.finanssi2.game.engine.GameEvent.JailRoll;
import fi.bizhop.finanssi2.game.engine.GameEvent.LandedOn;
import fi.bizhop.finanssi2.game.engine.GameEvent.MoneyTransferred;
import fi.bizhop.finanssi2.game.engine.GameEvent.NotImplemented;
import fi.bizhop.finanssi2.game.engine.GameEvent.PaymentDue;
import fi.bizhop.finanssi2.game.engine.GameEvent.PieceMoved;
import fi.bizhop.finanssi2.game.engine.GameEvent.PlayerBankrupt;
import fi.bizhop.finanssi2.game.engine.GameEvent.PlayerDividendCharged;
import fi.bizhop.finanssi2.game.engine.GameEvent.RentCharged;
import fi.bizhop.finanssi2.game.engine.GameEvent.TurnEnded;
import fi.bizhop.finanssi2.game.engine.GameEvent.TurnSkipped;
import fi.bizhop.finanssi2.game.engine.GameEvent.TurnStarted;
import fi.bizhop.finanssi2.game.engine.PendingDecision.RaiseFunds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static fi.bizhop.finanssi2.game.engine.EngineTests.GAME_DATA;
import static fi.bizhop.finanssi2.game.engine.EngineTests.roll;
import static fi.bizhop.finanssi2.game.engine.EngineTests.send;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialSquaresTest {
    static PlayerState player(GameState state, String uid) {
        return state.player(uid).orElseThrow();
    }

    static int rent(int square) {
        return GAME_DATA.titleDeed(square).rent().unbuilt();
    }

    @Test
    void testMoveFrom2To21() {
        var state = TestGame.players("a", "b").owns("b", 21).state();

        var events = roll(state, 1);

        assertEquals(List.of(
                new DiceRolled("a", List.of(1)),
                new PieceMoved("a", 1, 2),
                new LandedOn("a", 2),
                new PieceMoved("a", 2, 21),
                new LandedOn("a", 21),
                new RentCharged("a", "b", 21, rent(21), false),
                new MoneyTransferred("a", "b", rent(21), MoneyReason.RENT)), events);
        assertEquals(21, player(state, "a").getPosition());
    }

    @Test
    void testMoveFrom37To46() {
        var state = TestGame.players("a", "b").ownsShares("a", "OS-KASITEOLLISUUS-1").at("a", 34).state();

        var events = roll(state, 3);

        assertEquals(List.of(
                new PieceMoved("a", 37, 46),
                new LandedOn("a", 46),
                new BankDividend("a", 46, List.of("OS-KASITEOLLISUUS-1"), 10_000),
                new MoneyTransferred(null, "a", 10_000, MoneyReason.BANK_DIVIDEND),
                new NotImplemented("a", 46, SquareType.BOND_PURCHASE_AND_DIVIDEND)), events.subList(3, events.size()));
        assertEquals(46, player(state, "a").getPosition());
    }

    @Test
    void testMoveBackFrom44To30() {
        var state = TestGame.players("a", "b").owns("b", 30).at("a", 43).state();

        var events = roll(state, 1);

        assertEquals(List.of(
                new PieceMoved("a", 44, 30),
                new LandedOn("a", 30),
                new RentCharged("a", "b", 30, rent(30), false),
                new MoneyTransferred("a", "b", rent(30), MoneyReason.RENT)), events.subList(3, events.size()));
    }

    @Test
    void testJailDurations() {
        var expected = List.of(1, 1, 2, 2, 3, 3);
        for (int die = 1; die <= 6; die++) {
            var state = TestGame.players("a", "b").at("a", 23).state();

            var events = roll(state, 1, die);

            assertEquals(new JailRoll("a", die, expected.get(die - 1)), events.getLast());
            assertEquals(expected.get(die - 1), player(state, "a").getMissedTurns());
        }
    }

    @Test
    void testSkippedTurns() {
        var state = TestGame.players("a", "b", "c").at("a", 24).missedTurns("a", 2).turn("c").afterRoll().state();

        assertEquals(List.of(new TurnEnded("c"), new TurnSkipped("a", 1), new TurnStarted("b")), send(state, "c", new EndTurn()));
        assertFalse(player(state, "a").isJailExemption());

        state.setPhase(TurnPhase.AFTER_ROLL);
        assertEquals(List.of(new TurnEnded("b"), new TurnStarted("c")), send(state, "b", new EndTurn()));

        state.setPhase(TurnPhase.AFTER_ROLL);
        assertEquals(List.of(new TurnEnded("c"), new TurnSkipped("a", 0), new TurnStarted("b")), send(state, "c", new EndTurn()));
        assertTrue(player(state, "a").isJailExemption());

        state.setPhase(TurnPhase.AFTER_ROLL);
        send(state, "b", new EndTurn());
        state.setPhase(TurnPhase.AFTER_ROLL);
        assertEquals(List.of(new TurnEnded("c"), new TurnStarted("a")), send(state, "c", new EndTurn()));
    }

    @Test
    void testTwoPlayersInJail() {
        var state = TestGame.players("a", "b", "c").missedTurns("a", 1).missedTurns("b", 1).turn("c").afterRoll().state();
        assertEquals(List.of(new TurnEnded("c"), new TurnSkipped("a", 0), new TurnSkipped("b", 0), new TurnStarted("c")),
                send(state, "c", new EndTurn()));

        // Everyone in jail: turns are skipped until someone may play
        var both = TestGame.players("a", "b").missedTurns("a", 1).missedTurns("b", 2).afterRoll().state();
        assertEquals(List.of(new TurnEnded("a"), new TurnSkipped("b", 1), new TurnSkipped("a", 0), new TurnSkipped("b", 0),
                new TurnStarted("a")), send(both, "a", new EndTurn()));
    }

    @Test
    void testJailedPlayerAfterLanding() {
        // Landing in jail ends nothing at once; the missed turns start with the player's next turn
        var state = TestGame.players("a", "b").at("a", 23).state();
        roll(state, 1, 1);
        assertEquals(TurnPhase.AFTER_ROLL, state.getPhase());

        assertEquals(List.of(new TurnEnded("a"), new TurnStarted("b")), send(state, "a", new EndTurn()));
        state.setPhase(TurnPhase.AFTER_ROLL);
        assertEquals(List.of(new TurnEnded("b"), new TurnSkipped("a", 0), new TurnStarted("b")), send(state, "b", new EndTurn()));
    }

    @Test
    void testRentCollectedInJail() {
        var state = TestGame.players("a", "b").owns("a", 3).at("a", 24).missedTurns("a", 2).turn("b").at("b", 2).state();

        roll(state, 1);

        assertEquals(75_000 + rent(3), player(state, "a").getCash());
    }

    @Test
    void testGoToJailChance() {
        for (int die = 1; die <= 6; die++) {
            var state = TestGame.players("a", "b").at("a", 35).state();
            var jailed = die <= 2;

            var events = jailed ? roll(state, 1, die, 5) : roll(state, 1, die);

            var expected = jailed
                    ? List.of(new GoToJailRoll("a", die, true), new PieceMoved("a", 36, 24), new LandedOn("a", 24),
                            new JailRoll("a", 5, 3))
                    : List.of(new GoToJailRoll("a", die, false));
            assertEquals(expected, events.subList(3, events.size()), "die " + die);
            assertEquals(jailed ? 24 : 36, player(state, "a").getPosition());
            assertEquals(jailed ? 3 : 0, player(state, "a").getMissedTurns());
        }
    }

    @Test
    void testJailExemption() {
        var state = TestGame.players("a", "b").at("a", 35).jailExemption("a").state();
        assertEquals(List.of(new JailExempt("a")), roll(state, 1).subList(3, 4));
        assertEquals(36, player(state, "a").getPosition());

        // Cleared on square 1
        var reset = TestGame.players("a", "b").at("a", 45).jailExemption("a").state();
        roll(reset, 2);
        assertEquals(1, player(reset, "a").getPosition());
        assertFalse(player(reset, "a").isJailExemption());
    }

    @Test
    void testBankDividendOnAllShares() {
        // 10 000 + 40 000 + a fund share's 12 500
        for (var square : List.of(16, 28, 46)) {
            var state = TestGame.players("a", "b").ownsShares("a", "OS-KASITEOLLISUUS-1", "OS-KEMIA-2", "OS-RAHASTO-25")
                    .ownsShares("b", "OS-KEMIA-1").at("a", square - 1).state();

            var events = roll(state, 1);

            assertEquals(new BankDividend("a", square, List.of("OS-KASITEOLLISUUS-1", "OS-KEMIA-2", "OS-RAHASTO-25"), 62_500),
                    events.get(3), "square " + square);
            assertEquals(75_000 + 62_500, player(state, "a").getCash());
            assertEquals(75_000, player(state, "b").getCash());
        }
    }

    @Test
    void testBankDividendByShareClass() {
        // 40%: Finanssiyhtymä 2 (30 000) and Kemia 2 (40 000); 30%: Liikekeskus 3 (22 500) and Kemia 1 (30 000)
        var shares = new String[] {"OS-FINANSSIYHTYMA-2", "OS-KEMIA-2", "OS-LIIKEKESKUS-3", "OS-KEMIA-1", "OS-KASITEOLLISUUS-1"};

        var state = TestGame.players("a", "b").ownsShares("a", shares).at("a", 38).state();
        assertEquals(List.of(new BankDividend("a", 39, List.of("OS-FINANSSIYHTYMA-2", "OS-KEMIA-2"), 70_000),
                new MoneyTransferred(null, "a", 70_000, MoneyReason.BANK_DIVIDEND)), roll(state, 1).subList(3, 5));

        state = TestGame.players("a", "b").ownsShares("a", shares).at("a", 41).state();
        assertEquals(List.of(new BankDividend("a", 42, List.of("OS-LIIKEKESKUS-3", "OS-KEMIA-1"), 52_500),
                new MoneyTransferred(null, "a", 52_500, MoneyReason.BANK_DIVIDEND)), roll(state, 1).subList(3, 5));
    }

    @Test
    void testNoDividendWithoutShares() {
        // A 20% share on the 40% square, and no shares at all
        var state = TestGame.players("a", "b").ownsShares("a", "OS-KASITEOLLISUUS-1").at("a", 38).state();
        assertEquals(3, roll(state, 1).size());
        assertEquals(75_000, player(state, "a").getCash());
        assertEquals(3, roll(TestGame.players("a", "b").at("a", 15).state(), 1).size());
    }

    /** The rules example: a owns properties in Käsiteollisuus, Finanssiyhtymä and Tekniikka */
    static TestGame dividendExample() {
        return TestGame.players("a", "b", "c").owns("a", 3, 18, 21, 22).at("a", 40)
                // 200 000 in the counted groups; Kemia and the fund share don't count
                .ownsShares("b", "OS-KASITEOLLISUUS-1", "OS-KASITEOLLISUUS-2", "OS-TEKNIIKKA-3", "OS-KEMIA-1", "OS-RAHASTO-20")
                // 225 000
                .ownsShares("c", "OS-FINANSSIYHTYMA-1", "OS-FINANSSIYHTYMA-2", "OS-TEKNIIKKA-1");
    }

    @Test
    void testPlayerDividend() {
        var state = dividendExample().cash("a", 100_000).state();

        var events = roll(state, 1);

        assertEquals(List.of(
                new PlayerDividendCharged("a", "b", 41, List.of("OS-KASITEOLLISUUS-1", "OS-KASITEOLLISUUS-2", "OS-TEKNIIKKA-3"), 40_000),
                new MoneyTransferred("a", "b", 40_000, MoneyReason.PLAYER_DIVIDEND),
                new PlayerDividendCharged("a", "c", 41, List.of("OS-FINANSSIYHTYMA-1", "OS-FINANSSIYHTYMA-2", "OS-TEKNIIKKA-1"), 45_000),
                new MoneyTransferred("a", "c", 45_000, MoneyReason.PLAYER_DIVIDEND)), events.subList(3, events.size()));
        assertEquals(15_000, player(state, "a").getCash());
        assertEquals(115_000, player(state, "b").getCash());
        assertEquals(120_000, player(state, "c").getCash());
    }

    @Test
    void testPlayerDividendInTurnOrderFromPayer() {
        var state = TestGame.players("a", "b", "c").owns("b", 3).at("b", 40).turn("b")
                .ownsShares("a", "OS-KASITEOLLISUUS-1").ownsShares("c", "OS-KASITEOLLISUUS-3").state();

        var shareholders = roll(state, 1).stream()
                .filter(event -> event instanceof PlayerDividendCharged)
                .map(event -> ((PlayerDividendCharged) event).shareholder())
                .toList();

        assertEquals(List.of("c", "a"), shareholders);
    }

    @Test
    void testPlayerDividendShortfall() {
        // Cash for b's 40 000 but not for c's 45 000
        var state = dividendExample().cash("a", 50_000).state();

        var events = roll(state, 1);

        assertEquals(List.of(new MoneyTransferred("a", "b", 40_000, MoneyReason.PLAYER_DIVIDEND),
                new PlayerDividendCharged("a", "c", 41, List.of("OS-FINANSSIYHTYMA-1", "OS-FINANSSIYHTYMA-2", "OS-TEKNIIKKA-1"), 45_000),
                new PaymentDue("a", "c", 45_000)), events.subList(4, events.size()));
        assertEquals(List.of(new RaiseFunds("a", "c", List.of(new Charge(45_000, MoneyReason.PLAYER_DIVIDEND)))),
                state.getPendingDecisions());
    }

    /** b is owed 40 000 and c 10 000; with 30 000 cash a could pay c at once but pays in order */
    static GameState queuedPayments() {
        var state = dividendExample().cash("a", 30_000).state();
        state.share("OS-FINANSSIYHTYMA-1").setOwner(null);
        state.share("OS-FINANSSIYHTYMA-2").setOwner(null);
        state.share("OS-TEKNIIKKA-1").setOwner(null);
        state.share("OS-KASITEOLLISUUS-3").setOwner("c");
        return state;
    }

    @Test
    void testPlayerDividendPaidInOrder() {
        var state = queuedPayments();

        var events = roll(state, 1);

        assertEquals(List.of(new PaymentDue("a", "b", 40_000), new PaymentDue("a", "c", 10_000)),
                events.stream().filter(e -> e instanceof PaymentDue).toList());
        assertEquals(30_000, player(state, "a").getCash());

        send(state, "a", new TakeLoan());
        assertEquals(List.of(new MoneyTransferred("a", "b", 40_000, MoneyReason.PLAYER_DIVIDEND)), send(state, "a", new Pay()));
        assertEquals(List.of(new MoneyTransferred("a", "c", 10_000, MoneyReason.PLAYER_DIVIDEND)), send(state, "a", new Pay()));
        assertTrue(state.getPendingDecisions().isEmpty());
        assertEquals(30_000, player(state, "a").getCash());
    }

    @Test
    void testBankruptcyDropsOtherPayments() {
        // Nothing left to raise funds with: no loans available, every property mortgaged
        var state = queuedPayments();
        state.getProperties().stream().filter(property -> "a".equals(property.getOwner())).forEach(property -> property.setMortgaged(true));
        player(state, "a").setLoans(3);
        player(state, "b").setLoans(3);
        roll(state, 1);

        var events = send(state, "a", new DeclareBankruptcy());

        assertEquals(List.of(new MoneyTransferred("a", "b", 30_000, MoneyReason.BANKRUPTCY), new PlayerBankrupt("a", "b")),
                events.subList(0, 2));
        assertTrue(state.getPendingDecisions().isEmpty());
        assertEquals(75_000, player(state, "c").getCash());
        assertEquals("b", state.getCurrentPlayer());
    }

    @Test
    void testShareCrash() {
        // 10% of 100 000 + a fund share's 50 000
        var state = TestGame.players("a", "b").ownsShares("a", "OS-KEMIA-1", "OS-RAHASTO-20").at("a", 34).state();
        assertEquals(List.of(new MoneyTransferred("a", null, 15_000, MoneyReason.SHARE_CRASH)), roll(state, 1).subList(3, 4));
        assertEquals(60_000, player(state, "a").getCash());

        // Shares in a complete group don't count
        state = TestGame.players("a", "b").ownsGroup("a", "KASITEOLLISUUS").ownsShares("a", "OS-KEMIA-1", "OS-RAHASTO-20").at("a", 34)
                .state();
        roll(state, 1);
        assertEquals(60_000, player(state, "a").getCash());

        // Without shares nothing happens
        assertEquals(3, roll(TestGame.players("a", "b").at("a", 34).state(), 1).size());
    }

    @Test
    void testShareCrashShortfall() {
        var state = TestGame.players("a", "b").ownsShares("a", "OS-TEOLLISUUSKONSERNI-2").cash("a", 10_000).at("a", 34).state();
        assertEquals(List.of(new PaymentDue("a", null, 20_000)), roll(state, 1).subList(3, 4));
    }
}
