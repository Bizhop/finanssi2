package fi.bizhop.finanssi2.game.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PropertyState {
    int square;
    // Player playerId; null while the bank owns it
    @Nullable String owner;
    boolean mortgaged;
    boolean built;

    public PropertyState(int square) {
        this.square = square;
    }
}
