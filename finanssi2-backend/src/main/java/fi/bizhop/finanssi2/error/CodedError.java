package fi.bizhop.finanssi2.error;

import java.util.Map;

/** An exception that carries the {@link ErrorCode} and the values the frontend puts into its message (e.g. a square number) */
public interface CodedError {
    ErrorCode code();

    /** Values for the message's placeholders; empty when the wording has none */
    Map<String, Object> params();
}
