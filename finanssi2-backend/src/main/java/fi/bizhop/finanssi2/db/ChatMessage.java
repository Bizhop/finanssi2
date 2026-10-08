package fi.bizhop.finanssi2.db;

import org.springframework.data.annotation.Id;
public record ChatMessage(
    // Monotonic PostgreSQL identity, exposed as a string for the existing API.
    @Id String id,
    String userId,
    String message,
    long timestamp) {

    public ChatMessage withId(String id) {
        return new ChatMessage(id, userId, message, timestamp);
    }

}
