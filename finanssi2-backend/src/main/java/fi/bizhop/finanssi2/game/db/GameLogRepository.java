package fi.bizhop.finanssi2.game.db;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.bizhop.finanssi2.game.engine.GameEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class GameLogRepository {
    @Qualifier("persistenceEventMapper") final ObjectMapper persistenceEventMapper;
    @PersistenceContext EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<GameLogEntry> findByGameIdAndSeqGreaterThanOrderBySeq(String gameId, int seq) {
        return entityManager.createQuery("select e from GameEventEntity e where e.game.id = :gameId and e.seq > :seq order by e.seq",
                        GameEventEntity.class)
                .setParameter("gameId", java.util.UUID.fromString(gameId)).setParameter("seq", seq).getResultList().stream()
                .map(this::model).toList();
    }

    @Transactional
    public List<GameLogEntry> saveAll(Iterable<GameLogEntry> entries) {
        var saved = new java.util.ArrayList<GameLogEntry>();
        for (var entry : entries) {
            var row = new GameEventEntity();
            row.id = entry.id(); row.game = entityManager.getReference(GameEntity.class, java.util.UUID.fromString(entry.gameId()));
            row.gameId = java.util.UUID.fromString(entry.gameId());
            row.seq = entry.seq(); row.time = entry.time();
            row.type = entry.type();
            try {
                row.event = persistenceEventMapper.readTree(persistenceEventMapper.writerFor(GameEvent.class)
                        .writeValueAsString(entry.event()));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("Could not encode game event as JSONB", e);
            }
            entityManager.persist(row);
            saved.add(entry);
        }
        return List.copyOf(saved);
    }

    @Transactional
    public void deleteByGameId(String gameId) {
        entityManager.createQuery("delete from GameEventEntity e where e.game.id = :gameId")
                .setParameter("gameId", java.util.UUID.fromString(gameId)).executeUpdate();
    }

    private GameLogEntry model(GameEventEntity row) {
        try {
            return new GameLogEntry(row.id, row.gameId.toString(), row.seq, row.time, row.type,
                    persistenceEventMapper.treeToValue(row.event, GameEvent.class));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Could not decode game event JSONB", e);
        }
    }
}
