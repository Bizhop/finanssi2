package fi.bizhop.finanssi2.game.db;

import fi.bizhop.finanssi2.game.engine.GameEvent;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** An event in a game's log. {@code type} repeats the event's type for querying the collection. */
@Document(collection = "game_events")
public record GameLogEntry(@Id String id, String gameId, int seq, long time, String type, GameEvent event) {
    public static GameLogEntry of(String gameId, int seq, long time, GameEvent event) {
        // Replaying a game document's unarchived events must update the same log documents, even after a partial batch write.
        return new GameLogEntry(gameId + ":" + seq, gameId, seq, time, event.getClass().getSimpleName(), event);
    }
}
