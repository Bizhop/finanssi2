package fi.bizhop.finanssi2.game.db;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fi.bizhop.finanssi2.game.engine.GameState;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "games")
public class Game {
    @Id String id;
    // Optimistic locking: of two commands applied to the same version, only the first is saved
    @Version Long version;
    GameStatus status = GameStatus.LOBBY;
    // Immutable session mode; missing fields in legacy documents retain NORMAL.
    @lombok.Setter(lombok.AccessLevel.NONE)
    GameMode mode = GameMode.NORMAL;
    String creator;

    public Game() {}

    public Game(GameMode mode) {
        this.mode = java.util.Objects.requireNonNull(mode);
    }

    long createdAt;
    // Sequence number of the newest event in the log; the next one gets lastEventSeq + 1
    int lastEventSeq;
    // Holds the players from the moment the game is created
    GameState state = new GameState();
    // Saved atomically with state. Retained until a later save confirms they have reached the separate log collection.
    @JsonIgnore
    List<GameLogEntry> unarchivedEvents = List.of();
}
