package fi.bizhop.finanssi2.game.engine;

import org.junit.jupiter.api.Test;

import static fi.bizhop.finanssi2.game.engine.EngineTests.ENGINE;
import static fi.bizhop.finanssi2.game.engine.EngineTests.send;
import static org.junit.jupiter.api.Assertions.*;

class ShareholdersMeetingTest {
    @Test
    void successfulMeetingPaysSellersTransfersAssetsAndCompletesGroup() {
        var game = TestGame.players("A", "B", "C").at("A", 35).owns("A", 12, 13).owns("B", 15).built(15)
                .ownsShares("A", "OS-LIIKEKESKUS-3").ownsShares("B", "OS-LIIKEKESKUS-1")
                .ownsShares("C", "OS-LIIKEKESKUS-2").cash("A", 1_000_000).state();

        var events = ENGINE.handle(game, "A", new GameCommand.CallShareholdersMeeting("LIIKEKESKUS", 80_000),
                new ScriptedDice(4, 4));

        assertEquals("A", game.property(12).getOwner());
        assertEquals("A", game.property(15).getOwner());
        assertEquals("A", game.share("OS-LIIKEKESKUS-1").getOwner());
        assertEquals("A", game.share("OS-LIIKEKESKUS-2").getOwner());
        assertTrue(new Ownership(EngineTests.GAME_DATA, game).ownsCompleteGroup("A", "LIIKEKESKUS"));
        assertEquals(10_000, new Rules(EngineTests.GAME_DATA).rent(game, EngineTests.GAME_DATA.titleDeed(12),
                game.property(12), game.player("C").orElseThrow(), true));
        assertEquals(1_000_000 - 140_000 - 80_000, game.player("A").orElseThrow().getCash());
        assertEquals(75_000 + 115_000, game.player("B").orElseThrow().getCash());
        assertEquals(75_000 + 75_000, game.player("C").orElseThrow().getCash());
        assertTrue(events.contains(new GameEvent.ShareholdersMeetingResolved("A", "LIIKEKESKUS", 80_000, 140_000,
                java.util.List.of(4, 4), true)));
    }

    @Test
    void failedMeetingCostsFeeAndDoesNotTransferAssets() {
        var game = TestGame.players("A", "B").at("A", 35).owns("A", 12, 13).owns("B", 15).built(15)
                .ownsShares("A", "OS-LIIKEKESKUS-3").ownsShares("B", "OS-LIIKEKESKUS-1", "OS-LIIKEKESKUS-2")
                .cash("A", 1_000_000).state();

        var events = ENGINE.handle(game, "A", new GameCommand.CallShareholdersMeeting("LIIKEKESKUS", 80_000),
                new ScriptedDice(4, 5));

        assertEquals("B", game.property(15).getOwner());
        assertEquals("B", game.share("OS-LIIKEKESKUS-1").getOwner());
        assertEquals(920_000, game.player("A").orElseThrow().getCash());
        assertTrue(events.contains(new GameEvent.ShareholdersMeetingResolved("A", "LIIKEKESKUS", 80_000, 140_000,
                java.util.List.of(4, 5), false)));
    }

    @Test
    void highestFeeSucceedsWithoutRollingAndFinanceNewsCanStopMeetings() {
        var game = TestGame.players("A", "B").at("A", 46).owns("A", 12, 13).owns("B", 15).mortgaged(15)
                .ownsShares("A", "OS-LIIKEKESKUS-3").ownsShares("B", "OS-LIIKEKESKUS-1", "OS-LIIKEKESKUS-2")
                .cash("A", 1_000_000).state();
        var events = send(game, "A", new GameCommand.CallShareholdersMeeting("LIIKEKESKUS", 120_000));
        assertTrue(game.property(15).isMortgaged());
        assertTrue(events.contains(new GameEvent.ShareholdersMeetingResolved("A", "LIIKEKESKUS", 120_000, 120_000,
                java.util.List.of(), true)));

        game.setActiveFinanceNews("FL-15");
        var before = EngineTests.Snapshot.of(game);
        assertThrows(RuleViolation.class, () -> send(game, "A", new GameCommand.CallShareholdersMeeting("LIIKEKESKUS", 20_000)));
        assertEquals(before, EngineTests.Snapshot.of(game));
    }

    @Test
    void lowSuccessfulFeeGoesEntirelyToBank() {
        var game = TestGame.players("A", "B").at("A", 35).owns("A", 12, 13).owns("B", 15)
                .ownsShares("A", "OS-LIIKEKESKUS-3").ownsShares("B", "OS-LIIKEKESKUS-1", "OS-LIIKEKESKUS-2")
                .cash("A", 1_000_000).state();

        var events = ENGINE.handle(game, "A", new GameCommand.CallShareholdersMeeting("LIIKEKESKUS", 20_000),
                new ScriptedDice(1, 1));

        assertTrue(events.contains(new GameEvent.MoneyTransferred("A", null, 20_000, MoneyReason.SHAREHOLDERS_MEETING)));
        assertEquals(1_000_000 - 20_000 - 120_000, game.player("A").orElseThrow().getCash());
        assertEquals(75_000 + 120_000, game.player("B").orElseThrow().getCash());
    }

    @Test
    void threeSellersReceiveRoundedBrokerageAndBankAbsorbsTheDifference() {
        var game = TestGame.players("A", "B", "C", "D").at("A", 35).ownsShares("A", "OS-LIIKEKESKUS-3")
                .owns("B", 15).owns("C", 13).owns("D", 12).ownsShares("B", "OS-LIIKEKESKUS-1")
                .ownsShares("C", "OS-LIIKEKESKUS-2").cash("A", 1_000_000).state();

        var events = ENGINE.handle(game, "A", new GameCommand.CallShareholdersMeeting("LIIKEKESKUS", 80_000),
                new ScriptedDice(1, 1));

        assertEquals(75_000 + 70_000 + 16_500, game.player("B").orElseThrow().getCash());
        assertEquals(75_000 + 65_000 + 16_500, game.player("C").orElseThrow().getCash());
        assertEquals(75_000 + 20_000 + 16_500, game.player("D").orElseThrow().getCash());
        assertTrue(events.contains(new GameEvent.MoneyTransferred("A", null, 30_500, MoneyReason.SHAREHOLDERS_MEETING)));
    }

    @Test
    void meetingRequiresBankLocationBeforeRollAndSufficientFunds() {
        var command = new GameCommand.CallShareholdersMeeting("LIIKEKESKUS", 20_000);
        // Every asset of the group is owned, so each case is rejected for its own reason
        var outsideBank = meetingReady().at("A", 34);
        EngineTests.assertRejected(RuleViolation.class, outsideBank.state(), "A", command);

        var afterRoll = meetingReady().afterRoll();
        EngineTests.assertRejected(RuleViolation.class, afterRoll.state(), "A", command);

        // Takeover 70 000 (B's 15 and one share) plus the 20 000 fee
        var insufficient = meetingReady().cash("A", 80_000);
        EngineTests.assertRejected(RuleViolation.class, insufficient.state(), "A", command);

        var invalidFee = meetingReady().cash("A", 1_000_000);
        EngineTests.assertRejected(RuleViolation.class, invalidFee.state(), "A",
                new GameCommand.CallShareholdersMeeting("LIIKEKESKUS", 25_000));
    }

    @Test
    void meetingRequiresEveryAssetOfTheGroupBoughtFromTheBank() {
        var command = new GameCommand.CallShareholdersMeeting("LIIKEKESKUS", 20_000);
        EngineTests.assertRejected(RuleViolation.class,
                TestGame.players("A", "B").at("A", 35).owns("A", 12, 13).owns("B", 15).ownsShares("A", "OS-LIIKEKESKUS-3")
                        .ownsShares("B", "OS-LIIKEKESKUS-1").cash("A", 1_000_000).state(), "A", command);
        EngineTests.assertRejected(RuleViolation.class,
                TestGame.players("A", "B").at("A", 35).owns("A", 12).owns("B", 15)
                        .ownsShares("A", "OS-LIIKEKESKUS-2", "OS-LIIKEKESKUS-3").ownsShares("B", "OS-LIIKEKESKUS-1")
                        .cash("A", 1_000_000).state(), "A", command);
        assertTrue(ENGINE.allowedCommands(meetingReady().cash("A", 1_000_000).state(), "A").contains("CallShareholdersMeeting"));
    }

    @Test
    void printedRulesAllowMeetingWhileAssetsAreStillInTheBank() {
        // Property 13 and share 2 are still in the bank; with the printed rules they simply stay out of the takeover
        var game = TestGame.players("A", "B").at("A", 35).owns("A", 12).owns("B", 15).ownsShares("A", "OS-LIIKEKESKUS-3")
                .ownsShares("B", "OS-LIIKEKESKUS-1").cash("A", 1_000_000).state();
        game.setSettings(new GameSettings(LoanLimit.OFFICIAL, CompulsorySaleMinimumBid.NONE, ShareholdersMeeting.ANY_OTHER_OWNER));

        var events = ENGINE.handle(game, "A", new GameCommand.CallShareholdersMeeting("LIIKEKESKUS", 120_000), new ScriptedDice());

        assertEquals("A", game.property(15).getOwner());
        assertNull(game.property(13).getOwner());
        assertNull(game.share("OS-LIIKEKESKUS-2").getOwner());
        assertTrue(events.contains(new GameEvent.ShareholdersMeetingResolved("A", "LIIKEKESKUS", 120_000, 70_000,
                java.util.List.of(), true)));
    }

    /** A at the bank before rolling, sharing Liikekeskus Oy with B, every property and share bought */
    private static TestGame meetingReady() {
        return TestGame.players("A", "B").at("A", 35).owns("A", 12, 13).owns("B", 15)
                .ownsShares("A", "OS-LIIKEKESKUS-2", "OS-LIIKEKESKUS-3").ownsShares("B", "OS-LIIKEKESKUS-1");
    }
}
