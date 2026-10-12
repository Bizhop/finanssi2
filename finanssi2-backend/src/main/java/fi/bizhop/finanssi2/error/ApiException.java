package fi.bizhop.finanssi2.error;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** A request the API refuses with the given status; {@code message} is the English fallback shown when the frontend has no wording for the code */
public class ApiException extends RuntimeException implements CodedError {
    private final HttpStatus status;
    private final ErrorCode code;
    private final Map<String, Object> params;

    public ApiException(HttpStatus status, ErrorCode code, String message) {
        this(status, code, message, Map.of(), null);
    }

    public ApiException(HttpStatus status, ErrorCode code, String message, Map<String, Object> params) {
        this(status, code, message, params, null);
    }

    public ApiException(HttpStatus status, ErrorCode code, String message, Map<String, Object> params, @Nullable Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.params = Map.copyOf(params);
    }

    public HttpStatus status() {
        return status;
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
