package fi.bizhop.finanssi2.db;

import org.jspecify.annotations.Nullable;

public record ChatMessage(
    // Monotonic PostgreSQL identity, exposed as a string for the existing API.
    @Nullable String id,
    String userId,
    String message,
    long timestamp) {

    public ChatMessage withId(String id) {
        return new ChatMessage(id, userId, message, timestamp);
    }

}
