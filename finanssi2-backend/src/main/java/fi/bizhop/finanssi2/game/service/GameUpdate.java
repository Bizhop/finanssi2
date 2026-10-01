package fi.bizhop.finanssi2.game.service;

import fi.bizhop.finanssi2.game.db.GameLogEntry;

import java.util.List;

/** Broadcast on the game's topic after every change. A client that sees a version gap reloads the game. */
public record GameUpdate(String gameId, long version, List<GameLogEntry> events) {
    public GameUpdate {
        events = List.copyOf(events);
    }
}
