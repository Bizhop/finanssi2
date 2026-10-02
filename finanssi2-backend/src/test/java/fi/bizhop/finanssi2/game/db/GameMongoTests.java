package fi.bizhop.finanssi2.game.db;

import fi.bizhop.finanssi2.db.ChatMessage;
import fi.bizhop.finanssi2.db.ChatRepository;
import fi.bizhop.finanssi2.game.engine.BondContinuation;
import fi.bizhop.finanssi2.game.engine.Charge;
import fi.bizhop.finanssi2.game.engine.GameCommand;
import fi.bizhop.finanssi2.game.engine.GameEvent;
import fi.bizhop.finanssi2.game.engine.GameSettings;
import fi.bizhop.finanssi2.game.engine.LoanLimit;
import fi.bizhop.finanssi2.game.engine.MoneyReason;
import fi.bizhop.finanssi2.game.engine.PendingDecision;
import fi.bizhop.finanssi2.game.engine.ScriptedDice;
import fi.bizhop.finanssi2.game.service.DiceSource;
import fi.bizhop.finanssi2.game.service.GameService;
import fi.bizhop.finanssi2.security.User;
import fi.bizhop.finanssi2.service.MessagingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Saves and loads games in MongoDB; subclasses choose the database. Removes only the documents it created. */
@SpringBootTest(properties = "finanssi2.debug.allowed-emails=owner@example.com,other@example.com")
@ActiveProfiles("test")
abstract class GameMongoTests {
    @Autowired
    GameService gameService;
    @Autowired
    GameRepository gameRepository;
    @Autowired
    GameLogRepository gameLogRepository;
    @Autowired
    ChatRepository chatRepository;
    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    ObjectMapper objectMapper;
    @MockitoBean
    MessagingService messagingService;
    @MockitoBean
    DiceSource diceSource;

    final List<String> createdGames = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (var id : createdGames) {
            gameRepository.deleteById(id);
            gameLogRepository.deleteByGameId(id);
        }
    }

    static User user(String uid) {
        return new User("it-" + uid, uid + "@example.com", "Player " + uid, null);
    }

    Game create(String uid) {
        var game = gameService.create(user(uid));
        createdGames.add(game.getId());
        return game;
    }

    @Test
    void debugOwnerResolvesAllAuctionBidsAndGrandDrawOffers() {
        var owner = new User("debug-owner", "owner@example.com", "Owner", null, true);
        var game = gameService.createDebug(owner, 3, GameSettings.DEFAULT);
        var id = game.getId();
        createdGames.add(id);
        when(diceSource.forGame(any())).thenReturn(new ScriptedDice(6, 6, 2, 2, 1, 1));
        game = gameService.start(id, owner);
        game.getState().current().setPosition(37);
        game = gameRepository.save(game);
        gameService.debugCommand(id, owner, game.getState().actor(), game.getVersion(), new GameCommand.Roll(), List.of(1));
        for (int bid = 0; bid < 3; bid++) {
            game = gameService.get(id);
            assertInstanceOf(PendingDecision.BondAuction.class, game.getState().getPendingDecisions().getFirst());
            assertTrue(gameService.allowedCommands(game, owner).contains("BidBond"));
            gameService.debugCommand(id, owner, game.getState().actor(), game.getVersion(), new GameCommand.BidBond(0), null);
        }
        game = gameService.get(id);
        assertTrue(game.getState().getPendingDecisions().isEmpty());
        game.getState().current().setPosition(4);
        game.getState().setPhase(fi.bizhop.finanssi2.game.engine.TurnPhase.BEFORE_ROLL);
        game = gameRepository.save(game);
        game = gameService.nextCard(id, owner, "FINANCE_NEWS", "FL-02", game.getVersion());
        gameService.debugCommand(id, owner, game.getState().actor(), game.getVersion(), new GameCommand.Roll(), List.of(1));
        for (int offer = 0; offer < 3; offer++) {
            game = gameService.get(id);
            assertInstanceOf(PendingDecision.BondOffer.class, game.getState().getPendingDecisions().getFirst());
            gameService.debugCommand(id, owner, game.getState().actor(), game.getVersion(), new GameCommand.Pass(),
                    java.util.Collections.nCopies(32, 1));
        }
        game = gameService.get(id);
        assertTrue(game.getState().getPendingDecisions().isEmpty());
        var seller = game.getState().getPlayers().get(2).getUid();
        var order = game.getState().getPlayers().stream().map(fi.bizhop.finanssi2.game.engine.PlayerState::getUid)
                .filter(uid -> !uid.equals(seller)).toList();
        var share = game.getState().getShares().getFirst();
        share.setOwner(seller);
        game.getState().getPendingDecisions().add(new PendingDecision.AssetAuction(seller, "S:" + share.getId(), 0,
                order, 0, List.of()));
        game = gameRepository.save(game);
        for (int bid = 0; bid < 2; bid++) {
            game = gameService.get(id);
            assertTrue(gameService.allowedCommands(game, owner).contains("BidAsset"));
            gameService.debugCommand(id, owner, game.getState().actor(), game.getVersion(), new GameCommand.BidAsset(500), null);
        }
        game = gameService.get(id);
        assertTrue(game.getState().getPendingDecisions().isEmpty());
        assertEquals(owner.uid(), game.getState().share(share.getId()).getOwner());
    }

    @Test
    void debugCardSelectionPreservesDeckAndDrawsThroughGameplay() {
        var owner = new User("debug-owner", "owner@example.com", "Owner", null, true);
        var game = gameService.createDebug(owner, 2, GameSettings.DEFAULT);
        var id = game.getId();
        createdGames.add(id);
        when(diceSource.forGame(any())).thenReturn(new ScriptedDice(6, 6, 1, 1));
        game = gameService.start(id, owner);
        game.getState().current().setPosition(4);
        game.getState().setActiveFinanceNews("FL-09");
        game = gameRepository.save(game);
        var before = game.getState().getFinanceNewsDeck().stream().filter(card -> !card.equals("FL-05")).toList();
        var selected = gameService.nextCard(id, owner, "FINANCE_NEWS", "FL-05", game.getVersion());
        assertEquals("FL-09", selected.getState().getActiveFinanceNews());
        assertEquals("FL-05", selected.getState().getFinanceNewsDeck().getFirst());
        assertEquals(before, selected.getState().getFinanceNewsDeck().subList(1, 21));
        assertInstanceOf(GameEvent.DebugDeckChanged.class,
                gameService.events(id, selected.getLastEventSeq() - 1, owner).getFirst().event());
        var rolls = gameService.debugCommand(id, owner, owner.uid(), selected.getVersion(), new GameCommand.Roll(), List.of(1));
        assertTrue(rolls.stream().anyMatch(e -> e.event() instanceof GameEvent.FinanceNewsDrawn drawn && drawn.card().equals("FL-05")));
        assertEquals(21, gameService.get(id).getState().getFinanceNewsDeck().stream().distinct().count());
        var version = selected.getVersion();
        assertThrows(fi.bizhop.finanssi2.game.engine.RuleViolation.class,
                () -> gameService.nextCard(id, owner, "FINANCE_NEWS", "FL-01", version));
    }

    @Test
    void debugCardSelectionRejectsHeldCardsPendingDecisionsAndFinishedGames() {
        var owner = new User("debug-owner", "owner@example.com", "Owner", null, true);
        var game = gameService.createDebug(owner, 2, GameSettings.DEFAULT);
        var id = game.getId();
        createdGames.add(id);
        when(diceSource.forGame(any())).thenReturn(new ScriptedDice(6, 6, 1, 1));
        game = gameService.start(id, owner);
        game.getState().getStockTipDeck().remove("PV-01");
        game.getState().current().getHeldStockTips().add("PV-01");
        game = gameRepository.save(game);
        var version = game.getVersion();
        for (var choice : List.of(List.of("STOCK_TIP", "PV-01"), List.of("FINANCE_NEWS", "unknown"),
                List.of("unknown", "FL-01"))) {
            assertThrows(fi.bizhop.finanssi2.game.engine.RuleViolation.class,
                    () -> gameService.nextCard(id, owner, choice.getFirst(), choice.getLast(), version));
        }
        game.getState().getPendingDecisions().add(new PendingDecision.BondOffer(owner.uid(), BondContinuation.NONE));
        game = gameRepository.save(game);
        var pendingVersion = game.getVersion();
        assertThrows(fi.bizhop.finanssi2.game.engine.RuleViolation.class,
                () -> gameService.nextCard(id, owner, "STOCK_TIP", "PV-02", pendingVersion));
        game.getState().getPendingDecisions().clear();
        game.setStatus(GameStatus.FINISHED);
        game = gameRepository.save(game);
        var finishedVersion = game.getVersion();
        assertThrows(fi.bizhop.finanssi2.game.engine.RuleViolation.class,
                () -> gameService.nextCard(id, owner, "FINANCE_NEWS", "FL-01", finishedVersion));
    }

    @Test
    void debugCommandsControlDecisionsAndSurviveOwnerElimination() {
        var owner = new User("debug-owner", "owner@example.com", "Owner", null, true);
        var game = gameService.createDebug(owner, 3, GameSettings.DEFAULT);
        var id = game.getId();
        createdGames.add(id);
        when(diceSource.forGame(any())).thenReturn(new ScriptedDice(6, 6, 2, 2, 1, 1));
        game = gameService.start(id, owner);
        var seat = game.getState().getPlayers().get(1).getUid();
        game.getState().getPendingDecisions().add(new PendingDecision.RaiseFunds(seat, owner.uid(),
                List.of(new Charge(500, MoneyReason.RENT))));
        game = gameRepository.save(game);
        var version = game.getVersion();
        var seq = game.getLastEventSeq();
        assertTrue(gameService.allowedCommands(game, owner).contains("Pay"));
        assertThrows(fi.bizhop.finanssi2.game.engine.RuleViolation.class,
                () -> gameService.debugCommand(id, owner, owner.uid(), version, new GameCommand.Pay(), List.of(6)));
        assertThrows(fi.bizhop.finanssi2.game.engine.RuleViolation.class,
                () -> gameService.debugCommand(id, owner, seat, version - 1, new GameCommand.Pay(), null));
        assertEquals(seq, gameService.get(id).getLastEventSeq());
        gameService.debugCommand(id, owner, seat, version, new GameCommand.Pay(), null);
        game = gameService.get(id);
        gameService.debugCommand(id, owner, game.getState().actor(), game.getVersion(), new GameCommand.Resign(), null);
        game = gameService.get(id);
        assertTrue(game.getState().player(owner.uid()).orElseThrow().isOut());
        assertEquals(seat, game.getState().actor());
        assertTrue(gameService.allowedCommands(game, owner).contains("Roll"));
        gameService.debugCommand(id, owner, seat, game.getVersion(), new GameCommand.Roll(), List.of(1));
        game = gameService.get(id);
        assertEquals(21, game.getState().current().getPosition());
        gameService.debugCommand(id, owner, game.getState().actor(), game.getVersion(), new GameCommand.EndGame(), null);
        assertEquals(GameStatus.FINISHED, gameService.get(id).getStatus());
    }

    @Test
    void debugDiceAreDiscardedBetweenCommandsAndOnRejection() {
        var owner = new User("debug-owner", "owner@example.com", "Owner", null, true);
        var game = gameService.createDebug(owner, 2, GameSettings.DEFAULT);
        var id = game.getId();
        createdGames.add(id);
        when(diceSource.forGame(any())).thenReturn(new ScriptedDice(6, 6, 1, 1));
        game = gameService.start(id, owner);
        var version = game.getVersion();
        assertThrows(fi.bizhop.finanssi2.game.engine.RuleViolation.class,
                () -> gameService.debugCommand(id, owner, owner.uid(), version, new GameCommand.EndTurn(), List.of(6)));
        when(diceSource.forGame(any())).thenReturn(new ScriptedDice(2));
        gameService.debugCommand(id, owner, owner.uid(), version, new GameCommand.Roll(), List.of(1, 6));
        game = gameService.get(id);
        assertEquals(21, game.getState().current().getPosition());
        gameService.debugCommand(id, owner, game.getState().actor(), game.getVersion(), new GameCommand.EndTurn(), null);
        game = gameService.get(id);
        gameService.debugCommand(id, owner, game.getState().actor(), game.getVersion(), new GameCommand.Roll(), null);
        assertEquals(3, gameService.get(id).getState().current().getPosition());
        assertThrows(fi.bizhop.finanssi2.game.service.NotAllowedException.class,
                () -> gameService.command(id, owner, new GameCommand.EndTurn()));
    }

    @Test
    void debugLifecycleIsPrivateAndDeletesHistory() {
        var owner = new User("debug-owner", "owner@example.com", "Owner", null, true);
        var other = new User("debug-other", "other@example.com", "Other", null, true);
        var game = gameService.createDebug(owner, 6, GameSettings.DEFAULT);
        createdGames.add(game.getId());
        assertEquals(GameMode.DEBUG, game.getMode());
        assertEquals(6, game.getState().getPlayers().size());
        assertEquals("debug:" + game.getId() + ":seat:2", game.getState().getPlayers().get(1).getUid());
        assertTrue(gameService.list(owner).stream().anyMatch(g -> g.getId().equals(game.getId())));
        assertFalse(gameService.list(other).stream().anyMatch(g -> g.getId().equals(game.getId())));
        assertThrows(fi.bizhop.finanssi2.game.service.NotAllowedException.class, () -> gameService.get(game.getId(), other));
        assertThrows(fi.bizhop.finanssi2.game.service.NotAllowedException.class, () -> gameService.events(game.getId(), 0, other));
        assertThrows(fi.bizhop.finanssi2.game.service.NotAllowedException.class, () -> gameService.join(game.getId(), owner));
        assertThrows(fi.bizhop.finanssi2.game.service.NotAllowedException.class, () -> gameService.leave(game.getId(), owner));
        assertThrows(fi.bizhop.finanssi2.game.service.NotAllowedException.class, () -> gameService.deleteDebug(game.getId(), other));
        org.mockito.Mockito.verify(messagingService, org.mockito.Mockito.never()).send(
                org.mockito.ArgumentMatchers.eq("/topic/games"), org.mockito.ArgumentMatchers.any());
        var stale = gameService.get(game.getId());
        gameService.deleteDebug(game.getId(), owner);
        assertFalse(gameRepository.existsById(game.getId()));
        assertTrue(gameLogRepository.findByGameIdAndSeqGreaterThanOrderBySeq(game.getId(), 0).isEmpty());
        assertThrows(OptimisticLockingFailureException.class, () -> gameRepository.save(stale));
    }

    @Test
    void legacyDocumentDefaultsToNormalMode() {
        var game = create("legacy");
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(game.getId())),
                new Update().unset("mode"), Game.class);
        assertEquals(GameMode.NORMAL, gameRepository.findById(game.getId()).orElseThrow().getMode());
    }

    @Test
    void testGameAndEventsRoundTrip() {
        var id = create("a").getId();
        gameService.join(id, user("b"));
        gameService.changeSettings(id, user("a"), new GameSettings(LoanLimit.UNLIMITED));
        when(diceSource.forGame(any())).thenReturn(new ScriptedDice(6, 6, 1, 1, 4));
        gameService.start(id, user("a"));
        // Fix the next card so this persistence test does not depend on the shuffled deck.
        var started = gameService.get(id);
        started.getState().getFinanceNewsDeck().remove("FL-05");
        started.getState().getFinanceNewsDeck().addFirst("FL-05");
        gameRepository.save(started);
        // Two loans, then from square 1 to 5 and draw the no-immediate-effect card.
        gameService.command(id, user("a"), new GameCommand.TakeLoan());
        gameService.command(id, user("a"), new GameCommand.TakeLoan());
        gameService.command(id, user("a"), new GameCommand.Roll());

        // A pending decision is stored with its type
        var game = gameService.get(id);
        game.getState().getBonds().getFirst().setOwner("it-a");
        game.getState().getPendingDecisions().add(
                new PendingDecision.RaiseFunds("it-a", null, List.of(new Charge(10_000, MoneyReason.LOAN_INTEREST))));
        var saved = gameRepository.save(game);

        var loaded = gameRepository.findById(id).orElseThrow();
        assertEquals(saved.getVersion(), loaded.getVersion());
        assertEquals(game.getState(), loaded.getState());
        assertEquals(LoanLimit.UNLIMITED, loaded.getState().getSettings().loanLimit());
        assertEquals(21, loaded.getState().getFinanceNewsDeck().size());
        assertEquals("it-a", loaded.getState().getBonds().getFirst().getOwner());
        assertInstanceOf(PendingDecision.RaiseFunds.class, loaded.getState().getPendingDecisions().getFirst());

        var log = gameLogRepository.findByGameIdAndSeqGreaterThanOrderBySeq(id, 0);
        assertEquals(loaded.getLastEventSeq(), log.size());
        assertEquals(List.of(1, 2, 3), log.stream().limit(3).map(GameLogEntry::seq).toList());
        assertEquals(new GameEvent.PlayerJoined("it-b", "Player b", 1), log.get(1).event());
        assertEquals(new GameEvent.SettingsChanged(new GameSettings(LoanLimit.UNLIMITED)), log.get(2).event());
        assertEquals(new GameEvent.DiceRolled("it-a", List.of(4)),
                log.stream().filter(entry -> entry.type().equals("DiceRolled")).findFirst().orElseThrow().event());
        assertEquals(List.of("FinanceNewsDrawn"), gameLogRepository.findByGameIdAndSeqGreaterThanOrderBySeq(id, log.size() - 1)
                .stream().map(GameLogEntry::type).toList());
    }

    @Test
    void testConcurrentSaveOfSameVersionFails() {
        var id = create("a").getId();
        var first = gameRepository.findById(id).orElseThrow();
        var second = gameRepository.findById(id).orElseThrow();

        gameRepository.save(first);

        assertThrows(OptimisticLockingFailureException.class, () -> gameRepository.save(second));
    }

    @Test
    void testListShowsLobbyGamesAndOwnGames() {
        var lobby = create("a").getId();
        var ownRunning = create("b").getId();
        var othersRunning = create("c").getId();
        for (var id : List.of(ownRunning, othersRunning)) {
            var game = gameRepository.findById(id).orElseThrow();
            game.setStatus(GameStatus.RUNNING);
            gameRepository.save(game);
        }

        var ids = gameRepository.findByStatusOrPlayer(GameStatus.LOBBY, "it-b", Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream().map(Game::getId).toList();

        assertTrue(ids.contains(lobby));
        assertTrue(ids.contains(ownRunning));
        assertFalse(ids.contains(othersRunning));
    }

    @Test
    void testLastPlayerLeavingDeletesGameAndLog() {
        var id = create("a").getId();

        gameService.leave(id, user("a"));

        assertTrue(gameRepository.findById(id).isEmpty());
        assertTrue(gameLogRepository.findByGameIdAndSeqGreaterThanOrderBySeq(id, 0).isEmpty());
    }

    @Test
    void testAuctionBidsSurvivePersistenceAndStayHiddenInJson() {
        var id = create("a").getId();
        var game = gameService.get(id);
        var offer = new PendingDecision.BondOffer("it-a", BondContinuation.GRAND_DRAW);
        var auction = new PendingDecision.BondAuction("it-b", List.of("it-a", "it-b"), 1,
                List.of(new PendingDecision.Bid("it-a", 1_000)));
        var assetAuction = new PendingDecision.AssetAuction("it-a", "P:3", 0, List.of("it-b"), 0,
                List.of(new PendingDecision.Bid("it-c", 500)));
        game.getState().getPendingDecisions().addAll(List.of(offer, auction, assetAuction));
        game.getState().getPlayers().getFirst().getHeldStockTips().add("PV-25");
        game.getState().getStockTipDeck().add("PV-01");
        gameRepository.save(game);

        var loaded = gameService.get(id);
        assertEquals(List.of(offer, auction, assetAuction), loaded.getState().getPendingDecisions());
        assertEquals(List.of("PV-25"), loaded.getState().getPlayers().getFirst().getHeldStockTips());
        assertEquals(List.of("PV-01"), loaded.getState().getStockTipDeck());
        var json = objectMapper.readTree(objectMapper.writeValueAsString(loaded));
        assertFalse(json.has("unarchivedEvents"));
        var decisions = json.get("state").get("pendingDecisions");
        assertEquals(3, decisions.size());
        assertEquals(2, decisions.get(0).get("after").asInt());
        assertFalse(decisions.get(1).has("bids"));
        assertFalse(decisions.get(2).has("bids"));
    }

    @Test
    void testLegacyIntegerBondContinuationsStillLoad() {
        var id = create("a").getId();
        var game = gameService.get(id);
        game.getState().getPendingDecisions().add(new PendingDecision.BondOffer("it-a", BondContinuation.SMALL_DRAW));
        gameRepository.save(game);
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(id)),
                new Update().set("state.pendingDecisions.0.after", 1), Game.class);

        assertEquals(new PendingDecision.BondOffer("it-a", BondContinuation.SMALL_DRAW),
                gameService.get(id).getState().getPendingDecisions().getFirst());
    }

    @Test
    void testDurableEventsRecoverFromPartialArchiveWithoutDuplicates() {
        var id = create("a").getId();
        var game = gameService.get(id);
        var second = GameLogEntry.of(id, 2, 1000, new GameEvent.SettingsChanged(GameSettings.DEFAULT));
        var third = GameLogEntry.of(id, 3, 1000, new GameEvent.SettingsChanged(new GameSettings(LoanLimit.UNLIMITED)));
        game.setLastEventSeq(3);
        game.setUnarchivedEvents(List.of(second, third));
        gameRepository.save(game);
        gameLogRepository.save(second); // Only part of the archive made it before an interruption.
        var stored = gameService.get(id);
        assertEquals(List.of(second, third), stored.getUnarchivedEvents());

        assertEquals(List.of(second, third), gameService.events(id, 1));
        assertEquals(List.of(second, third), gameService.events(id, 1));
        assertEquals(3, gameLogRepository.findByGameIdAndSeqGreaterThanOrderBySeq(id, 0).size());
        assertEquals(stored.getVersion(), gameService.get(id).getVersion());

        gameService.changeSettings(id, user("a"), GameSettings.DEFAULT);
        assertEquals(List.of(4), gameService.get(id).getUnarchivedEvents().stream().map(GameLogEntry::seq).toList());
        assertEquals(4, gameService.events(id, 0).size());
    }

    @Test
    void testChatRecordGetsGeneratedIdAndRoundTrips() {
        var message = new ChatMessage(null, "test@example.com", null, "Hello", 1000, null);
        var saved = chatRepository.save(message);
        assertNotNull(saved.id());
        try {
            assertEquals(message.withId(saved.id()), chatRepository.findById(saved.id()).orElseThrow());
        } finally {
            chatRepository.deleteById(saved.id());
        }
    }
}
