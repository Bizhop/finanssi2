package fi.bizhop.finanssi2.game.engine;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PlayerState {
    // Firebase uid
    String uid;
    // Name and photo copied from the user when joining
    String name;
    String photoUrl;
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
    // Left jail and not on square 1 since: square 36 doesn't affect the player
    boolean jailExemption;

    public PlayerState(String uid, String name, String photoUrl, int piece) {
        this.uid = uid;
        this.name = name;
        this.photoUrl = photoUrl;
        this.piece = piece;
    }
}
