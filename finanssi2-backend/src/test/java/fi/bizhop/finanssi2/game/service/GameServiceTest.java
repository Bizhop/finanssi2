package fi.bizhop.finanssi2.game.service;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.data.GameDataConfig;
import fi.bizhop.finanssi2.game.db.Game;
import fi.bizhop.finanssi2.game.db.GameLogEntry;
import fi.bizhop.finanssi2.game.db.GameLogRepository;
import fi.bizhop.finanssi2.game.db.GameRepository;
import fi.bizhop.finanssi2.game.db.GameStatus;
import fi.bizhop.finanssi2.game.engine.GameCommand;
import fi.bizhop.finanssi2.game.engine.GameEngine;
import fi.bizhop.finanssi2.game.engine.GameEvent;
import fi.bizhop.finanssi2.game.engine.GameSettings;
import fi.bizhop.finanssi2.game.engine.GameSetup;
import fi.bizhop.finanssi2.game.engine.LoanLimit;
import fi.bizhop.finanssi2.game.engine.NotYourTurn;
import fi.bizhop.finanssi2.game.engine.Rules;
import fi.bizhop.finanssi2.game.engine.PlayerState;
import fi.bizhop.finanssi2.game.engine.RuleViolation;
import fi.bizhop.finanssi2.game.engine.ScriptedDice;
import fi.bizhop.finanssi2.security.User;
import fi.bizhop.finanssi2.service.MessagingService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataAccessResourceFailureException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {
    static final String GAME_ID = "66f9a1b2c3d4e5f607182931";

    static GameData gameData;

    @Mock
    GameRepository gameRepository;
    @Mock
    GameLogRepository gameLogRepository;
    @Mock
    MessagingService messagingService;

    @BeforeAll
    static void load() throws IOException {
        gameData = new GameDataConfig().gameData();
    }

    static User user(String uid) {
        return new User(uid, uid + "@example.com", "Player " + uid, "https://example.com/" + uid + ".png");
    }

    GameService service(Integer... dice) {
        var rules = new Rules(gameData);
        var scripted = new ScriptedDice(dice);
        return new GameService(gameRepository, gameLogRepository, messagingService, gameData, new GameSetup(gameData),
                new GameEngine(gameData, rules), gameId -> scripted, new Random(1), new DebugAccess(""));
    }

    /** A saved game in the lobby with the given players, the first one the creator */
    Game lobby(String... uids) {
        var game = new Game();
        game.setId(GAME_ID);
        game.setVersion(3L);
        game.setCreator(uids[0]);
        for (int i = 0; i < uids.length; i++) {
            game.getState().getPlayers().add(new PlayerState(uids[i], "Player " + uids[i], null, i));
        }
        game.setLastEventSeq(uids.length);
        lenient().when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        return game;
    }

    void saveSucceeds() {
        when(gameRepository.save(any())).thenAnswer(invocation -> {
            var game = invocation.<Game>getArgument(0);
            if (game.getId() == null) {
                game.setId(GAME_ID);
            }
            game.setVersion(game.getVersion() == null ? 0 : game.getVersion() + 1);
            return game;
        });
        when(gameLogRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @SuppressWarnings("unchecked")
    List<GameLogEntry> savedEntries() {
        var captor = ArgumentCaptor.forClass(List.class);
        verify(gameLogRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void failedArchivalOfDeletedGameCleansPartialHistory() {
        var entry = GameLogEntry.of(GAME_ID, 1, 1000, new GameEvent.PlayerJoined("a", "Player a", 0));
        when(gameLogRepository.saveAll(any())).thenThrow(new DataAccessResourceFailureException("partial write"));
        when(gameRepository.existsById(GAME_ID)).thenReturn(false);
        assertEquals(false, service().archive(List.of(entry), true));
        verify(gameLogRepository).deleteByGameId(GAME_ID);
    }

    @Test
    void runningNormalGamesSkipArchivalCleanupCheck() {
        var game = lobby("a", "b");
        saveSucceeds();
        var service = service(6, 6, 1, 1, 3, 4);
        service.start(GAME_ID, user("a"));
        clearInvocations(gameRepository);

        service.command(GAME_ID, user("a"), new GameCommand.Roll());
        service.events(GAME_ID, 0);

        assertEquals(GameStatus.RUNNING, game.getStatus());
        verify(gameRepository, never()).existsById(any());
    }

    @Test
    void testCreateJoinsCreatorAndBroadcasts() {
        saveSucceeds();

        var game = service().create(user("a"));
        var id = game.getId();

        assertEquals(GameStatus.LOBBY, game.getStatus());
        assertEquals("a", game.getCreator());
        var player = game.getState().getPlayers().getFirst();
        assertEquals(new PlayerState("a", "Player a", "https://example.com/a.png", 0), player);
        var entries = savedEntries();
        assertEquals(1, entries.size());
        assertEquals(GameLogEntry.of(id, 1, entries.getFirst().time(), new GameEvent.PlayerJoined("a", "Player a", 0)),
                entries.getFirst());
        verify(messagingService).send("/topic/games/" + id, new GameUpdate(id, 0, entries));
        verify(messagingService).send("/topic/games", new LobbyChange(id, game));
    }

    @Test
    void testJoinTakesFirstFreePiece() {
        var game = lobby("a", "b", "c");
        game.getState().getPlayers().remove(1);
        saveSucceeds();

        service().join(GAME_ID, user("d"));

        assertEquals(1, game.getState().player("d").orElseThrow().getPiece());
        var entry = savedEntries().getFirst();
        assertEquals(4, entry.seq());
        assertEquals(new GameEvent.PlayerJoined("d", "Player d", 1), entry.event());
    }

    @Test
    void testJoinTwice() {
        lobby("a", "b");
        assertThrows(RuleViolation.class, () -> service().join(GAME_ID, user("b")));
        verify(gameRepository, never()).save(any());
    }

    @Test
    void testJoinFullGame() {
        lobby("a", "b", "c", "d", "e", "f");
        assertThrows(RuleViolation.class, () -> service().join(GAME_ID, user("g")));
        verify(gameRepository, never()).save(any());
    }

    @Test
    void testJoinRunningGame() {
        lobby("a", "b").setStatus(GameStatus.RUNNING);
        assertThrows(RuleViolation.class, () -> service().join(GAME_ID, user("c")));
        verify(gameRepository, never()).save(any());
    }

    @Test
    void testJoinMissingGame() {
        when(gameRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(GameNotFoundException.class, () -> service().join("missing", user("a")));
    }

    @Test
    void testCreatorLeavingPassesGameOn() {
        var game = lobby("a", "b", "c");
        saveSucceeds();

        service().leave(GAME_ID, user("a"));

        assertEquals("b", game.getCreator());
        assertEquals(List.of("b", "c"), game.getState().getPlayers().stream().map(PlayerState::getUid).toList());
        assertEquals(new GameEvent.PlayerLeft("a"), savedEntries().getFirst().event());
    }

    @Test
    void testLastPlayerLeavingDeletesGame() {
        var game = lobby("a");

        service().leave(GAME_ID, user("a"));

        verify(gameRepository).delete(game);
        verify(gameLogRepository).deleteByGameId(GAME_ID);
        verify(messagingService).send("/topic/games", new LobbyChange(GAME_ID, null));
        verify(gameRepository, never()).save(any());
    }

    @Test
    void testLeaveWhenNotInGame() {
        lobby("a", "b");
        assertThrows(RuleViolation.class, () -> service().leave(GAME_ID, user("c")));
    }

    @Test
    void testStart() {
        var game = lobby("a", "b");
        saveSucceeds();

        service(2, 3, 4, 4).start(GAME_ID, user("a"));

        assertEquals(GameStatus.RUNNING, game.getStatus());
        assertEquals(List.of("b", "a"), game.getState().getTurnOrder());
        var entries = savedEntries();
        assertEquals(List.of(3, 4, 5, 6), entries.stream().map(GameLogEntry::seq).toList());
        assertEquals(List.of("StartingRoll", "StartingRoll", "GameStarted", "TurnStarted"),
                entries.stream().map(GameLogEntry::type).toList());
        verify(messagingService).send("/topic/games/" + GAME_ID, new GameUpdate(GAME_ID, 4, entries));
        verify(messagingService).send("/topic/games", new LobbyChange(GAME_ID, game));
    }

    @Test
    void testChangeSettings() {
        var game = lobby("a", "b");
        saveSucceeds();

        service().changeSettings(GAME_ID, user("a"), new GameSettings(LoanLimit.UNLIMITED));

        assertEquals(LoanLimit.UNLIMITED, game.getState().getSettings().loanLimit());
        assertEquals(new GameEvent.SettingsChanged(new GameSettings(LoanLimit.UNLIMITED)), savedEntries().getFirst().event());
        verify(messagingService).send("/topic/games", new LobbyChange(GAME_ID, game));
    }

    @Test
    void testChangeSettingsRejected() {
        lobby("a", "b");
        assertThrows(NotAllowedException.class,
                () -> service().changeSettings(GAME_ID, user("b"), new GameSettings(LoanLimit.UNLIMITED)));
        running();
        assertThrows(RuleViolation.class,
                () -> service().changeSettings(GAME_ID, user("a"), new GameSettings(LoanLimit.UNLIMITED)));
        verify(gameRepository, never()).save(any());
    }

    @Test
    void testStartWithOnePlayer() {
        var game = lobby("a");
        assertThrows(RuleViolation.class, () -> service().start(GAME_ID, user("a")));
        assertEquals(GameStatus.LOBBY, game.getStatus());
        verify(gameRepository, never()).save(any());
    }

    @Test
    void testNonCreatorStarting() {
        lobby("a", "b");
        assertThrows(NotAllowedException.class, () -> service().start(GAME_ID, user("b")));
        verify(gameRepository, never()).save(any());
    }

    @Test
    void testStartTwice() {
        lobby("a", "b").setStatus(GameStatus.RUNNING);
        assertThrows(RuleViolation.class, () -> service().start(GAME_ID, user("a")));
    }

    @Test
    void testConcurrentChangeStoresAndBroadcastsNothing() {
        lobby("a");
        when(gameRepository.save(any())).thenThrow(new OptimisticLockingFailureException("version 3 changed"));

        assertThrows(OptimisticLockingFailureException.class, () -> service().join(GAME_ID, user("b")));

        verifyNoInteractions(gameLogRepository, messagingService);
    }

    /** A running game of a and b, a to roll */
    Game running() {
        var game = lobby("a", "b");
        game.setStatus(GameStatus.RUNNING);
        new GameSetup(gameData).start(game.getState(), new ScriptedDice(6, 6, 1, 1), new Random(1));
        return game;
    }

    @Test
    void testCommandSavesAndBroadcastsOnGameTopicOnly() {
        var game = running();
        saveSucceeds();

        var entries = service(4).command(GAME_ID, user("a"), new GameCommand.Roll());

        assertEquals(5, game.getState().current().getPosition());
        assertEquals(List.of("DiceRolled", "PieceMoved", "LandedOn", "FinanceNewsDrawn"),
                entries.stream().map(GameLogEntry::type).toList());
        assertEquals(entries, savedEntries());
        verify(messagingService).send("/topic/games/" + GAME_ID, new GameUpdate(GAME_ID, 4, entries));
        verify(messagingService, never()).send(eq("/topic/games"), any());
    }

    @Test
    void testCommandInLobby() {
        lobby("a", "b");
        assertThrows(RuleViolation.class, () -> service().command(GAME_ID, user("a"), new GameCommand.Roll()));
        verify(gameRepository, never()).save(any());
    }

    @Test
    void testCommandOutOfTurn() {
        running();
        assertThrows(NotYourTurn.class, () -> service().command(GAME_ID, user("b"), new GameCommand.Roll()));
        verify(gameRepository, never()).save(any());
    }

    @Test
    void creatorCanEndGameWithoutWinnerAndServiceMarksItFinished() {
        var game = running();
        saveSucceeds();

        var entries = service().command(GAME_ID, user("a"), new GameCommand.EndGame());

        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertTrue(game.getState().isFinished());
        assertNull(game.getState().getWinner());
        assertEquals("GameEnded", entries.getFirst().type());
        assertEquals(List.of(), service().allowedCommands(game, user("a")));
        assertThrows(RuleViolation.class, () -> service().command(GAME_ID, user("a"), new GameCommand.Roll()));
    }

    @Test
    void onlyCreatorCanEndGame() {
        running();
        assertThrows(NotAllowedException.class, () -> service().command(GAME_ID, user("b"), new GameCommand.EndGame()));
        verify(gameRepository, never()).save(any());
    }

    @Test
    void engineWinMarksPersistedGameFinished() {
        var game = running();
        game.getState().getPlayers().stream().filter(player -> player.getUid().equals("a")).findFirst().orElseThrow().setCash(1_000_000);
        new GameSetup(gameData).initAssets(game.getState(), gameData);
        for (var group : List.of("KASITEOLLISUUS", "PALVELUYHTIO")) {
            gameData.group(group).properties().forEach(square -> game.getState().property(square).setOwner("a"));
            gameData.sharesOf(group).forEach(share -> game.getState().share(share.id()).setOwner("a"));
        }
        game.getState().setPhase(fi.bizhop.finanssi2.game.engine.TurnPhase.AFTER_ROLL);
        saveSucceeds();

        var entries = service().command(GAME_ID, user("a"), new GameCommand.EndTurn());

        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertTrue(game.getState().isFinished());
        assertEquals("a", game.getState().getWinner());
        assertEquals("GameEnded", entries.getLast().type());
    }

    @Test
    void testAllowedCommands() {
        var game = running();
        assertEquals(List.of("BuyCar", "EndGame", "Resign", "Roll", "TakeLoan"), service().allowedCommands(game, user("a")));
        assertEquals(List.of("Resign"), service().allowedCommands(game, user("b")));
        assertEquals(List.of(), service().allowedCommands(lobby("a", "b"), user("a")));
    }

    @Test
    void testEventsOfMissingGame() {
        when(gameRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(GameNotFoundException.class, () -> service().events("missing", 0));
        verify(gameLogRepository, never()).findByGameIdAndSeqGreaterThanOrderBySeq(eq("missing"), any(Integer.class));
    }

    @Test
    void testArchiveFailureKeepsEventsWithSavedStateAndAcceptsChange() {
        var game = lobby("a");
        when(gameRepository.save(any())).thenAnswer(invocation -> {
            var document = invocation.<Game>getArgument(0);
            assertEquals(2, document.getLastEventSeq());
            assertEquals(2, document.getState().getPlayers().size());
            assertEquals(List.of(new GameEvent.PlayerJoined("b", "Player b", 1)),
                    document.getUnarchivedEvents().stream().map(GameLogEntry::event).toList());
            document.setVersion(4L);
            return document;
        });
        when(gameLogRepository.saveAll(anyList())).thenThrow(new DataAccessResourceFailureException("log unavailable"));

        assertEquals(game, service().join(GAME_ID, user("b")));

        assertEquals(1, game.getUnarchivedEvents().size());
        verify(messagingService).send("/topic/games/" + GAME_ID, new GameUpdate(GAME_ID, 4, game.getUnarchivedEvents()));
    }

    @Test
    void testEventReadsMergeDurableEventsAfterFailedArchiveWithoutDuplicates() {
        var game = lobby("a", "b");
        var first = GameLogEntry.of(GAME_ID, 1, 1000, new GameEvent.PlayerJoined("a", "Player a", 0));
        var second = GameLogEntry.of(GAME_ID, 2, 1000, new GameEvent.PlayerJoined("b", "Player b", 1));
        var third = GameLogEntry.of(GAME_ID, 3, 2000, new GameEvent.SettingsChanged(game.getState().getSettings()));
        game.setUnarchivedEvents(List.of(second, third));
        when(gameLogRepository.saveAll(anyList())).thenThrow(new DataAccessResourceFailureException("partial log write"));
        when(gameLogRepository.findByGameIdAndSeqGreaterThanOrderBySeq(GAME_ID, 0)).thenReturn(List.of(first, second));
        when(gameLogRepository.findByGameIdAndSeqGreaterThanOrderBySeq(GAME_ID, 2)).thenReturn(List.of());

        assertEquals(List.of(first, second, third), service().events(GAME_ID, 0));
        assertEquals(List.of(third), service().events(GAME_ID, 2));
        assertEquals(3L, game.getVersion());
    }

    @Test
    void testLaterChangeRetainsOlderEventsWhenArchivalStillFails() {
        var game = lobby("a");
        when(gameRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameLogRepository.saveAll(anyList())).thenThrow(new DataAccessResourceFailureException("log unavailable"));
        var service = service();
        service.join(GAME_ID, user("b"));
        var earlier = game.getUnarchivedEvents().getFirst();

        service.join(GAME_ID, user("c"));

        assertEquals(List.of(2, 3), game.getUnarchivedEvents().stream().map(GameLogEntry::seq).toList());
        assertEquals(earlier, game.getUnarchivedEvents().getFirst());
    }

    @Test
    void testLaterChangeDiscardsOnlySuccessfullyArchivedEvents() {
        var game = lobby("a");
        when(gameRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameLogRepository.saveAll(anyList())).thenThrow(new DataAccessResourceFailureException("log unavailable"));
        var service = service();
        service.join(GAME_ID, user("b"));
        var earlier = game.getUnarchivedEvents().getFirst();
        var batches = new ArrayList<List<GameLogEntry>>();
        doAnswer(invocation -> {
            var batch = List.copyOf(invocation.<List<GameLogEntry>>getArgument(0));
            batches.add(batch);
            return batch;
        }).when(gameLogRepository).saveAll(anyList());

        service.join(GAME_ID, user("c"));

        assertEquals(List.of(earlier), batches.getFirst());
        assertEquals(List.of(3), game.getUnarchivedEvents().stream().map(GameLogEntry::seq).toList());
        assertEquals(game.getUnarchivedEvents(), batches.getLast());
    }

    @Test
    void testPartialArchiveIsRetriedWithStableIds() {
        var game = lobby("a", "b");
        when(gameRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var log = new LinkedHashMap<String, GameLogEntry>();
        when(gameLogRepository.saveAll(anyList())).thenAnswer(invocation -> {
            var batch = invocation.<List<GameLogEntry>>getArgument(0);
            log.put(batch.getFirst().id(), batch.getFirst());
            throw new DataAccessResourceFailureException("failed after first event");
        });
        var service = service(6, 6, 1, 1);
        service.start(GAME_ID, user("a"));
        assertEquals(1, log.size());
        assertEquals(4, game.getUnarchivedEvents().size());
        doAnswer(invocation -> {
            var batch = invocation.<List<GameLogEntry>>getArgument(0);
            batch.forEach(entry -> log.put(entry.id(), entry));
            return batch;
        }).when(gameLogRepository).saveAll(anyList());
        when(gameLogRepository.findByGameIdAndSeqGreaterThanOrderBySeq(GAME_ID, 2))
                .thenAnswer(invocation -> List.copyOf(log.values()));

        assertEquals(game.getUnarchivedEvents(), service.events(GAME_ID, 2));
        assertEquals(game.getUnarchivedEvents(), service.events(GAME_ID, 2));
        assertEquals(4, log.size());
        assertTrue(log.values().stream().allMatch(entry -> entry.id().equals(GAME_ID + ":" + entry.seq())));
    }
}
