package fi.bizhop.finanssi2.game.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PropertyState {
    int square;
    // Player uid; null while the bank owns it
    String owner;
    boolean mortgaged;
    boolean built;

    public PropertyState(int square) {
        this.square = square;
    }
}
