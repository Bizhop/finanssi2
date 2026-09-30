package fi.bizhop.finanssi2.game.service;

import fi.bizhop.finanssi2.game.engine.Dice;

/** Dice for a game's next command */
public interface DiceSource {
    Dice forGame(String gameId);
}
