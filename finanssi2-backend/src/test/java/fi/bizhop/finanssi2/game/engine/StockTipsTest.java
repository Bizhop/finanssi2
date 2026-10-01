package fi.bizhop.finanssi2.game.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static fi.bizhop.finanssi2.game.engine.EngineTests.ENGINE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockTipsTest {
    @Test
    void heldTipLeavesTheDeckUntilUsed() {
        var state = TestGame.players("a", "b").state();
        state.getStockTipDeck().addAll(List.of("PV-01", "PV-03"));

        var events = ENGINE.drawStockTip(state, state.current(), new ScriptedDice());

        assertEquals(List.of(new GameEvent.StockTipDrawn("a", "PV-01", true)), events);
        assertEquals(List.of("PV-01"), state.current().getHeldStockTips());
        assertEquals(List.of("PV-03"), state.getStockTipDeck());
    }

    @Test
    void immediateTipReturnsToTheBottomOfTheDeck() {
        var state = TestGame.players("a", "b").state();
        state.getStockTipDeck().addAll(List.of("PV-03", "PV-01"));

        var events = ENGINE.drawStockTip(state, state.current(), new ScriptedDice());

        assertEquals(List.of(new GameEvent.StockTipDrawn("a", "PV-03", false),
                new GameEvent.ShareIssued("a", EngineTests.GAME_DATA.fundShares().stream()
                        .filter(share -> share.dividendPercent() == 20).findFirst().orElseThrow().id())), events);
        assertEquals(List.of("PV-01", "PV-03"), state.getStockTipDeck());
        assertEquals(List.of(), state.current().getHeldStockTips());
    }

    @Test
    void purchaseCertificateIsUsedOnlyToBypassASalesStop() {
        var state = TestGame.players("a", "b").at("a", 35).state();
        state.current().getHeldStockTips().add("PV-02");
        state.getStockTipDeck().add("PV-02");
        ENGINE.handle(state, "a", new GameCommand.BuyProperty(3), new ScriptedDice());
        assertEquals(List.of("PV-02"), state.current().getHeldStockTips());

        state.property(3).setOwner(null);
        state.setBoughtThisTurn(false);
        state.setActiveFinanceNews("FL-21");
        ENGINE.handle(state, "a", new GameCommand.BuyProperty(3), new ScriptedDice());
        assertTrue(state.current().getHeldStockTips().isEmpty());
        assertEquals("a", state.property(3).getOwner());
        assertEquals("PV-02", state.getStockTipDeck().getLast());
    }

    @Test
    void cardDrawAppliesItsEffectImmediately() {
        var state = TestGame.players("a", "b").car("a").state();
        state.getStockTipDeck().add("PV-04");

        var events = ENGINE.drawStockTip(state, state.current(), new ScriptedDice());

        assertEquals(List.of(new GameEvent.StockTipDrawn("a", "PV-04", false), new GameEvent.CarLost("a")), events);
        assertTrue(!state.current().isCar());
    }

    @Test
    void heldMeetingMovesToConstructionAndAllowsBuildingWithoutARoll() {
        var state = TestGame.players("a", "b").at("a", 13).owns("a", 3).state();
        state.current().getHeldStockTips().add("PV-25");

        ENGINE.handle(state, "a", new GameCommand.UseHeldStockTip("PV-25"), new ScriptedDice());
        ENGINE.handle(state, "a", new GameCommand.Build(List.of(3)), new ScriptedDice());

        assertEquals(17, state.current().getPosition());
        assertTrue(state.property(3).isBuilt());
        assertTrue(state.current().isNoMovementRollThisTurn());
    }

    @Test
    void transportStrikeDrawsFinanceNewsAtTheFirstTurnAfterTwoSkips() {
        var state = TestGame.players("a", "b").state();
        state.getStockTipDeck().add("PV-29");
        state.getFinanceNewsDeck().add("FL-05");
        ENGINE.drawStockTip(state, state.current(), new ScriptedDice());
        assertEquals(2, state.current().getMissedTurns());

        state.setPhase(TurnPhase.AFTER_ROLL);
        EngineTests.send(state, "a", new GameCommand.EndTurn());
        state.setPhase(TurnPhase.AFTER_ROLL);
        EngineTests.send(state, "b", new GameCommand.EndTurn());
        state.setPhase(TurnPhase.AFTER_ROLL);
        EngineTests.send(state, "b", new GameCommand.EndTurn());
        state.setPhase(TurnPhase.AFTER_ROLL);
        var events = EngineTests.send(state, "b", new GameCommand.EndTurn());

        assertEquals("a", state.getCurrentPlayer());
        assertEquals("FL-05", state.getActiveFinanceNews());
        assertTrue(state.current().getHeldStockTips().isEmpty());
        assertTrue(state.getStockTipDeck().contains("PV-29"));
        assertTrue(events.stream().anyMatch(GameEvent.FinanceNewsDrawn.class::isInstance));
    }

    @Test
    void compulsorySaleUsesSealedBidsAndCurrentTurnOrderForTies() {
        var state = TestGame.players("a", "b", "c").owns("a", 3).state();
        state.getStockTipDeck().add("PV-36");
        ENGINE.drawStockTip(state, state.current(), new ScriptedDice());
        EngineTests.send(state, "a", new GameCommand.ChooseStockTipOption("P:3"));

        EngineTests.send(state, "b", new GameCommand.BidAsset(500));
        var events = ENGINE.handle(state, "c", new GameCommand.BidAsset(500), new ScriptedDice());

        assertEquals("b", state.property(3).getOwner());
        assertEquals(75_500, state.player("a").orElseThrow().getCash());
        assertTrue(events.stream().anyMatch(event -> event.equals(new GameEvent.AssetAuctionCompleted(
                "a", "P:3", 500, "b", List.of(new PendingDecision.Bid("b", 500), new PendingDecision.Bid("c", 500))))));
    }
}
