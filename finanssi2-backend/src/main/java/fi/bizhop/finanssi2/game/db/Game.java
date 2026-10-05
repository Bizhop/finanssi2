package fi.bizhop.finanssi2.game.db;

import fi.bizhop.finanssi2.game.engine.GameState;
import lombok.Data;

import java.util.List;

@Data
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
        this.mode = java.util.Objects.requireNonNull(mode);
    }

    long createdAt;
    // Sequence number of the newest event in the log; the next one gets lastEventSeq + 1
    int lastEventSeq;
    // Holds the players from the moment the game is created
    GameState state = new GameState();
}
