package fi.bizhop.finanssi2.game.db;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Sort;
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

@Repository
@RequiredArgsConstructor
public class GameRepository {
    final JsonMapper persistenceJsonMapper;
    @Qualifier("persistenceEventMapper") final ObjectMapper persistenceEventMapper;
    @PersistenceContext EntityManager entityManager;

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
            var entity = entityManager.find(GameEntity.class, UUID.fromString(id), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            return Optional.ofNullable(entity).map(this::model);
        } catch (IllegalArgumentException invalidId) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<Game> findByStatusOrPlayer(GameStatus status, String playerId, Sort sort) {
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
        if (entity == null) throw new org.springframework.dao.OptimisticLockingFailureException("Game was deleted");
        if (game.getVersion() != null && !game.getVersion().equals(entity.version)) {
            throw new org.springframework.dao.OptimisticLockingFailureException("Game version changed");
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
        if (entity == null || !java.util.Objects.equals(entity.version, game.getVersion())) {
            throw new org.springframework.dao.OptimisticLockingFailureException("Game version changed or game was deleted");
        }
        entityManager.createQuery("delete from GameEventEntity e where e.game.id = :id")
                .setParameter("id", UUID.fromString(game.getId())).executeUpdate();
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
                entityManager.createQuery("delete from GameEventEntity e where e.game.id = :id")
                        .setParameter("id", UUID.fromString(id)).executeUpdate();
                entityManager.remove(entity);
            }
        } catch (IllegalArgumentException invalidId) {
            // No row can have a non-UUID key.
        }
    }

    private void apply(Game source, GameEntity target) {
        target.status = source.getStatus();
        target.mode = source.getMode();
        target.creatorUser = entityManager.getReference(fi.bizhop.finanssi2.db.ApplicationUser.class, UUID.fromString(source.getCreator()));
        target.createdAt = source.getCreatedAt();
        target.lastEventSeq = source.getLastEventSeq();
    }

    private void applyState(Game source, GameEntity target) {
        try {
            var state = (com.fasterxml.jackson.databind.node.ObjectNode) persistenceEventMapper
                    .readTree(persistenceJsonMapper.writeValueAsString(source.getState()));
            addStrings(state.putArray("financeNewsDeck"), source.getState().getFinanceNewsDeck());
            addStrings(state.putArray("stockTipDeck"), source.getState().getStockTipDeck());
            var decisions = state.withArray("pendingDecisions");
            for (int i = 0; i < source.getState().getPendingDecisions().size(); i++) {
                var decision = source.getState().getPendingDecisions().get(i);
                List<fi.bizhop.finanssi2.game.engine.PendingDecision.Bid> bids = switch (decision) {
                    case fi.bizhop.finanssi2.game.engine.PendingDecision.BondAuction auction -> auction.bids();
                    case fi.bizhop.finanssi2.game.engine.PendingDecision.AssetAuction auction -> auction.bids();
                    default -> List.of();
                };
                if (decision instanceof fi.bizhop.finanssi2.game.engine.PendingDecision.BondAuction
                        || decision instanceof fi.bizhop.finanssi2.game.engine.PendingDecision.AssetAuction) {
                    var pending = (com.fasterxml.jackson.databind.node.ObjectNode) decisions.get(i);
                    var encodedBids = pending.putArray("bids");
                    for (var bid : bids) encodedBids.addObject().put("player", bid.player()).put("amount", bid.amount());
                }
            }
            target.state = state;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Could not encode persistence JSON", e);
        }
    }

    private static void addStrings(com.fasterxml.jackson.databind.node.ArrayNode target, List<String> values) {
        values.forEach(target::add);
    }

    private com.fasterxml.jackson.databind.JsonNode encodeEvent(GameEvent event) {
        try {
            return persistenceEventMapper.readTree(persistenceEventMapper.writerFor(GameEvent.class).writeValueAsString(event));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
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
        var state = persistenceJsonMapper.readValue(entity.state.toString(), fi.bizhop.finanssi2.game.engine.GameState.class);
        state.setFinanceNewsDeck(strings(entity.state.get("financeNewsDeck")));
        state.setStockTipDeck(strings(entity.state.get("stockTipDeck")));
        var decisions = entity.state.get("pendingDecisions");
        for (int i = 0; i < state.getPendingDecisions().size(); i++) {
            var decision = state.getPendingDecisions().get(i);
            var bids = bids(decisions.get(i).get("bids"));
            switch (decision) {
                case fi.bizhop.finanssi2.game.engine.PendingDecision.BondAuction auction -> state.getPendingDecisions().set(i,
                        new fi.bizhop.finanssi2.game.engine.PendingDecision.BondAuction(auction.player(), auction.order(), auction.index(), bids));
                case fi.bizhop.finanssi2.game.engine.PendingDecision.AssetAuction auction -> state.getPendingDecisions().set(i,
                        new fi.bizhop.finanssi2.game.engine.PendingDecision.AssetAuction(auction.seller(), auction.asset(),
                                auction.minimumBid(), auction.order(), auction.index(), bids));
                default -> { }
            }
        }
        game.setState(state);
        return game;
    }

    private static List<String> strings(com.fasterxml.jackson.databind.JsonNode node) {
        var values = new java.util.ArrayList<String>();
        node.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static List<fi.bizhop.finanssi2.game.engine.PendingDecision.Bid> bids(com.fasterxml.jackson.databind.JsonNode node) {
        var values = new java.util.ArrayList<fi.bizhop.finanssi2.game.engine.PendingDecision.Bid>();
        if (node != null && node.isArray()) {
            node.forEach(value -> values.add(new fi.bizhop.finanssi2.game.engine.PendingDecision.Bid(
                    value.get("player").asText(), value.get("amount").asInt())));
        }
        return List.copyOf(values);
    }
}
