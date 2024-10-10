package fi.bizhop.finanssi2.db;

import fi.bizhop.finanssi2.model.db.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatRepository extends MongoRepository<ChatMessage, String> {}
