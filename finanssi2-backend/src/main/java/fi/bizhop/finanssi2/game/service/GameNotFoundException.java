package fi.bizhop.finanssi2.game.service;

import fi.bizhop.finanssi2.error.CodedError;
import fi.bizhop.finanssi2.error.ErrorCode;

import java.util.Map;

public class GameNotFoundException extends RuntimeException implements CodedError {
    public GameNotFoundException(String id) {
        super("No game " + id);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.GAME_NOT_FOUND;
    }

    @Override
    public Map<String, Object> params() {
        return Map.of();
    }
}
