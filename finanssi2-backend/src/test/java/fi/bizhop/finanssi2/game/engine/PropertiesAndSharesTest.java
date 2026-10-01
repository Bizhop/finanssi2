package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.engine.GameCommand.BuyProperty;
import fi.bizhop.finanssi2.game.engine.GameCommand.BuyShare;
import fi.bizhop.finanssi2.game.engine.GameCommand.DeclareBankruptcy;
import fi.bizhop.finanssi2.game.engine.GameCommand.EndTurn;
import fi.bizhop.finanssi2.game.engine.GameCommand.Mortgage;
import fi.bizhop.finanssi2.game.engine.GameCommand.Pay;
import fi.bizhop.finanssi2.game.engine.GameCommand.Redeem;
import fi.bizhop.finanssi2.game.engine.GameCommand.SellBackProperty;
import fi.bizhop.finanssi2.game.engine.GameCommand.SellBackShare;
import fi.bizhop.finanssi2.game.engine.GameEvent.AssetsReturned;
import fi.bizhop.finanssi2.game.engine.GameEvent.MoneyTransferred;
import fi.bizhop.finanssi2.game.engine.GameEvent.PaymentDue;
import fi.bizhop.finanssi2.game.engine.GameEvent.PlayerBankrupt;
import fi.bizhop.finanssi2.game.engine.GameEvent.PropertyBought;
import fi.bizhop.finanssi2.game.engine.GameEvent.PropertyMortgaged;
import fi.bizhop.finanssi2.game.engine.GameEvent.PropertyRedeemed;
import fi.bizhop.finanssi2.game.engine.GameEvent.PropertySoldBack;
import fi.bizhop.finanssi2.game.engine.GameEvent.RentCharged;
import fi.bizhop.finanssi2.game.engine.GameEvent.ShareBought;
import fi.bizhop.finanssi2.game.engine.GameEvent.ShareSoldBack;
import fi.bizhop.finanssi2.game.engine.GameEvent.TurnStarted;
import org.junit.jupiter.api.Test;

import java.util.List;

import static fi.bizhop.finanssi2.game.engine.EngineTests.ENGINE;
import static fi.bizhop.finanssi2.game.engine.EngineTests.GAME_DATA;
import static fi.bizhop.finanssi2.game.engine.EngineTests.assertRejected;
import static fi.bizhop.finanssi2.game.engine.EngineTests.roll;
import static fi.bizhop.finanssi2.game.engine.EngineTests.send;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertiesAndSharesTest {
    static PlayerState player(GameState state, String uid) {
        return state.player(uid).orElseThrow();
    }

    /** Events after the roll's DiceRolled, PieceMoved and LandedOn */
    static List<GameEvent> landing(List<GameEvent> events) {
        return events.subList(3, events.size());
    }

    // Buying

    @Test
    void testBuyPropertyOnBranchOffice() {
        var state = TestGame.players("a", "b").at("a", 11).state();

        var events = send(state, "a", new BuyProperty(3));

        assertEquals(List.of(new MoneyTransferred("a", null, 10_000, MoneyReason.PROPERTY_PURCHASE), new PropertyBought("a", 3)), events);
        assertEquals("a", state.property(3).getOwner());
        assertEquals(65_000, player(state, "a").getCash());
        assertTrue(state.isBoughtThisTurn());
    }

    @Test
    void testBuyShareInHeadOffice() {
        for (var square : List.of(35, 40, 46)) {
            var state = TestGame.players("a", "b").at("a", square).state();

            var events = send(state, "a", new BuyShare("OS-KASITEOLLISUUS-1"));

            assertEquals(new ShareBought("a", "OS-KASITEOLLISUUS-1"), events.getLast());
            assertEquals(25_000, player(state, "a").getCash());
        }
    }

    @Test
    void testBuyFundShare() {
        var state = TestGame.players("a", "b").at("a", 11).state();
        send(state, "a", new BuyShare("OS-RAHASTO-25"));
        assertEquals("a", state.share("OS-RAHASTO-25").getOwner());
        assertEquals(25_000, player(state, "a").getCash());
    }

    @Test
    void testBuyingRejected() {
        // Elsewhere on the board, and just outside the head office
        for (var square : List.of(1, 3, 34)) {
            assertRejected(RuleViolation.class, TestGame.players("a", "b").at("a", square).state(), "a", new BuyProperty(3));
        }
        assertRejected(RuleViolation.class, TestGame.players("a", "b").at("a", 11).afterRoll().state(), "a", new BuyProperty(3));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").at("a", 11).boughtThisTurn().state(), "a", new BuyShare(
                "OS-KEMIA-1"));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").at("a", 11).owns("b", 3).state(), "a", new BuyProperty(3));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").at("a", 11).owns("a", 3).state(), "a", new BuyProperty(3));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").at("a", 11).cash("a", 9_500).state(), "a", new BuyProperty(3));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").at("a", 11).state(), "a", new BuyProperty(5));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").at("a", 11).state(), "a", new BuyShare("OS-NOPE"));
    }

    @Test
    void testOnePurchasePerTurn() {
        var state = TestGame.players("a", "b").at("a", 11).state();
        send(state, "a", new BuyProperty(3));
        assertRejected(RuleViolation.class, state, "a", new BuyShare("OS-KASITEOLLISUUS-1"));

        state.setPhase(TurnPhase.AFTER_ROLL);
        send(state, "a", new EndTurn());
        assertFalse(state.isBoughtThisTurn());
    }

    @Test
    void testAllowedCommandsListPurchases() {
        assertTrue(ENGINE.allowedCommands(TestGame.players("a", "b").at("a", 11).state(), "a").containsAll(List.of("BuyProperty",
                "BuyShare")));
        assertFalse(ENGINE.allowedCommands(TestGame.players("a", "b").state(), "a").contains("BuyProperty"));
        assertTrue(ENGINE.allowedCommands(TestGame.players("a", "b").owns("a", 3).state(), "a").containsAll(List.of("Mortgage")));
        assertFalse(ENGINE.allowedCommands(TestGame.players("a", "b").owns("a", 3).state(), "a").contains("SellBackProperty"));
    }

    // Rent

    @Test
    void testNoRentOnUnownedOrOwnProperty() {
        assertEquals(List.of(), landing(roll(TestGame.players("a", "b").state(), 2)));
        var own = TestGame.players("a", "b").owns("a", 3).state();
        assertEquals(List.of(), landing(roll(own, 2)));
        assertEquals(75_000, player(own, "a").getCash());
    }

    @Test
    void testRentToOwner() {
        var state = TestGame.players("a", "b").owns("b", 3).state();

        var events = landing(roll(state, 2));

        assertEquals(List.of(
                new RentCharged("a", "b", 3, 2_500, false),
                new MoneyTransferred("a", "b", 2_500, MoneyReason.RENT)), events);
        assertEquals(72_500, player(state, "a").getCash());
        assertEquals(77_500, player(state, "b").getCash());
    }

    @Test
    void testBuiltRent() {
        var state = TestGame.players("a", "b").owns("b", 3).built(3).state();
        assertEquals(new RentCharged("a", "b", 3, 25_000, false), landing(roll(state, 2)).getFirst());
    }

    @Test
    void testNoRentOnMortgagedProperty() {
        var state = TestGame.players("a", "b").owns("b", 3).mortgaged(3).state();
        assertEquals(List.of(), landing(roll(state, 2)));
    }

    @Test
    void testCompleteGroupDoublesRent() {
        var state = TestGame.players("a", "b").ownsGroup("b", "KASITEOLLISUUS").state();
        assertEquals(new RentCharged("a", "b", 3, 5_000, true), landing(roll(state, 2)).getFirst());
    }

    @Test
    void testCompleteGroupNeedsAllShares() {
        var game = TestGame.players("a", "b").owns("b", 3, 4, 6).ownsShares("b", "OS-KASITEOLLISUUS-1", "OS-KASITEOLLISUUS-2");
        var ownership = new Ownership(GAME_DATA, game.state());
        assertFalse(ownership.ownsCompleteGroup("b", "KASITEOLLISUUS"));
        assertEquals(new RentCharged("a", "b", 3, 2_500, false), landing(roll(game.state(), 2)).getFirst());

        game.ownsShares("b", "OS-KASITEOLLISUUS-3");
        assertTrue(ownership.ownsCompleteGroup("b", "KASITEOLLISUUS"));
        // Fund shares belong to no group
        assertFalse(ownership.ownsCompleteGroup("b", null));
    }

    @Test
    void testNoRentWhereDeedShowsADash() {
        // Hotelli charges rent only when built
        var state = TestGame.players("a", "b").owns("b", 9).at("a", 8).state();
        assertEquals(List.of(), landing(roll(state, 1)));
        var built = TestGame.players("a", "b").owns("b", 9).built(9).at("a", 8).state();
        assertEquals(new RentCharged("a", "b", 9, 50_000, false), landing(roll(built, 1)).getFirst());
    }

    @Test
    void testParkingFeeOnlyFromCarOwners() {
        var withoutCar = TestGame.players("a", "b").owns("b", 8).at("a", 7).state();
        assertEquals(List.of(), landing(roll(withoutCar, 1)));

        var withCar = TestGame.players("a", "b").owns("b", 8).car("a").at("a", 6).state();
        assertEquals(new RentCharged("a", "b", 8, 10_000, false), landing(roll(withCar, 1, 1)).getFirst());
    }

    @Test
    void testRentShortOfCashIsRaisedByMortgaging() {
        var state = TestGame.players("a", "b").owns("b", 23).built(23).owns("a", 21).cash("a", 40_000).at("a", 22).state();

        var events = landing(roll(state, 1));

        assertEquals(new PaymentDue("a", "b", 60_000), events.getLast());
        assertTrue(ENGINE.allowedCommands(state, "a").containsAll(List.of("Mortgage", "TakeLoan")));
        send(state, "a", new Mortgage(21));
        send(state, "a", new Pay());
        assertEquals(5_000, player(state, "a").getCash());
        assertEquals(135_000, player(state, "b").getCash());
    }

    // Mortgage, redeem, sell back

    @Test
    void testMortgageAndRedeem() {
        var state = TestGame.players("a", "b").owns("a", 3).state();

        assertEquals(List.of(new PropertyMortgaged("a", 3), new MoneyTransferred(null, "a", 10_000, MoneyReason.MORTGAGE)),
                send(state, "a", new Mortgage(3)));
        assertTrue(state.property(3).isMortgaged());
        assertEquals(85_000, player(state, "a").getCash());

        // The redemption price printed on the back of the deed
        assertEquals(List.of(new MoneyTransferred("a", null, 11_000, MoneyReason.REDEMPTION), new PropertyRedeemed("a", 3)),
                send(state, "a", new Redeem(3)));
        assertFalse(state.property(3).isMortgaged());
        assertEquals(74_000, player(state, "a").getCash());
    }

    @Test
    void testMortgageBuiltValue() {
        var state = TestGame.players("a", "b").owns("a", 10).built(10).afterRoll().state();
        send(state, "a", new Mortgage(10));
        assertEquals(110_000, player(state, "a").getCash());
    }

    @Test
    void testMortgageRejected() {
        // Teollisuuskonserni and Pysäköintitalo are never mortgaged; Liikekeskus only unbuilt, Finanssiyhtymä only built
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("a", 30).state(), "a", new Mortgage(30));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("a", 8).state(), "a", new Mortgage(8));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("a", 12).built(12).state(), "a", new Mortgage(12));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("a", 18).state(), "a", new Mortgage(18));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("a", 3).mortgaged(3).state(), "a", new Mortgage(3));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("b", 3).state(), "a", new Mortgage(3));
    }

    @Test
    void testRedeemRejected() {
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("a", 3).mortgaged(3).afterRoll().state(), "a",
                new Redeem(3));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("a", 3).state(), "a", new Redeem(3));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("a", 3).mortgaged(3).cash("a", 10_500).state(), "a",
                new Redeem(3));
    }

    @Test
    void testSellBackProperty() {
        var state = TestGame.players("a", "b").owns("a", 10).afterRoll().state();

        assertEquals(List.of(new PropertySoldBack("a", 10), new MoneyTransferred(null, "a", 15_000, MoneyReason.PROPERTY_SALE)),
                send(state, "a", new SellBackProperty(10)));
        assertNull(state.property(10).getOwner());

        var built = TestGame.players("a", "b").owns("a", 10).built(10).state();
        send(built, "a", new SellBackProperty(10));
        assertEquals(115_000, player(built, "a").getCash());
        assertFalse(built.property(10).isBuilt());
    }

    @Test
    void testSellBackRejected() {
        // Käsiteollisuus is never bought back; Liikekeskus only built; mortgaged must be redeemed first
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("a", 3).state(), "a", new SellBackProperty(3));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("a", 12).state(), "a", new SellBackProperty(12));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("a", 10).mortgaged(10).state(), "a",
                new SellBackProperty(10));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").state(), "a", new SellBackShare("OS-KEMIA-1"));
    }

    @Test
    void testSellBackShareAtPrintedValue() {
        var state = TestGame.players("a", "b").ownsGroup("a", "FINANSSIYHTYMA").state();

        assertEquals(List.of(new ShareSoldBack("a", "OS-FINANSSIYHTYMA-2"),
                        new MoneyTransferred(null, "a", 37_500, MoneyReason.SHARE_SALE)),
                send(state, "a", new SellBackShare("OS-FINANSSIYHTYMA-2")));
        assertNull(state.share("OS-FINANSSIYHTYMA-2").getOwner());
    }

    // Raising funds and bankruptcy

    @Test
    void testBankruptcyRefusedWhileAssetsCanBeSold() {
        // Loans are out (official limit reached by others), but a share can still be sold back
        var state = TestGame.players("a", "b", "c").owns("b", 23).built(23).ownsShares("a", "OS-KEMIA-1").loans("b", 3).loans("c", 3)
                .cash("a", 0).at("a", 22).state();
        roll(state, 1);
        assertRejected(RuleViolation.class, state, "a", new DeclareBankruptcy());
        assertTrue(ENGINE.allowedCommands(state, "a").contains("SellBackShare"));
    }

    @Test
    void testBankruptcyReturnsAssets() {
        // No loans left under the official limit, and a mortgaged property adds nothing while raising funds
        var state = TestGame.players("a", "b", "c").owns("b", 23).built(23).owns("a", 3).mortgaged(3).loans("b", 3).loans("c", 3)
                .ownsShares("a", "OS-RAHASTO-25").cash("a", 0).at("a", 22).state();
        roll(state, 1);
        // Selling the fund share back (25 000) is not enough for the 60 000 rent, so bankruptcy is allowed; the creditor gets the cash
        assertTrue(ENGINE.allowedCommands(state, "a").contains("DeclareBankruptcy"));
        send(state, "a", new SellBackShare("OS-RAHASTO-25"));

        var events = send(state, "a", new DeclareBankruptcy());

        assertEquals(List.of(
                new MoneyTransferred("a", "b", 25_000, MoneyReason.BANKRUPTCY),
                new PlayerBankrupt("a", "b"),
                new AssetsReturned("a", List.of(3), List.of(), List.of()),
                new TurnStarted("b")), events);
        assertNull(state.property(3).getOwner());
        assertFalse(state.property(3).isMortgaged());
    }
}
