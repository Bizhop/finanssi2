package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.Square;

import java.util.List;

/** The effect of landing on a square of one type */
@FunctionalInterface
public interface SquareHandler {
    List<GameEvent> land(GameState state, PlayerState player, Square square);
}
