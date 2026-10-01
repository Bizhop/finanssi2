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
    TurnPhase phase;
    GameSettings settings = GameSettings.DEFAULT;
    // Title deeds in board order and shares, with their owners; created at game start
    List<PropertyState> properties = new ArrayList<>();
    List<ShareState> shares = new ArrayList<>();
    List<BondState> bonds = new ArrayList<>();
    // The current player has bought a property or share this turn (one purchase per turn)
    boolean boughtThisTurn;
    // Oldest first; play waits until it is empty
    List<PendingDecision> pendingDecisions = new ArrayList<>();
    // Card ids in draw order. Hidden from clients, who would otherwise know the cards in advance.
    @JsonIgnore
    List<String> financeNewsDeck = new ArrayList<>();
    // The current lasting Finance News card, if any.
    String activeFinanceNews;

    /** The player who may act now: the one addressed by the first pending decision, otherwise the one whose turn it is */
    public String actor() {
        return pendingDecisions.isEmpty() ? currentPlayer : pendingDecisions.getFirst().player();
    }

    public PropertyState property(int square) {
        return properties.stream().filter(property -> property.getSquare() == square).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No property on square " + square));
    }

    public ShareState share(String id) {
        return shares.stream().filter(share -> share.getId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No share " + id));
    }

    public BondState bond(int number) {
        return bonds.stream().filter(bond -> bond.getNumber() == number).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No bond " + number));
    }

    public int totalLoans() {
        return players.stream().mapToInt(PlayerState::getLoans).sum();
    }

    /** The player whose turn it is */
    public PlayerState current() {
        return player(currentPlayer).orElseThrow();
    }

    public Optional<PlayerState> player(String uid) {
        return players.stream().filter(player -> player.getUid().equals(uid)).findFirst();
    }
}
