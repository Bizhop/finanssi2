package fi.bizhop.finanssi2.game.engine;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class PlayerState {
    // Application user UUID, or a game-local debug seat ID
    String playerId;
    // Piece 0–5, unique within a game
    int piece;
    int cash;
    // Square 1–46; 0 before the game starts
    int position;
    boolean car;
    int loans;
    // Out of the game (bankrupt); skipped in turn order
    boolean out;
    // Turns still to skip in jail (square 24)
    int missedTurns;
    boolean missedTurnsInJail = true;
    // Left jail and not on square 1 since: square 36 doesn't affect the player
    boolean jailExemption;
    boolean bailRollPending;
    boolean transportNewsDue;
    boolean noMovementRollThisTurn;
    // Held Stock Tip card ids, in acquisition order.
    List<String> heldStockTips = new ArrayList<>();

    public PlayerState(String playerId, int piece) {
        this.playerId = playerId;
        this.piece = piece;
    }
}
