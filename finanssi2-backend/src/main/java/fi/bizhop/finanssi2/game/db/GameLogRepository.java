package fi.bizhop.finanssi2.game.db;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface GameLogRepository extends MongoRepository<GameLogEntry, String> {
    /** A game's events after the given sequence number, oldest first */
    List<GameLogEntry> findByGameIdAndSeqGreaterThanOrderBySeq(String gameId, int seq);

    void deleteByGameId(String gameId);
}
