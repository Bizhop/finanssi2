package fi.bizhop.finanssi2.game.engine;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BondState {
    int number;
    String owner;
    public BondState(int number, String owner) { this.number = number; this.owner = owner; }
}
