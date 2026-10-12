package fi.bizhop.finanssi2.game.db;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import fi.bizhop.finanssi2.game.engine.GameEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fi.bizhop.finanssi2.db.ApplicationUser;
import fi.bizhop.finanssi2.game.engine.GameState;
import fi.bizhop.finanssi2.game.engine.PendingDecision;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.dao.OptimisticLockingFailureException;

@Repository
@RequiredArgsConstructor
public class GameRepository {
    // Private on purpose: this mapper writes the persisted state, including deck order and sealed bids that API views must never carry.
    private final JsonMapper persistenceJsonMapper = JsonMapper.builder().build();
    @Qualifier("persistenceEventMapper") final ObjectMapper persistenceEventMapper;
    @PersistenceContext EntityManager entityManager;

    /** What authorization needs to know about a game, without loading its state */
    public record Access(GameMode mode, String creator) {}

    @Transactional(readOnly = true)
    public Optional<Access> findAccess(String id) {
        try {
            return entityManager.createQuery("select g.mode, g.creatorUser.id from GameEntity g where g.id = :id", Object[].class)
                    .setParameter("id", UUID.fromString(id)).getResultStream().findFirst()
                    .map(row -> new Access((GameMode) row[0], row[1].toString()));
        } catch (IllegalArgumentException invalidId) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<Game> findById(String id) {
        try {
            return Optional.ofNullable(entityManager.find(GameEntity.class, UUID.fromString(id))).map(this::model);
        } catch (IllegalArgumentException invalidId) {
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<Game> findByIdForUpdate(String id) {
        try {
            var entity = entityManager.find(GameEntity.class, UUID.fromString(id), LockModeType.PESSIMISTIC_WRITE);
            return Optional.ofNullable(entity).map(this::model);
        } catch (IllegalArgumentException invalidId) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<Game> findByStatusOrPlayer(GameStatus status, String playerId) {
        @SuppressWarnings("unchecked")
        var ids = (List<UUID>) entityManager.createNativeQuery("select id from games g where g.status = :status " +
                        "or exists (select 1 from jsonb_array_elements(g.state -> 'players') player " +
                        "where player ->> 'playerId' = :playerId) order by g.created_at desc")
                .setParameter("status", status.name()).setParameter("playerId", playerId).getResultList();
        return ids.stream().map(id -> entityManager.find(GameEntity.class, id)).map(this::model).toList();
    }

    @Transactional
    public Game save(Game game) {
        var entity = game.getVersion() == null
                ? new GameEntity()
                : entityManager.find(GameEntity.class, UUID.fromString(game.getId()));
        if (entity == null) throw new OptimisticLockingFailureException("Game was deleted");
        if (game.getVersion() != null && !game.getVersion().equals(entity.version)) {
            throw new OptimisticLockingFailureException("Game version changed");
        }
        apply(game, entity);
        applyState(game, entity);
        entity.saveNonce = UUID.randomUUID().toString();
        if (game.getVersion() == null) {
            entity.id = UUID.fromString(game.getId());
            entityManager.persist(entity);
        }
        entityManager.flush();
        return model(entity);
    }

    @Transactional
    public Game saveWithEvents(Game game, List<GameLogEntry> entries) {
        var saved = save(game);
        for (var entry : entries) {
            var event = new GameEventEntity();
            event.id = entry.id();
            event.game = entityManager.getReference(GameEntity.class, UUID.fromString(entry.gameId()));
            event.gameId = UUID.fromString(entry.gameId());
            event.seq = entry.seq();
            event.time = entry.time();
            event.type = entry.type();
            event.event = encodeEvent(entry.event());
            entityManager.persist(event);
        }
        entityManager.flush();
        return saved;
    }

    @Transactional
    public void delete(Game game) {
        var entity = entityManager.find(GameEntity.class, UUID.fromString(game.getId()));
        if (entity == null || !Objects.equals(entity.version, game.getVersion())) {
            throw new OptimisticLockingFailureException("Game version changed or game was deleted");
        }
        remove(entity);
    }

    private void remove(GameEntity entity) {
        entityManager.createQuery("delete from GameEventEntity e where e.game.id = :id").setParameter("id", entity.id).executeUpdate();
        entityManager.remove(entity);
    }

    @Transactional(readOnly = true)
    public boolean existsById(String id) {
        return findById(id).isPresent();
    }

    @Transactional
    public void deleteById(String id) {
        try {
            var entity = entityManager.find(GameEntity.class, UUID.fromString(id));
            if (entity != null) {
                remove(entity);
            }
        } catch (IllegalArgumentException invalidId) {
            // No row can have a non-UUID key.
        }
    }

    private void apply(Game source, GameEntity target) {
        target.status = source.getStatus();
        target.mode = source.getMode();
        target.creatorUser = entityManager.getReference(ApplicationUser.class, UUID.fromString(source.getCreator()));
        target.createdAt = source.getCreatedAt();
        target.lastEventSeq = source.getLastEventSeq();
    }

    private void applyState(Game source, GameEntity target) {
        try {
            var state = (ObjectNode) persistenceEventMapper
                    .readTree(persistenceJsonMapper.writeValueAsString(source.getState()));
            addStrings(state.putArray("financeNewsDeck"), source.getState().getFinanceNewsDeck());
            addStrings(state.putArray("stockTipDeck"), source.getState().getStockTipDeck());
            var decisions = state.withArray("pendingDecisions");
            for (int i = 0; i < source.getState().getPendingDecisions().size(); i++) {
                var decision = source.getState().getPendingDecisions().get(i);
                List<PendingDecision.Bid> bids = switch (decision) {
                    case PendingDecision.BondAuction auction -> auction.bids();
                    case PendingDecision.AssetAuction auction -> auction.bids();
                    default -> List.of();
                };
                if (decision instanceof PendingDecision.BondAuction
                        || decision instanceof PendingDecision.AssetAuction) {
                    var pending = (ObjectNode) decisions.get(i);
                    var encodedBids = pending.putArray("bids");
                    for (var bid : bids) encodedBids.addObject().put("player", bid.player()).put("amount", bid.amount());
                }
            }
            target.state = state;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not encode persistence JSON", e);
        }
    }

    private static void addStrings(ArrayNode target, List<String> values) {
        values.forEach(target::add);
    }

    private JsonNode encodeEvent(GameEvent event) {
        try {
            return persistenceEventMapper.readTree(persistenceEventMapper.writerFor(GameEvent.class).writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not encode game event as JSONB", e);
        }
    }

    private Game model(GameEntity entity) {
        var game = new Game(entity.mode);
        game.setId(entity.id.toString());
        game.setVersion(entity.version);
        game.setStatus(entity.status);
        game.setCreator(entity.creatorUser.getId().toString());
        game.setCreatedAt(entity.createdAt);
        game.setLastEventSeq(entity.lastEventSeq);
        var state = persistenceJsonMapper.readValue(entity.state.toString(), GameState.class);
        state.setFinanceNewsDeck(strings(entity.state.get("financeNewsDeck")));
        state.setStockTipDeck(strings(entity.state.get("stockTipDeck")));
        var decisions = entity.state.get("pendingDecisions");
        for (int i = 0; i < state.getPendingDecisions().size(); i++) {
            var decision = state.getPendingDecisions().get(i);
            var bids = bids(decisions.get(i).get("bids"));
            switch (decision) {
                case PendingDecision.BondAuction auction -> state.getPendingDecisions().set(i,
                        new PendingDecision.BondAuction(auction.player(), auction.order(), auction.index(), bids));
                case PendingDecision.AssetAuction auction -> state.getPendingDecisions().set(i,
                        new PendingDecision.AssetAuction(auction.seller(), auction.asset(),
                                auction.minimumBid(), auction.order(), auction.index(), bids));
                default -> { }
            }
        }
        game.setState(state);
        return game;
    }

    private static List<String> strings(JsonNode node) {
        var values = new ArrayList<String>();
        node.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static List<PendingDecision.Bid> bids(JsonNode node) {
        var values = new ArrayList<PendingDecision.Bid>();
        if (node != null && node.isArray()) {
            node.forEach(value -> values.add(new PendingDecision.Bid(
                    value.get("player").asText(), value.get("amount").asInt())));
        }
        return List.copyOf(values);
    }
}
