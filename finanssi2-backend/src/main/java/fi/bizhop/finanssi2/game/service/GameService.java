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
import fi.bizhop.finanssi2.game.engine.GameSettings;
import fi.bizhop.finanssi2.game.engine.GameSetup;
import fi.bizhop.finanssi2.game.engine.PlayerState;
import fi.bizhop.finanssi2.game.engine.RuleViolation;
import fi.bizhop.finanssi2.security.User;
import fi.bizhop.finanssi2.service.MessagingService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static fi.bizhop.finanssi2.game.data.GameConstants.MAX_PLAYERS;

/**
 * Saves state and new events together in the game document, then archives events and broadcasts the change. Optimistic locking
 * rejects competing changes before their new events are archived or broadcast. A failed archive is retried on event reads and
 * later changes; the events remain available from the game document.
 */
@Service
@RequiredArgsConstructor
public class GameService {
    static final String LOBBY_TOPIC = "/topic/games";
    static final Logger logger = Logger.getLogger(GameService.class.getName());

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
        game.setId(new ObjectId().toHexString());
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
        var game = get(id);
        archive(game.getUnarchivedEvents());
        var archived = gameLogRepository.findByGameIdAndSeqGreaterThanOrderBySeq(id, after);
        var entries = Stream.concat(archived.stream(), game.getUnarchivedEvents().stream().filter(entry -> entry.seq() > after))
                .collect(Collectors.toMap(GameLogEntry::seq, Function.identity(), (first, second) -> second, TreeMap::new));
        return List.copyOf(entries.values());
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

    /** Sets the house rules of a game in the lobby */
    public Game changeSettings(String id, User user, GameSettings settings) {
        var game = get(id);
        requireCreator(game, user, "Only the creator can change the settings");
        requireLobby(game);
        game.getState().setSettings(settings);
        return commit(game, List.of(new GameEvent.SettingsChanged(settings)), true).game();
    }

    public Game start(String id, User user) {
        var game = get(id);
        requireCreator(game, user, "Only the creator can start the game");
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
        return switch (game.getStatus()) {
            case RUNNING -> gameEngine.allowedCommands(game.getState(), user.uid());
            case LOBBY, FINISHED -> List.of();
        };
    }

    static void requireCreator(Game game, User user, String reason) {
        if (!game.getCreator().equals(user.uid())) {
            throw new NotAllowedException(reason);
        }
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
     * Events awaiting archival travel with the next state save. Successful archival of an earlier batch lets that save discard
     * it; a failed state save leaves the previously stored batch intact. No second game save or version increment is needed.
     */
    Committed commit(Game game, List<GameEvent> events, boolean lobbyChange) {
        var firstSeq = game.getLastEventSeq() + 1;
        var time = System.currentTimeMillis();
        var entries = IntStream.range(0, events.size())
                .mapToObj(i -> GameLogEntry.of(game.getId(), firstSeq + i, time, events.get(i)))
                .toList();
        var earlier = game.getUnarchivedEvents();
        if (archive(earlier)) {
            earlier = List.of();
        }
        game.setUnarchivedEvents(Stream.concat(earlier.stream(), entries.stream()).toList());
        game.setLastEventSeq(game.getLastEventSeq() + events.size());
        var saved = gameRepository.save(game);
        archive(saved.getUnarchivedEvents());

        messagingService.send(gameTopic(saved.getId()), new GameUpdate(saved.getId(), saved.getVersion(), entries));
        if (lobbyChange) {
            messagingService.send(LOBBY_TOPIC, new LobbyChange(saved.getId(), saved));
        }
        return new Committed(saved, entries);
    }

    /** Idempotent: stable event ids make retries safe after either a complete or a partial log write. */
    boolean archive(List<GameLogEntry> entries) {
        if (entries.isEmpty()) {
            return true;
        }
        try {
            gameLogRepository.saveAll(entries);
            return true;
        } catch (DataAccessException e) {
            logger.log(Level.WARNING, "Events remain in game " + entries.getFirst().gameId() + " for archival retry", e);
            return false;
        }
    }
}
