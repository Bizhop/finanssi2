package fi.bizhop.finanssi2.game.engine;

/** A command from a player who is not the one to act now */
public class NotYourTurn extends RuntimeException {
    public NotYourTurn() {
        super("Not your turn");
    }
}
