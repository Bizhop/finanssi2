package fi.bizhop.finanssi2.game.engine;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Everything on the table. The engine changes it in place; the service saves it as part of the game document. */
@Data
public class GameState {
    // In join order
    List<PlayerState> players = new ArrayList<>();
    // Player uids in turn order, starting with the first player; empty before the game starts
    List<String> turnOrder = new ArrayList<>();
    String currentPlayer;
    // Card ids in draw order. Hidden from clients, who would otherwise know the cards in advance.
    @JsonIgnore
    List<String> financeNewsDeck = new ArrayList<>();

    public Optional<PlayerState> player(String uid) {
        return players.stream().filter(player -> player.getUid().equals(uid)).findFirst();
    }
}
