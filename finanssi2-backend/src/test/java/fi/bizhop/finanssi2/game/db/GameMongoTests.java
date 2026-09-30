package fi.bizhop.finanssi2.game.db;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Saves and loads games in MongoDB; subclasses choose the database. Removes only the documents it created. */
@SpringBootTest
@ActiveProfiles("test")
abstract class GameMongoTests {
    @Autowired
    GameService gameService;
    @Autowired
    GameRepository gameRepository;
    @Autowired
    GameLogRepository gameLogRepository;
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
    void testGameAndEventsRoundTrip() {
        var id = create("a").getId();
        gameService.join(id, user("b"));
        gameService.changeSettings(id, user("a"), new GameSettings(LoanLimit.UNLIMITED));
        when(diceSource.forGame(any())).thenReturn(new ScriptedDice(6, 6, 1, 1, 4));
        gameService.start(id, user("a"));
        // Two loans, then from square 1 to 5; a Finance News square does nothing yet
        gameService.command(id, user("a"), new GameCommand.TakeLoan());
        gameService.command(id, user("a"), new GameCommand.TakeLoan());
        gameService.command(id, user("a"), new GameCommand.Roll());

        // A pending decision is stored with its type
        var game = gameService.get(id);
        game.getState().getPendingDecisions().add(
                new PendingDecision.RaiseFunds("it-a", null, List.of(new Charge(10_000, MoneyReason.LOAN_INTEREST))));
        var saved = gameRepository.save(game);

        var loaded = gameRepository.findById(id).orElseThrow();
        assertEquals(saved.getVersion(), loaded.getVersion());
        assertEquals(game.getState(), loaded.getState());
        assertEquals(LoanLimit.UNLIMITED, loaded.getState().getSettings().loanLimit());
        assertEquals(21, loaded.getState().getFinanceNewsDeck().size());
        assertInstanceOf(PendingDecision.RaiseFunds.class, loaded.getState().getPendingDecisions().getFirst());

        var log = gameLogRepository.findByGameIdAndSeqGreaterThanOrderBySeq(id, 0);
        assertEquals(loaded.getLastEventSeq(), log.size());
        assertEquals(List.of(1, 2, 3), log.stream().limit(3).map(GameLogEntry::seq).toList());
        assertEquals(new GameEvent.PlayerJoined("it-b", "Player b", 1), log.get(1).event());
        assertEquals(new GameEvent.SettingsChanged(new GameSettings(LoanLimit.UNLIMITED)), log.get(2).event());
        assertEquals(new GameEvent.DiceRolled("it-a", List.of(4)),
                log.stream().filter(entry -> entry.type().equals("DiceRolled")).findFirst().orElseThrow().event());
        assertEquals(List.of("NotImplemented"), gameLogRepository.findByGameIdAndSeqGreaterThanOrderBySeq(id, log.size() - 1)
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
}
