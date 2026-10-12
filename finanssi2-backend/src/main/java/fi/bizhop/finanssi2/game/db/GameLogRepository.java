package fi.bizhop.finanssi2.game.db;

import jakarta.persistence.EntityManager;
import fi.bizhop.finanssi2.game.engine.GameEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class GameLogRepository {
    final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<GameLogEntry> findByGameIdAndSeqGreaterThanOrderBySeq(String gameId, int seq) {
        return entityManager.createQuery("select e from GameEventEntity e where e.game.id = :gameId and e.seq > :seq order by e.seq",
                        GameEventEntity.class)
                .setParameter("gameId", UUID.fromString(gameId)).setParameter("seq", seq).getResultList().stream()
                .map(this::model).toList();
    }

    private GameLogEntry model(GameEventEntity row) {
        return new GameLogEntry(row.id, row.gameId.toString(), row.seq, row.time, row.type,
                PersistenceJson.EVENTS.treeToValue(row.event, GameEvent.class));
    }
}
