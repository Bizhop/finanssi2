package fi.bizhop.finanssi2.game.db;

import fi.bizhop.finanssi2.game.engine.GameState;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
@SuppressWarnings("NullAway.Init") // id, version and creator are set when the game is created or loaded
public class Game {
    String id;
    // Optimistic locking: of two commands applied to the same version, only the first is saved
    Long version;
    GameStatus status = GameStatus.LOBBY;
    // Immutable session mode
    @lombok.Setter(lombok.AccessLevel.NONE)
    GameMode mode = GameMode.NORMAL;
    String creator;

    public Game() {}

    public Game(GameMode mode) {
        this.mode = Objects.requireNonNull(mode);
    }

    long createdAt;
    // Sequence number of the newest event in the log; the next one gets lastEventSeq + 1
    int lastEventSeq;
    // Holds the players from the moment the game is created
    GameState state = new GameState();
}
