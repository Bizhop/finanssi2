package fi.bizhop.finanssi2.model.db;

import lombok.Value;
import org.springframework.data.mongodb.core.mapping.Document;

@Value
@Document(collection = "chat")
public class ChatMessage {
    String username;
    String message;
    long timestamp;
}
