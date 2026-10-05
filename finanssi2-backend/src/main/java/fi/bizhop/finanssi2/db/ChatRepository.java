package fi.bizhop.finanssi2.db;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class ChatRepository {
    @PersistenceContext EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<ChatMessage> findAllByOrderByIdDesc(Limit limit) {
        return entityManager.createQuery("select m from ChatMessageEntity m order by m.id desc", ChatMessageEntity.class)
                .setMaxResults(limit.max()).getResultList().stream().map(ChatRepository::model).toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> findByIdLessThanOrderByIdDesc(String id, Limit limit) {
        return entityManager.createQuery("select m from ChatMessageEntity m where m.id < :id order by m.id desc", ChatMessageEntity.class)
                .setParameter("id", Long.parseLong(id)).setMaxResults(limit.max()).getResultList().stream().map(ChatRepository::model).toList();
    }

    @Transactional
    public ChatMessage save(ChatMessage message) {
        var row = new ChatMessageEntity();
        row.username = message.username(); row.name = message.name(); row.message = message.message();
        row.timestamp = message.timestamp(); row.photoUrl = message.photoUrl();
        entityManager.persist(row);
        entityManager.flush();
        return model(row);
    }

    @Transactional(readOnly = true)
    public Optional<ChatMessage> findById(String id) {
        try {
            return Optional.ofNullable(entityManager.find(ChatMessageEntity.class, Long.parseLong(id))).map(ChatRepository::model);
        } catch (NumberFormatException invalidId) {
            return Optional.empty();
        }
    }

    @Transactional
    public void deleteById(String id) {
        findById(id).ifPresent(message -> entityManager.remove(entityManager.find(ChatMessageEntity.class, Long.parseLong(id))));
    }

    private static ChatMessage model(ChatMessageEntity row) {
        return new ChatMessage(String.format(java.util.Locale.ROOT, "%019d", row.id), row.username, row.name,
                row.message, row.timestamp, row.photoUrl);
    }
}
