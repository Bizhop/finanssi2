package fi.bizhop.finanssi2.db;

import org.springframework.data.annotation.Id;
public record ChatMessage(
    // Monotonic PostgreSQL identity, exposed as a string for the existing API.
    @Id String id,
    String userId,
    // Sender's display name at the time of posting; null in messages saved before it was stored
    String name,
    String message,
    long timestamp,
    String photoUrl) {

    public ChatMessage withId(String id) {
        return new ChatMessage(id, userId, name, message, timestamp, photoUrl);
    }

}
