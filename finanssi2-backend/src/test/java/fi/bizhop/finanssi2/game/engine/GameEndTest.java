package fi.bizhop.finanssi2.game.engine;

import org.junit.jupiter.api.Test;

import static fi.bizhop.finanssi2.game.engine.EngineTests.ENGINE;
import static fi.bizhop.finanssi2.game.engine.EngineTests.send;
import static org.junit.jupiter.api.Assertions.*;

class GameEndTest {
    @Test
    void winsAtMillionWithTwoCompleteGroupsAndFinishedStateRejectsCommands() {
        var game = TestGame.players("A", "B").ownsGroup("A", "KASITEOLLISUUS")
                .ownsGroup("A", "PALVELUYHTIO").cash("A", 1_000_000).afterRoll().state();

        var events = send(game, "A", new GameCommand.EndTurn());

        assertTrue(game.isFinished());
        assertEquals("A", game.getWinner());
        assertEquals(2, game.getFinalStandings().getFirst().completeGroups().size());
        assertTrue(events.stream().anyMatch(event -> event instanceof GameEvent.GameEnded ended && ended.winner().equals("A")));
        var before = EngineTests.Snapshot.of(game);
        assertThrows(RuleViolation.class, () -> send(game, "B", new GameCommand.Resign()));
        assertEquals(before, EngineTests.Snapshot.of(game));
    }

    @Test
    void cashOrOneGroupAloneDoesNotWin() {
        var game = TestGame.players("A", "B").ownsGroup("A", "KASITEOLLISUUS")
                .cash("A", 1_000_000).afterRoll().state();

        send(game, "A", new GameCommand.EndTurn());

        assertFalse(game.isFinished());
    }

    @Test
    void resignationActsAsBankruptcyAndLeavesTheLastPlayerAsWinner() {
        var game = TestGame.players("A", "B").owns("A", 3).ownsShares("A", "OS-KASITEOLLISUUS-1")
                .cash("A", 20_000).state();

        var events = send(game, "A", new GameCommand.Resign());

        assertTrue(game.player("A").orElseThrow().isOut());
        assertNull(game.property(3).getOwner());
        assertNull(game.share("OS-KASITEOLLISUUS-1").getOwner());
        assertTrue(game.isFinished());
        assertEquals("B", game.getWinner());
        assertTrue(events.stream().anyMatch(GameEvent.PlayerResigned.class::isInstance));
    }
}
