package fi.bizhop.finanssi2.game.db;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface GameRepository extends MongoRepository<Game, String> {
    /** Games with the given status, and any game the player is in */
    @Query("{ $or: [ { 'status': ?0 }, { 'state.players.uid': ?1 } ] }")
    List<Game> findByStatusOrPlayer(GameStatus status, String uid, Sort sort);
}
