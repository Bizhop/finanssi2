package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.error.CodedError;
import fi.bizhop.finanssi2.error.ErrorCode;

import java.util.Map;

/** A command from a player who is not the one to act now */
public class NotYourTurn extends RuntimeException implements CodedError {
    public NotYourTurn() {
        super("Not your turn");
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.NOT_YOUR_TURN;
    }

    @Override
    public Map<String, Object> params() {
        return Map.of();
    }
}
