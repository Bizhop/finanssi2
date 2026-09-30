package fi.bizhop.finanssi2.game.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareState {
    String id;
    // Player uid; null while the bank owns it
    String owner;
}
