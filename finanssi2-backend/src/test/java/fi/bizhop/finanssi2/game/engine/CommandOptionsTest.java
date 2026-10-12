package fi.bizhop.finanssi2.game.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static fi.bizhop.finanssi2.game.engine.EngineTests.ENGINE;
import static fi.bizhop.finanssi2.game.engine.EngineTests.GAME_DATA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandOptionsTest {
    final Rules rules = new Rules(GAME_DATA);

    @Test
    void propertyAndSharePricesCarryTheActiveFinanceNewsModifier() {
        var state = TestGame.players("a", "b").at("a", 11).state();
        var deed = GAME_DATA.titleDeed(GAME_DATA.titleDeeds().getFirst().square());
        var share = GAME_DATA.shares().getFirst();

        var base = ENGINE.options(state, "a");
        assertEquals(deed.price(), base.buyProperty().stream().filter(o -> o.square() == deed.square()).findFirst().orElseThrow().amount());
        assertEquals(share.value(), base.buyShare().stream().filter(o -> o.share().equals(share.id())).findFirst().orElseThrow().amount());

        state.setActiveFinanceNews("FL-11");
        var surcharged = ENGINE.options(state, "a");
        assertEquals(deed.price() * 3 / 2, surcharged.buyProperty().stream().filter(o -> o.square() == deed.square()).findFirst().orElseThrow().amount());
        assertEquals(rules.propertyPrice(state, deed), deed.price() * 3 / 2);

        state.setActiveFinanceNews("FL-08");
        var discounted = ENGINE.options(state, "a");
        assertEquals(rules.sharePrice(state, share), discounted.buyShare().stream().filter(o -> o.share().equals(share.id())).findFirst().orElseThrow().amount());
        assertTrue(rules.sharePrice(state, share) < share.value());
    }

    @Test
    void onlyChoicesTheEngineWouldAcceptAreListed() {
        var state = TestGame.players("a", "b").at("a", 11).cash("a", 100).state();
        var poor = ENGINE.options(state, "a");
        assertTrue(poor.buyProperty().isEmpty(), "no property is affordable with 100");
        assertTrue(poor.buyShare().isEmpty());

        state.player("a").orElseThrow().setCash(1_000_000);
        state.setBoughtThisTurn(true);
        assertTrue(ENGINE.options(state, "a").buyProperty().isEmpty(), "one purchase per turn");
    }

    @Test
    void noChoicesAreOfferedToAPlayerWhoseTurnItIsNot() {
        var state = TestGame.players("a", "b").at("b", 11).state();

        var options = ENGINE.options(state, "b");
        assertTrue(options.buyProperty().isEmpty() && options.buyShare().isEmpty() && options.mortgage().isEmpty()
                && options.sellBackProperty().isEmpty() && options.build().isEmpty() && options.shareholdersMeeting().isEmpty()
                && options.buyBond().isEmpty());
    }

    @Test
    void ownedPropertiesShowWhatTheBankPaysAndLends() {
        var deed = GAME_DATA.titleDeeds().stream().filter(d -> d.mortgage() != null && d.buyBack() != null).findFirst().orElseThrow();
        var state = TestGame.players("a", "b").owns("a", deed.square()).state();

        var options = ENGINE.options(state, "a");

        var property = state.property(deed.square());
        assertEquals(rules.mortgageValue(state, deed, property),
                options.mortgage().stream().filter(o -> o.square() == deed.square()).findFirst().orElseThrow().amount());
        assertEquals(rules.propertyBuyBack(state, deed, property),
                options.sellBackProperty().stream().filter(o -> o.square() == deed.square()).findFirst().orElseThrow().amount());
    }

    @Test
    void shareholdersMeetingsListTheLegalFees() {
        var state = TestGame.players("A", "B", "C").at("A", 35).owns("A", 12, 13).owns("B", 15).built(15)
                .ownsShares("A", "OS-LIIKEKESKUS-3").ownsShares("B", "OS-LIIKEKESKUS-1")
                .ownsShares("C", "OS-LIIKEKESKUS-2").cash("A", 1_000_000).state();

        var meeting = ENGINE.options(state, "A").shareholdersMeeting().stream()
                .filter(offer -> offer.group().equals("LIIKEKESKUS")).findFirst().orElseThrow();

        assertEquals(List.of(20_000, 30_000, 40_000, 50_000, 60_000, 70_000, 80_000, 90_000, 100_000, 110_000, 120_000), meeting.brokerageFees());
    }

    @Test
    void aPendingBondOfferListsTheBondsAndTheirPrice() {
        var state = TestGame.players("a", "b").state();
        state.enqueueDecision(new PendingDecision.BondOffer("a", BondContinuation.NONE));

        var options = ENGINE.options(state, "a");

        assertEquals(500, options.bondPrice());
        assertEquals(12, options.buyBond().size());
    }
}
