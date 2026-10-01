package fi.bizhop.finanssi2.game.engine;

import org.junit.jupiter.api.Test;
import java.util.List;
import static fi.bizhop.finanssi2.game.engine.EngineTests.*;
import static org.junit.jupiter.api.Assertions.*;

class BondsTest {
    @Test void buyThenDrawReturnsWinningBond() {
        var state=TestGame.players("a","b").at("a",44).state();
        roll(state,1);
        assertInstanceOf(PendingDecision.BondOffer.class,state.getPendingDecisions().getFirst());
        state.getBonds().get(6).setOwner("b");
        var events=ENGINE.handle(state,"a",new GameCommand.BuyBond(1),new ScriptedDice(3,4));
        assertTrue(state.getBonds().get(0).getOwner().equals("a"));
        assertNull(state.getBonds().get(6).getOwner());
        assertEquals(125_000,state.player("b").orElseThrow().getCash());
        assertTrue(events.stream().anyMatch(e->e instanceof GameEvent.BondDrawn d && d.number()==7 && d.prize()==50_000));
    }

    @Test void auctionAcceptsSealedBidsAndAwardsHighest() {
        var state=TestGame.players("a","b").at("a",37).state();
        roll(state,1);
        assertInstanceOf(PendingDecision.BondAuction.class,state.getPendingDecisions().getFirst());
        send(state,"a",new GameCommand.BidBond(1_000));
        send(state,"b",new GameCommand.BidBond(2_000));
        assertEquals("b",state.getBonds().getFirst().getOwner());
        assertEquals(73_000,state.player("b").orElseThrow().getCash());
    }

    @Test void grandDrawOffersInTurnOrderFromDrawer() {
        var state=TestGame.players("a","b","c").state();
        ENGINE.grandBondDraw(state,state.player("b").orElseThrow(),new ScriptedDice());
        assertEquals("b",state.getPendingDecisions().getFirst().player());
        send(state,"b",new GameCommand.Pass());
        assertEquals("c",state.getPendingDecisions().getFirst().player());
        send(state,"c",new GameCommand.Pass());
        assertEquals("a",state.getPendingDecisions().getFirst().player());
        var events=ENGINE.handle(state,"a",new GameCommand.Pass(),new ScriptedDice(2,3));
        assertTrue(events.stream().anyMatch(e->e instanceof GameEvent.BondDrawn d && d.prize()==100_000));
        assertTrue(state.getPendingDecisions().isEmpty());
    }
}
