package fi.bizhop.finanssi2.game.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
}
