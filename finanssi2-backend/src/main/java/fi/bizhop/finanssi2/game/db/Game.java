package fi.bizhop.finanssi2.game.db;

import fi.bizhop.finanssi2.game.engine.GameState;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "games")
public class Game {
    @Id String id;
    // Optimistic locking: of two commands applied to the same version, only the first is saved
    @Version Long version;
    GameStatus status = GameStatus.LOBBY;
    String creator;
    long createdAt;
    // Sequence number of the newest event in the log; the next one gets lastEventSeq + 1
    int lastEventSeq;
    // Holds the players from the moment the game is created
    GameState state = new GameState();
}
