package fi.bizhop.finanssi2.game.web;

import fi.bizhop.finanssi2.game.db.Game;
import fi.bizhop.finanssi2.game.engine.CommandOptions;

import java.util.List;

/**
 * A game as the requesting user sees it: the commands they may send now are listed, with the concrete choices and their current
 * prices, so clients need not know the rules
 */
public record GameView(Game game, List<String> allowedCommands, String actingPlayer, CommandOptions options) {
    public GameView {
        allowedCommands = List.copyOf(allowedCommands);
    }
}
