package fi.bizhop.finanssi2.game.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static fi.bizhop.finanssi2.game.engine.EngineTests.ENGINE;
import static fi.bizhop.finanssi2.game.engine.EngineTests.GAME_DATA;
import static fi.bizhop.finanssi2.game.engine.EngineTests.assertRejected;
import static fi.bizhop.finanssi2.game.engine.EngineTests.roll;
import static fi.bizhop.finanssi2.game.engine.EngineTests.send;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BondsTest {
    @Test
    void buyThenDrawReturnsWinningBond() {
        var state = TestGame.players("a", "b").at("a", 44).state();
        roll(state, 1);
        assertEquals(new PendingDecision.BondOffer("a", BondContinuation.SMALL_DRAW), state.getPendingDecisions().getFirst());
        state.bond(7).setOwner("b");

        var events = handle(state, "a", new GameCommand.BuyBond(1), 3, 4);

        assertEquals("a", state.bond(1).getOwner());
        assertNull(state.bond(7).getOwner());
        assertEquals(125_000, state.player("b").orElseThrow().getCash());
        assertEquals(74_500, state.player("a").orElseThrow().getCash());
        assertEquals(List.of(new GameEvent.BondDrawn(7, 50_000, "b"), new GameEvent.BondDrawn(4, 25_000, null),
                new GameEvent.BondDrawn(3, 15_000, null)), draws(events));
        assertTrue(state.getPendingDecisions().isEmpty());
    }

    @Test
    void availableCommandsIncludePurchaseWhenBondOneIsOwned() {
        var state = TestGame.players("a", "b").at("a", 45).state();
        state.bond(1).setOwner("b");
        roll(state, 1);

        assertEquals(List.of("BuyBond", "Pass"), ENGINE.allowedCommands(state, "a"));
        send(state, "a", new GameCommand.BuyBond(2));
        assertEquals("a", state.bond(2).getOwner());
    }

    @Test
    void squareFortySixOffersPurchaseWithoutDrawAndPassCostsNothing() {
        var state = TestGame.players("a", "b").at("a", 45).state();
        roll(state, 1);
        assertEquals(new PendingDecision.BondOffer("a", BondContinuation.NONE), state.getPendingDecisions().getFirst());

        assertEquals(List.of(), send(state, "a", new GameCommand.Pass()));
        assertEquals(75_000, state.current().getCash());
        assertTrue(state.getPendingDecisions().isEmpty());
    }

    @Test
    void passingSmallDrawStillDraws() {
        var state = TestGame.players("a", "b").at("a", 44).state();
        roll(state, 1);
        assertEquals(List.of(new GameEvent.BondDrawn(5, 50_000, null), new GameEvent.BondDrawn(3, 25_000, null),
                new GameEvent.BondDrawn(2, 15_000, null)), handle(state, "a", new GameCommand.Pass(), 2, 3));
        assertEquals(75_000, state.current().getCash());
    }

    @ParameterizedTest
    @CsvSource({"3,4,7,4,3", "4,4,8,4,3", "1,1,2,1,12", "6,6,12,6,5"})
    void drawNumbersFollowDiceIncludingDoubles(int first, int second, int largest, int middle, int smallest) {
        var state = TestGame.players("a", "b").state();
        state.bond(largest).setOwner("a");
        state.bond(middle).setOwner("b");
        state.bond(smallest).setOwner("a");
        var dice = new ScriptedDice(first, second);

        var events = ENGINE.bonds.drawBonds(state, ENGINE.rules.smallBondPrizes(state), dice);

        assertEquals(List.of(new GameEvent.BondDrawn(largest, 50_000, "a"), new GameEvent.BondDrawn(middle, 25_000, "b"),
                new GameEvent.BondDrawn(smallest, 15_000, "a")), draws(events));
        assertEquals(140_000, state.player("a").orElseThrow().getCash());
        assertEquals(100_000, state.player("b").orElseThrow().getCash());
        assertNull(state.bond(largest).getOwner());
        assertNull(state.bond(middle).getOwner());
        assertNull(state.bond(smallest).getOwner());
        assertTrue(dice.isEmpty());
    }

    @Test
    void emptyBankSkipsPurchaseButStillRunsSmallDraw() {
        var state = TestGame.players("a", "b").at("a", 44).state();
        state.getBonds().forEach(bond -> bond.setOwner("b"));

        assertEquals(3, draws(roll(state, 1, 2, 3)).size());
        assertTrue(state.getPendingDecisions().isEmpty());
    }

    @Test
    void emptyBankSkipsSquareFortySixAndAuctionOffers() {
        for (var position : List.of(37, 45)) {
            var state = TestGame.players("a", "b").at("a", position).state();
            state.getBonds().forEach(bond -> bond.setOwner("b"));
            roll(state, 1);
            assertTrue(state.getPendingDecisions().isEmpty());
        }
    }

    @Test
    void auctionAcceptsSealedBidsAndAwardsHighest() {
        var state = TestGame.players("a", "b").at("a", 37).state();
        roll(state, 1);
        assertInstanceOf(PendingDecision.BondAuction.class, state.getPendingDecisions().getFirst());
        assertEquals(List.of(), send(state, "a", new GameCommand.BidBond(1_000)));
        assertNull(state.bond(1).getOwner());

        var events = send(state, "b", new GameCommand.BidBond(2_000));

        assertEquals("b", state.bond(1).getOwner());
        assertEquals(73_000, state.player("b").orElseThrow().getCash());
        assertEquals(75_000, state.player("a").orElseThrow().getCash());
        assertEquals(new GameEvent.BondAuctionCompleted(2_000, "b", 1,
                List.of(new PendingDecision.Bid("a", 1_000), new PendingDecision.Bid("b", 2_000))), events.getLast());
        assertTrue(state.getPendingDecisions().isEmpty());
    }

    @Test
    void auctionTieGoesToFirstBidderFromCurrentPlayer() {
        var state = TestGame.players("a", "b", "c").turn("b").at("b", 37).state();
        roll(state, 1);
        send(state, "b", new GameCommand.BidBond(1_000));
        send(state, "c", new GameCommand.BidBond(1_000));
        send(state, "a", new GameCommand.BidBond(500));
        assertEquals("b", state.bond(1).getOwner());
    }

    @Test
    void auctionWithNoBidsMakesNoSale() {
        var state = TestGame.players("a", "b").at("a", 37).state();
        roll(state, 1);
        send(state, "a", new GameCommand.BidBond(0));
        assertEquals(List.of(new GameEvent.BondAuctionCompleted(0, null, 0,
                List.of(new PendingDecision.Bid("a", 0), new PendingDecision.Bid("b", 0)))),
                send(state, "b", new GameCommand.BidBond(0)));
        assertNull(state.bond(1).getOwner());
        assertEquals(75_000, state.player("a").orElseThrow().getCash());
        assertTrue(state.getPendingDecisions().isEmpty());
    }

    @Test
    void auctionSkipsBankruptPlayersAndPlayersWithoutCash() {
        var state = TestGame.players("a", "b", "c").at("a", 37).cash("b", 0).out("c").state();
        roll(state, 1);
        var auction = assertInstanceOf(PendingDecision.BondAuction.class, state.getPendingDecisions().getFirst());
        assertEquals(List.of("a"), auction.order());
        send(state, "a", new GameCommand.BidBond(500));
        assertEquals("a", state.bond(1).getOwner());
    }

    @Test
    void auctionWithoutEligiblePlayersQueuesNothing() {
        var state = TestGame.players("a", "b").at("a", 37).cash("a", 0).cash("b", 0).state();
        roll(state, 1);
        assertTrue(state.getPendingDecisions().isEmpty());
    }

    @Test
    void unavailablePurchasesAreRejectedWithoutChanges() {
        var state = TestGame.players("a", "b").at("a", 45).state();
        roll(state, 1);
        state.bond(1).setOwner("b");
        for (var number : List.of(0, 1, 13)) {
            assertRejected(RuleViolation.class, state, "a", new GameCommand.BuyBond(number));
        }
        assertRejected(NotYourTurn.class, state, "b", new GameCommand.BuyBond(2));
        assertRejected(RuleViolation.class, state, "a", new GameCommand.BidBond(500));
        state.current().setCash(499);
        assertEquals(List.of("Pass"), ENGINE.allowedCommands(state, "a"));
        assertRejected(RuleViolation.class, state, "a", new GameCommand.BuyBond(2));
    }

    @Test
    void bondCommandsWithoutDecisionsAreRejectedWithoutChanges() {
        var state = TestGame.players("a", "b").state();
        assertRejected(RuleViolation.class, state, "a", new GameCommand.BuyBond(1));
        assertRejected(RuleViolation.class, state, "a", new GameCommand.Pass());
        assertRejected(RuleViolation.class, state, "a", new GameCommand.BidBond(0));
    }

    @Test
    void invalidAuctionBidsAreRejectedWithoutChanges() {
        var state = TestGame.players("a", "b").at("a", 37).state();
        roll(state, 1);
        for (var amount : List.of(-500, 250, 75_500)) {
            assertRejected(RuleViolation.class, state, "a", new GameCommand.BidBond(amount));
        }
        assertRejected(NotYourTurn.class, state, "b", new GameCommand.BidBond(500));
        assertRejected(RuleViolation.class, state, "a", new GameCommand.Pass());
        assertRejected(RuleViolation.class, state, "a", new GameCommand.BuyBond(1));
    }

    @Test
    void grandDrawOffersInTurnOrderFromDrawer() {
        var state = TestGame.players("a", "b", "c").state();
        ENGINE.grandBondDraw(state, state.player("b").orElseThrow(), new ScriptedDice());
        assertEquals(List.of(new PendingDecision.BondOffer("b", BondContinuation.CONTINUE_GRAND_DRAW),
                new PendingDecision.BondOffer("c", BondContinuation.CONTINUE_GRAND_DRAW),
                new PendingDecision.BondOffer("a", BondContinuation.GRAND_DRAW)), state.getPendingDecisions());
        send(state, "b", new GameCommand.Pass());
        send(state, "c", new GameCommand.Pass());
        var events = handle(state, "a", new GameCommand.Pass(), 2, 3);
        assertEquals(List.of(new GameEvent.BondDrawn(5, 100_000, null), new GameEvent.BondDrawn(3, 50_000, null),
                new GameEvent.BondDrawn(2, 25_000, null)), events);
        assertTrue(state.getPendingDecisions().isEmpty());
    }

    @Test
    void grandDrawSkipsIneligiblePlayersAndDrawsAfterLastEligiblePlayer() {
        var state = TestGame.players("a", "b", "c").cash("b", 499).out("c").state();
        ENGINE.grandBondDraw(state, state.player("b").orElseThrow(), new ScriptedDice());
        assertEquals(List.of(new PendingDecision.BondOffer("a", BondContinuation.GRAND_DRAW)), state.getPendingDecisions());
        assertEquals(3, draws(handle(state, "a", new GameCommand.BuyBond(1), 2, 3)).size());
        assertTrue(state.getPendingDecisions().isEmpty());
    }

    @Test
    void grandDrawWithNoEligibleBuyersOrNoSupplyDrawsImmediately() {
        for (var noSupply : List.of(false, true)) {
            var state = TestGame.players("a", "b").cash("a", 0).cash("b", 0).state();
            if (noSupply) {
                state.getBonds().forEach(bond -> bond.setOwner("a"));
            }
            var dice = new ScriptedDice(2, 3);
            assertEquals(3, draws(ENGINE.grandBondDraw(state, state.current(), dice)).size());
            assertTrue(dice.isEmpty());
            assertTrue(state.getPendingDecisions().isEmpty());
        }
    }

    @Test
    void grandDrawCanFinishWhenAnEarlierBuyerTakesLastAvailableBond() {
        var state = TestGame.players("a", "b").state();
        state.getBonds().stream().filter(bond -> bond.getNumber() != 1).forEach(bond -> bond.setOwner("a"));
        ENGINE.grandBondDraw(state, state.current(), new ScriptedDice());
        send(state, "a", new GameCommand.BuyBond(1));
        assertEquals(List.of("Pass"), ENGINE.allowedCommands(state, "b"));
        assertEquals(3, draws(handle(state, "b", new GameCommand.Pass(), 2, 3)).size());
        assertTrue(state.getPendingDecisions().isEmpty());
    }

    @Test
    void bondPriceAndPrizesUseRules() {
        var rules = new Rules(GAME_DATA) {
            @Override
            public int bondPrice(GameState state) {
                return 1_000;
            }

            @Override
            public List<Integer> smallBondPrizes(GameState state) {
                return List.of(5_000, 2_500, 1_500);
            }
        };
        var engine = new GameEngine(GAME_DATA, rules);
        var state = TestGame.players("a", "b").cash("a", 500).state();
        state.getPendingDecisions().add(new PendingDecision.BondOffer("a", BondContinuation.SMALL_DRAW));
        assertEquals(List.of("Pass"), engine.allowedCommands(state, "a"));
        state.current().setCash(2_000);
        state.bond(5).setOwner("b");
        var events = engine.handle(state, "a", new GameCommand.BuyBond(1), new ScriptedDice(2, 3));
        assertEquals(1_000, state.current().getCash());
        assertEquals(80_000, state.player("b").orElseThrow().getCash());
        assertEquals(5_000, draws(events).getFirst().prize());
    }

    static List<GameEvent> handle(GameState state, String playerId, GameCommand command, Integer... values) {
        var dice = new ScriptedDice(values);
        var events = ENGINE.handle(state, playerId, command, dice);
        assertTrue(dice.isEmpty(), "dice left over");
        return events;
    }

    static List<GameEvent.BondDrawn> draws(List<GameEvent> events) {
        return events.stream().filter(GameEvent.BondDrawn.class::isInstance).map(GameEvent.BondDrawn.class::cast).toList();
    }
}
