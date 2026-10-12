package fi.bizhop.finanssi2.game.service;

import fi.bizhop.finanssi2.error.CodedError;
import fi.bizhop.finanssi2.error.ErrorCode;

import java.util.Map;

/** The user may not perform this action in this game (e.g. starting a game they did not create) */
public class NotAllowedException extends RuntimeException implements CodedError {
    private final ErrorCode code;

    public NotAllowedException(ErrorCode code, String reason) {
        super(reason);
        this.code = code;
    }

    @Override
    public ErrorCode code() {
        return code;
    }

    @Override
    public Map<String, Object> params() {
        return Map.of();
    }
}
