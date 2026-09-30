package fi.bizhop.finanssi2.game.service;

public class GameNotFoundException extends RuntimeException {
    public GameNotFoundException(String id) {
        super("No game " + id);
    }
}
