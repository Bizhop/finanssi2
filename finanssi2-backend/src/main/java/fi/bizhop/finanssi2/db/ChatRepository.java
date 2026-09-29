package fi.bizhop.finanssi2.db;

import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ChatRepository extends MongoRepository<ChatMessage, String> {
    /** Newest messages first */
    List<ChatMessage> findAllByOrderByIdDesc(Limit limit);

    /** Messages older than the given message, newest first */
    List<ChatMessage> findByIdLessThanOrderByIdDesc(String id, Limit limit);
}
