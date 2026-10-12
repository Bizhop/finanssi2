package fi.bizhop.finanssi2.game.engine;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Data
@NoArgsConstructor
public class BondState {
    int number;
    @Nullable String owner;
    public BondState(int number, @Nullable String owner) { this.number = number; this.owner = owner; }
}
