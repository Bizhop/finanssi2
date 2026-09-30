package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.GameData;

/**
 * Rule values that cards and settings can change. Callers ask here instead of using the constants, so Finance News, Stock Tips and
 * house rules only change this class.
 */
public class Rules {
    static final int BANK_FIRST_SQUARE = 34;

    final GameData gameData;

    public Rules(GameData gameData) {
        this.gameData = gameData;
    }

    /** Number of dice for a movement roll: two with a car, one without, and one inside the bank (starting on 34–46) */
    public int movementDice(GameState state, PlayerState player) {
        return player.isCar() && player.getPosition() < BANK_FIRST_SQUARE ? 2 : 1;
    }
}
