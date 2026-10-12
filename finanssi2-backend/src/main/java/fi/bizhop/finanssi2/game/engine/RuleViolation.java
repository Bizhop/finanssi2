package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.error.CodedError;
import fi.bizhop.finanssi2.error.ErrorCode;

import java.util.Map;

/** A command the rules do not allow in the current state; the state is left unchanged */
public class RuleViolation extends RuntimeException implements CodedError {
    private final ErrorCode code;
    private final Map<String, Object> params;

    public RuleViolation(ErrorCode code, String reason) {
        this(code, reason, Map.of());
    }

    public RuleViolation(ErrorCode code, String reason, Map<String, Object> params) {
        super(reason);
        this.code = code;
        this.params = Map.copyOf(params);
    }

    @Override
    public ErrorCode code() {
        return code;
    }

    @Override
    public Map<String, Object> params() {
        return params;
    }
}
