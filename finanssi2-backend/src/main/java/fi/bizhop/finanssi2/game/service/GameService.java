package fi.bizhop.finanssi2.game.service;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.db.Game;
import fi.bizhop.finanssi2.game.db.GameLogEntry;
import fi.bizhop.finanssi2.game.db.GameLogRepository;
import fi.bizhop.finanssi2.game.db.GameRepository;
import fi.bizhop.finanssi2.game.db.GameStatus;
import fi.bizhop.finanssi2.game.engine.GameCommand;
import fi.bizhop.finanssi2.game.engine.GameEngine;
import fi.bizhop.finanssi2.game.engine.GameEvent;
import fi.bizhop.finanssi2.game.engine.GameSetup;
import fi.bizhop.finanssi2.game.engine.PlayerState;
import fi.bizhop.finanssi2.game.engine.RuleViolation;
import fi.bizhop.finanssi2.security.User;
import fi.bizhop.finanssi2.service.MessagingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import static fi.bizhop.finanssi2.game.data.GameConstants.MAX_PLAYERS;

/**
 * Loads a game, applies a change, then saves the game and its new events and broadcasts them. A save that loses the optimistic
 * locking race throws {@link org.springframework.dao.OptimisticLockingFailureException} and nothing is stored or broadcast.
 */
@Service
@RequiredArgsConstructor
public class GameService {
    static final String LOBBY_TOPIC = "/topic/games";

    final GameRepository gameRepository;
    final GameLogRepository gameLogRepository;
    final MessagingService messagingService;
    final GameData gameData;
    final GameSetup gameSetup;
    final GameEngine gameEngine;
    final DiceSource diceSource;
    final RandomGenerator gameRandom;

    static String gameTopic(String gameId) {
        return LOBBY_TOPIC + "/" + gameId;
    }

    /** Creates a game in the lobby with its creator as the first player */
    public Game create(User user) {
        var game = new Game();
        game.setCreator(user.uid());
        game.setCreatedAt(System.currentTimeMillis());
        return commit(game, List.of(addPlayer(game, user)), true).game();
    }

    /** Games in the lobby and games the user is in, newest first */
    public List<Game> list(User user) {
        return gameRepository.findByStatusOrPlayer(GameStatus.LOBBY, user.uid(), Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public Game get(String id) {
        return gameRepository.findById(id).orElseThrow(() -> new GameNotFoundException(id));
    }

    public List<GameLogEntry> events(String id, int after) {
        get(id);
        return gameLogRepository.findByGameIdAndSeqGreaterThanOrderBySeq(id, after);
    }

    public Game join(String id, User user) {
        var game = get(id);
        requireLobby(game);
        if (game.getState().player(user.uid()).isPresent()) {
            throw new RuleViolation("Already in the game");
        }
        if (game.getState().getPlayers().size() >= MAX_PLAYERS) {
            throw new RuleViolation("The game is full");
        }
        return commit(game, List.of(addPlayer(game, user)), true).game();
    }

    /** Leaves a game in the lobby. The last player leaving deletes the game; if the creator leaves, the next player takes over. */
    public void leave(String id, User user) {
        var game = get(id);
        requireLobby(game);
        var player = game.getState().player(user.uid()).orElseThrow(() -> new RuleViolation("Not in the game"));
        var players = game.getState().getPlayers();
        if (players.size() == 1) {
            gameRepository.delete(game);
            gameLogRepository.deleteByGameId(id);
            messagingService.send(LOBBY_TOPIC, new LobbyChange(id, null));
            return;
        }
        players.remove(player);
        if (game.getCreator().equals(user.uid())) {
            game.setCreator(players.getFirst().getUid());
        }
        commit(game, List.of(new GameEvent.PlayerLeft(user.uid())), true);
    }

    public Game start(String id, User user) {
        var game = get(id);
        if (!game.getCreator().equals(user.uid())) {
            throw new NotAllowedException("Only the creator can start the game");
        }
        requireLobby(game);
        var events = gameSetup.start(game.getState(), diceSource.forGame(id), gameRandom);
        game.setStatus(GameStatus.RUNNING);
        return commit(game, events, true).game();
    }

    /** Runs a player's command and returns the events it caused */
    public List<GameLogEntry> command(String id, User user, GameCommand command) {
        var game = get(id);
        if (game.getStatus() != GameStatus.RUNNING) {
            throw new RuleViolation("The game is not running");
        }
        var events = gameEngine.handle(game.getState(), user.uid(), command, diceSource.forGame(id));
        return commit(game, events, false).entries();
    }

    /** Command types the user may send in the game right now */
    public List<String> allowedCommands(Game game, User user) {
        return game.getStatus() == GameStatus.RUNNING ? gameEngine.allowedCommands(game.getState(), user.uid()) : List.of();
    }

    static void requireLobby(Game game) {
        if (game.getStatus() != GameStatus.LOBBY) {
            throw new RuleViolation("The game has already started");
        }
    }

    static GameEvent addPlayer(Game game, User user) {
        var players = game.getState().getPlayers();
        var taken = players.stream().map(PlayerState::getPiece).toList();
        var piece = IntStream.range(0, MAX_PLAYERS).filter(p -> !taken.contains(p)).findFirst().orElseThrow();
        players.add(new PlayerState(user.uid(), user.name(), user.photoUrl(), piece));
        return new GameEvent.PlayerJoined(user.uid(), user.name(), piece);
    }

    record Committed(Game game, List<GameLogEntry> entries) {}

    /**
     * Saves the game and the events, numbered after the game's previous events, and broadcasts them; lobby changes also on the
     * lobby topic
     */
    Committed commit(Game game, List<GameEvent> events, boolean lobbyChange) {
        var firstSeq = game.getLastEventSeq() + 1;
        game.setLastEventSeq(game.getLastEventSeq() + events.size());
        var saved = gameRepository.save(game);

        var time = System.currentTimeMillis();
        var entries = new ArrayList<GameLogEntry>();
        for (int i = 0; i < events.size(); i++) {
            entries.add(GameLogEntry.of(saved.getId(), firstSeq + i, time, events.get(i)));
        }
        var savedEntries = gameLogRepository.saveAll(entries);

        messagingService.send(gameTopic(saved.getId()), new GameUpdate(saved.getId(), saved.getVersion(), savedEntries));
        if (lobbyChange) {
            messagingService.send(LOBBY_TOPIC, new LobbyChange(saved.getId(), saved));
        }
        return new Committed(saved, savedEntries);
    }
}
