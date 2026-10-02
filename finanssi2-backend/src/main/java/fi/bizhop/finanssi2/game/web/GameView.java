package fi.bizhop.finanssi2.game.web;

import fi.bizhop.finanssi2.game.db.Game;

import java.util.List;

/** A game as the requesting user sees it: the commands they may send now are listed, so clients need not know the rules */
public record GameView(Game game, List<String> allowedCommands, String actingPlayer) {
    public GameView {
        allowedCommands = List.copyOf(allowedCommands);
    }
}
