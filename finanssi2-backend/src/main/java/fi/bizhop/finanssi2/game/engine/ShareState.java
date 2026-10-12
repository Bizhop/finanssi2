package fi.bizhop.finanssi2.game.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Data
@SuppressWarnings("NullAway.Init") // filled in by Jackson after the no-arg constructor
@NoArgsConstructor
@AllArgsConstructor
public class ShareState {
    String id;
    // Player playerId; null while the bank owns it
    @Nullable String owner;
}
