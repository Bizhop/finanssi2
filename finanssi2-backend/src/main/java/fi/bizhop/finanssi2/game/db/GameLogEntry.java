package fi.bizhop.finanssi2.game.db;

import fi.bizhop.finanssi2.game.engine.GameEvent;

/** An event in a game's log. {@code type} repeats the event's type so the log can be queried by it. */
public record GameLogEntry(String id, String gameId, int seq, long time, String type, GameEvent event) {
    public static GameLogEntry of(String gameId, int seq, long time, GameEvent event) {
        // A deterministic id, so saving the same event again updates its row instead of adding a second one
        return new GameLogEntry(gameId + ":" + seq, gameId, seq, time, event.getClass().getSimpleName(), event);
    }
}
