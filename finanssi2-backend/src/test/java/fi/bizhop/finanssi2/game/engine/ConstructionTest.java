package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.engine.GameCommand.Build;
import fi.bizhop.finanssi2.game.engine.GameCommand.DeclareBankruptcy;
import fi.bizhop.finanssi2.game.engine.GameEvent.MoneyTransferred;
import fi.bizhop.finanssi2.game.engine.GameEvent.PropertyBuilt;
import fi.bizhop.finanssi2.game.engine.GameEvent.RentCharged;
import org.junit.jupiter.api.Test;

import java.util.List;

import static fi.bizhop.finanssi2.game.engine.EngineTests.ENGINE;
import static fi.bizhop.finanssi2.game.engine.EngineTests.GAME_DATA;
import static fi.bizhop.finanssi2.game.engine.EngineTests.assertRejected;
import static fi.bizhop.finanssi2.game.engine.EngineTests.roll;
import static fi.bizhop.finanssi2.game.engine.EngineTests.send;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionTest {
    static PlayerState player(GameState state, String playerId) {
        return state.player(playerId).orElseThrow();
    }

    @Test
    void testBuildSeveralOnConstructionSquares() {
        for (var square : List.of(17, 40)) {
            // Properties of different groups, a plant and a building
            var state = TestGame.players("a", "b").owns("a", 3, 27).at("a", square).state();

            var events = send(state, "a", new Build(List.of(3, 27)));

            assertEquals(List.of(
                    new MoneyTransferred("a", null, 10_000, MoneyReason.CONSTRUCTION),
                    new PropertyBuilt("a", 3, false),
                    new MoneyTransferred("a", null, 30_000, MoneyReason.CONSTRUCTION),
                    new PropertyBuilt("a", 27, true)), events);
            assertTrue(state.property(3).isBuilt());
            assertTrue(state.property(27).isBuilt());
            assertEquals(35_000, player(state, "a").getCash());
            // Building doesn't end the turn or use up the purchase
            assertEquals(TurnPhase.BEFORE_ROLL, state.getPhase());
            assertFalse(state.isBoughtThisTurn());
        }
    }

    @Test
    void testBuildRejected() {
        // Elsewhere, after rolling
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("a", 3).at("a", 11).state(), "a", new Build(List.of(3)));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("a", 3).at("a", 17).afterRoll().state(), "a",
                new Build(List.of(3)));
        // Someone else's, the bank's, Pysäköintitalo, mortgaged, already built, the same property twice, nothing
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("b", 3).at("a", 17).state(), "a", new Build(List.of(3)));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").at("a", 17).state(), "a", new Build(List.of(3)));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("a", 8).at("a", 17).state(), "a", new Build(List.of(8)));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("a", 3).mortgaged(3).at("a", 17).state(), "a",
                new Build(List.of(3)));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("a", 3).built(3).at("a", 17).state(), "a",
                new Build(List.of(3)));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("a", 3).at("a", 17).state(), "a", new Build(List.of(3, 3)));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").owns("a", 3).at("a", 17).state(), "a", new Build(List.of()));
        assertRejected(RuleViolation.class, TestGame.players("a", "b").at("a", 17).state(), "a", new Build(List.of(5)));
    }

    @Test
    void testBuildIsAllOrNothing() {
        // 10 000 + 200 000 for Ompelimo and Sahalaitos, with 75 000 cash: neither is built
        var game = TestGame.players("a", "b").owns("a", 3, 30).at("a", 40);
        assertRejected(RuleViolation.class, game.state(), "a", new Build(List.of(3, 30)));

        send(game.state(), "a", new Build(List.of(3)));
        assertTrue(game.state().property(3).isBuilt());
    }

    @Test
    void testAllowedCommandsListBuild() {
        assertTrue(ENGINE.allowedCommands(TestGame.players("a", "b").owns("a", 3).at("a", 17).state(), "a").contains("Build"));
        assertFalse(ENGINE.allowedCommands(TestGame.players("a", "b").owns("a", 3).state(), "a").contains("Build"));
        assertFalse(ENGINE.allowedCommands(TestGame.players("a", "b").owns("a", 8).at("a", 17).state(), "a").contains("Build"));
    }

    @Test
    void testBuiltRentWithCompleteGroup() {
        var state = TestGame.players("a", "b").ownsGroup("b", "KEMIA").built(26).at("a", 25).state();
        assertEquals(new RentCharged("a", "b", 26, 150_000, true), roll(state, 1).get(3));
    }

    @Test
    void testBuildingCounts() {
        var state = TestGame.players("a", "b").owns("a", 3, 4, 26, 27, 30).built(3, 4, 26).state();
        var ownership = new Ownership(GAME_DATA, state);
        assertEquals(1, ownership.plantCount("a"));
        assertEquals(2, ownership.otherBuildingCount("a"));
        assertEquals(0, ownership.plantCount("b"));
    }

    @Test
    void testBankruptcyRemovesBuildings() {
        // a owns a built Ompelimo, which can be neither mortgaged for enough nor sold back (Käsiteollisuus is never bought back)
        // Official loan limit: b and c hold all 6 loans
        var state = TestGame.players("a", "b", "c").settings(GameSettings.ORIGINAL).owns("b", 23).built(23).owns("a", 3).built(3)
                .loans("b", 3).loans("c", 3).cash("a", 0).at("a", 22).state();
        roll(state, 1);

        send(state, "a", new DeclareBankruptcy());

        assertFalse(state.property(3).isBuilt());
        assertEquals(null, state.property(3).getOwner());
    }
}
