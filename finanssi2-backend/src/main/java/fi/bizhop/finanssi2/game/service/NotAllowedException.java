package fi.bizhop.finanssi2.game.service;

/** The user may not perform this action in this game (e.g. starting a game they did not create) */
public class NotAllowedException extends RuntimeException {
    public NotAllowedException(String reason) {
        super(reason);
    }
}
