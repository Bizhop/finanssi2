package fi.bizhop.finanssi2.game.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static fi.bizhop.finanssi2.game.engine.EngineTests.ENGINE;
import static fi.bizhop.finanssi2.game.engine.EngineTests.GAME_DATA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceNewsTest {
    @Test
    void drawingAnImmediateCardReplacesTheActiveCardAndMovesItToTheBottom() {
        var state = TestGame.players("a", "b").state();
        state.setActiveFinanceNews("FL-05");
        state.getFinanceNewsDeck().addAll(List.of("FL-07", "FL-08"));

        var events = ENGINE.financeNews(state, state.current(), GAME_DATA.square(5), new ScriptedDice());

        assertEquals("FL-07", ((GameEvent.FinanceNewsDrawn) events.getFirst()).card());
        assertEquals("FL-05", ((GameEvent.FinanceNewsDrawn) events.getFirst()).replaced());
        assertEquals(List.of("FL-08", "FL-07"), state.getFinanceNewsDeck());
        assertNull(state.getActiveFinanceNews());
        assertEquals(37_500, state.player("a").orElseThrow().getCash());
        assertEquals(37_500, state.player("b").orElseThrow().getCash());
    }

    @Test
    void changingMarketsWaitsForTheDrawersDirectionAndSkipsFurtherNewsDraws() {
        var state = TestGame.players("a", "b").at("b", 10).state();
        state.getFinanceNewsDeck().add("FL-04");
        ENGINE.financeNews(state, state.current(), GAME_DATA.square(5), new ScriptedDice());
        assertInstanceOf(PendingDecision.NewsDirection.class, state.getPendingDecisions().getFirst());

        var events = ENGINE.handle(state, "a", new GameCommand.ChooseNewsDirection(true), new ScriptedDice());

        assertEquals(4, state.player("a").orElseThrow().getPosition());
        assertEquals(11, state.player("b").orElseThrow().getPosition());
        assertEquals(2, events.stream().filter(GameEvent.LandedOn.class::isInstance).count());
        assertTrue(events.stream().noneMatch(GameEvent.FinanceNewsDrawn.class::isInstance));
        assertFalse(state.getPendingDecisions().stream().anyMatch(PendingDecision.NewsDirection.class::isInstance));
    }

    @Test
    void goodTimesSquare46PaysEveryPlayerAndOffersABondOnlyToTheActivePlayer() {
        var shares = GAME_DATA.shares();
        var state = TestGame.players("a", "b").ownsShares("a", shares.get(0).id())
                .ownsShares("b", shares.get(1).id()).state();
        state.setActiveFinanceNews("FL-16");

        var events = ENGINE.bondPurchaseAndDividend(state, state.current(), GAME_DATA.square(46), new ScriptedDice());

        assertEquals(2, events.stream().filter(GameEvent.BankDividend.class::isInstance).count());
        assertEquals(List.of(new PendingDecision.BondOffer("a", BondContinuation.NONE)), state.getPendingDecisions());
    }

    @Test
    void goodTimesDoublesPlayerDividendsButShareBoomOnlyDoublesBankDividends() {
        var state = TestGame.players("a", "b").state();
        var square41 = GAME_DATA.square(41);
        var share = GAME_DATA.shares().getFirst();
        state.setActiveFinanceNews("FL-16");
        assertEquals(40_000, ENGINE.rules.playerDividend(state, square41, 100_000));
        assertEquals(share.dividend() * 2, ENGINE.rules.bankDividend(state, share));

        state.setActiveFinanceNews("FL-20");
        assertEquals(20_000, ENGINE.rules.playerDividend(state, square41, 100_000));
        assertEquals(share.dividend() * 2, ENGINE.rules.bankDividend(state, share));
    }

    @Test
    void energyTaxMovesPlayersStraightToSquareOneAndRewardsCrossingSquare34() {
        var state = TestGame.players("a", "b").at("a", 10).owns("a", 3).built(3).state();
        state.getFinanceNewsDeck().add("FL-19");

        var events = ENGINE.financeNews(state, state.current(), GAME_DATA.square(5), new ScriptedDice(2, 3));

        assertEquals(1, state.player("a").orElseThrow().getPosition());
        assertTrue(events.stream().anyMatch(event -> event.equals(new GameEvent.PieceMoved("a", 10, 1))));
        assertTrue(events.stream().anyMatch(event -> event.equals(new GameEvent.BankEntranceRoll("a", List.of(2, 3)))));
        assertTrue(events.stream().anyMatch(event -> event.equals(new GameEvent.MoneyTransferred(null, "a", 25_000,
                MoneyReason.BANK_ENTRANCE_REWARD))));
        assertTrue(events.stream().anyMatch(event -> event.equals(new GameEvent.MoneyTransferred("a", null, 20_000,
                MoneyReason.FINANCE_NEWS))));
    }
}
