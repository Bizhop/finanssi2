package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.engine.GameCommand.DeclareBankruptcy;
import fi.bizhop.finanssi2.game.engine.GameCommand.EndTurn;
import fi.bizhop.finanssi2.game.engine.GameCommand.Pay;
import fi.bizhop.finanssi2.game.engine.GameCommand.RepayLoan;
import fi.bizhop.finanssi2.game.engine.GameCommand.Roll;
import fi.bizhop.finanssi2.game.engine.GameCommand.SellCar;
import fi.bizhop.finanssi2.game.engine.GameCommand.TakeLoan;
import fi.bizhop.finanssi2.game.engine.GameEvent.BankEntranceRoll;
import fi.bizhop.finanssi2.game.engine.GameEvent.LoanRepaid;
import fi.bizhop.finanssi2.game.engine.GameEvent.LoanTaken;
import fi.bizhop.finanssi2.game.engine.GameEvent.MoneyTransferred;
import fi.bizhop.finanssi2.game.engine.GameEvent.PaymentDue;
import fi.bizhop.finanssi2.game.engine.GameEvent.PlayerBankrupt;
import fi.bizhop.finanssi2.game.engine.GameEvent.TurnStarted;
import fi.bizhop.finanssi2.game.engine.PendingDecision.RaiseFunds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static fi.bizhop.finanssi2.game.engine.EngineTests.ENGINE;
import static fi.bizhop.finanssi2.game.engine.EngineTests.assertRejected;
import static fi.bizhop.finanssi2.game.engine.EngineTests.roll;
import static fi.bizhop.finanssi2.game.engine.EngineTests.send;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyAndLoansTest {
    static PlayerState player(GameState state, String uid) {
        return state.player(uid).orElseThrow();
    }

    @Test
    void testInterestOnBankExit() {
        for (int loans = 0; loans <= 3; loans++) {
            var state = TestGame.players("a", "b").at("a", 45).loans("a", loans).state();

            var events = roll(state, 2);

            var interest = loans * 5_000;
            assertEquals(1, player(state, "a").getPosition());
            assertEquals(75_000 - interest, player(state, "a").getCash());
            var expected = loans == 0 ? List.of() : List.of(new MoneyTransferred("a", null, interest, MoneyReason.LOAN_INTEREST));
            assertEquals(expected, events.subList(3, events.size()), loans + " loans");
        }
    }

    @Test
    void testTakeAndRepayLoan() {
        var state = TestGame.players("a", "b").state();

        assertEquals(List.of(new LoanTaken("a", 1), new MoneyTransferred(null, "a", 50_000, MoneyReason.LOAN)),
                send(state, "a", new TakeLoan()));
        assertEquals(125_000, player(state, "a").getCash());

        assertEquals(List.of(new MoneyTransferred("a", null, 50_000, MoneyReason.LOAN_REPAYMENT), new LoanRepaid("a", 0)),
                send(state, "a", new RepayLoan()));
        assertEquals(75_000, player(state, "a").getCash());
        assertEquals(0, player(state, "a").getLoans());
    }

    @Test
    void testLoansBeforeAndAfterRolling() {
        var state = TestGame.players("a", "b").afterRoll().state();
        send(state, "a", new TakeLoan());
        send(state, "a", new RepayLoan());
        assertEquals(0, player(state, "a").getLoans());
    }

    @Test
    void testRepayLoanRejected() {
        assertRejected(RuleViolation.class, TestGame.players("a", "b").state(), "a", new RepayLoan());
        var shortOfCash = TestGame.players("a", "b").loans("a", 1).cash("a", 49_500).state();
        assertRejected(RuleViolation.class, shortOfCash, "a", new RepayLoan());
    }

    @Test
    void testThreeLoansPerPlayer() {
        for (var limit : LoanLimit.values()) {
            var state = TestGame.players("a", "b").loanLimit(limit).loans("a", 3).state();
            assertRejected(RuleViolation.class, state, "a", new TakeLoan());
        }
    }

    @Test
    void testOfficialLimitOfSixLoansInTotal() {
        var game = TestGame.players("a", "b", "c").loans("a", 3).loans("b", 2);
        send(game.state(), "a", new RepayLoan());
        send(game.state(), "a", new TakeLoan());
        assertRejected(RuleViolation.class, game.loans("c", 1).state(), "a", new TakeLoan());
        assertFalse(ENGINE.allowedCommands(game.state(), "a").contains("TakeLoan"));
    }

    @Test
    void testUnlimitedBankLoans() {
        var state = TestGame.players("a", "b", "c").loanLimit(LoanLimit.UNLIMITED).loans("b", 3).loans("c", 3).state();

        send(state, "a", new TakeLoan());
        send(state, "a", new TakeLoan());
        send(state, "a", new TakeLoan());

        assertEquals(9, state.totalLoans());
    }

    @Test
    void testNoLoanOnRepayLoanSquare() {
        assertRejected(RuleViolation.class, TestGame.players("a", "b").at("a", 43).state(), "a", new TakeLoan());
        assertRejected(RuleViolation.class, TestGame.players("a", "b").at("a", 43).afterRoll().state(), "a", new TakeLoan());
    }

    @Test
    void testRepayLoanSquareWithoutLoans() {
        var state = TestGame.players("a", "b").at("a", 42).state();
        var events = roll(state, 1);
        assertEquals(3, events.size());
        assertEquals(75_000, player(state, "a").getCash());
    }

    @Test
    void testRepayLoanSquareWithEnoughCash() {
        var state = TestGame.players("a", "b").at("a", 42).loans("a", 2).state();

        var events = roll(state, 1);

        assertEquals(List.of(
                new MoneyTransferred("a", null, 50_000, MoneyReason.LOAN_REPAYMENT),
                new LoanRepaid("a", 1),
                new MoneyTransferred("a", null, 5_000, MoneyReason.LOAN_INTEREST)), events.subList(3, events.size()));
        assertEquals(20_000, player(state, "a").getCash());
    }

    @Test
    void testRepayLoanSquareShortOfCash() {
        var state = TestGame.players("a", "b").at("a", 42).loans("a", 1).cash("a", 40_000).car("a").state();

        var events = roll(state, 1);

        assertEquals(new PaymentDue("a", null, 55_000), events.getLast());
        assertEquals(40_000, player(state, "a").getCash());
        assertEquals(List.of("SellCar"), ENGINE.allowedCommands(state, "a"));
        assertRejected(RuleViolation.class, state, "a", new TakeLoan());
        assertRejected(RuleViolation.class, state, "a", new Pay());
        // Selling the car raises enough, so bankruptcy is refused
        assertRejected(RuleViolation.class, state, "a", new DeclareBankruptcy());

        send(state, "a", new SellCar());
        var payment = send(state, "a", new Pay());

        assertEquals(List.of(
                new MoneyTransferred("a", null, 50_000, MoneyReason.LOAN_REPAYMENT),
                new LoanRepaid("a", 0),
                new MoneyTransferred("a", null, 5_000, MoneyReason.LOAN_INTEREST)), payment);
        assertEquals(10_000, player(state, "a").getCash());
        assertTrue(state.getPendingDecisions().isEmpty());
        assertEquals(List.of("EndTurn"), ENGINE.allowedCommands(state, "a"));
    }

    @Test
    void testBankEntranceReward() {
        var withoutCar = TestGame.players("a", "b").at("a", 33).state();
        var events = roll(withoutCar, 1, 3, 4);
        assertEquals(List.of(
                new BankEntranceRoll("a", List.of(3, 4)),
                new MoneyTransferred(null, "a", 35_000, MoneyReason.BANK_ENTRANCE_REWARD)), events.subList(3, events.size()));
        assertEquals(110_000, player(withoutCar, "a").getCash());

        var withCar = TestGame.players("a", "b").car("a").at("a", 30).state();
        roll(withCar, 2, 2, 6, 6);
        assertEquals(135_000, player(withCar, "a").getCash());
    }

    @Test
    void testRaisingFundsWithLoanThenPaying() {
        var state = TestGame.players("a", "b").at("a", 45).loans("a", 2).cash("a", 5_000).state();

        var events = roll(state, 2);

        assertEquals(new PaymentDue("a", null, 10_000), events.getLast());
        assertEquals(List.of(new RaiseFunds("a", null, List.of(new Charge(10_000, MoneyReason.LOAN_INTEREST)))),
                state.getPendingDecisions());
        assertRejected(RuleViolation.class, state, "a", new EndTurn());
        assertRejected(RuleViolation.class, state, "a", new RepayLoan());
        assertRejected(NotYourTurn.class, state, "b", new Roll());

        send(state, "a", new TakeLoan());
        send(state, "a", new Pay());

        assertEquals(45_000, player(state, "a").getCash());
        assertEquals(3, player(state, "a").getLoans());
        send(state, "a", new EndTurn());
        assertEquals("b", state.getCurrentPlayer());
    }

    @Test
    void testBankruptcyRefusedWhileLoansAvailable() {
        var state = TestGame.players("a", "b").at("a", 45).loans("a", 1).cash("a", 0).state();
        roll(state, 2);
        assertRejected(RuleViolation.class, state, "a", new DeclareBankruptcy());
    }

    @Test
    void testBankruptcyWhenNothingCanBeRaised() {
        // Official limit: b and c hold the other 5 loans, so a cannot borrow to pay the 5 000 interest
        var state = TestGame.players("a", "b", "c").at("a", 45).loans("a", 1).loans("b", 3).loans("c", 2).cash("a", 1_500).state();
        roll(state, 2);
        assertRejected(RuleViolation.class, state, "a", new Pay());

        var events = send(state, "a", new DeclareBankruptcy());

        assertEquals(List.of(
                new MoneyTransferred("a", null, 1_500, MoneyReason.BANKRUPTCY),
                new PlayerBankrupt("a", null),
                new TurnStarted("b")), events);
        var a = player(state, "a");
        assertTrue(a.isOut());
        assertEquals(0, a.getCash());
        assertEquals(0, a.getLoans());
        assertTrue(state.getPendingDecisions().isEmpty());
        assertEquals("b", state.getCurrentPlayer());
        assertEquals(TurnPhase.BEFORE_ROLL, state.getPhase());
        // The cancelled loan is back in the pool
        assertTrue(ENGINE.allowedCommands(state, "c").isEmpty());
        state.setCurrentPlayer("c");
        assertTrue(ENGINE.allowedCommands(state, "c").contains("TakeLoan"));

        // a is skipped from now on
        state.setPhase(TurnPhase.AFTER_ROLL);
        send(state, "c", new EndTurn());
        assertEquals("b", state.getCurrentPlayer());
    }

    @Test
    void testUnlimitedLoansPreventBankruptcy() {
        var state = TestGame.players("a", "b", "c").loanLimit(LoanLimit.UNLIMITED).at("a", 45).loans("a", 1).loans("b", 3)
                .loans("c", 2).cash("a", 1_500).state();
        roll(state, 2);
        assertRejected(RuleViolation.class, state, "a", new DeclareBankruptcy());
    }
}
