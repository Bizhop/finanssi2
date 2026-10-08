package fi.bizhop.finanssi2.game.service;

import fi.bizhop.finanssi2.game.data.Card;
import fi.bizhop.finanssi2.game.data.Deck;
import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.db.Game;
import fi.bizhop.finanssi2.game.db.GameLogEntry;
import fi.bizhop.finanssi2.game.db.GameLogRepository;
import fi.bizhop.finanssi2.game.db.GameMode;
import fi.bizhop.finanssi2.game.db.GameRepository;
import fi.bizhop.finanssi2.game.db.GameStatus;
import fi.bizhop.finanssi2.game.engine.Dice;
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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static fi.bizhop.finanssi2.game.data.GameConstants.MAX_PLAYERS;

/**
 * Saves state and new events in one PostgreSQL transaction, then broadcasts the committed change. Optimistic locking rejects
 * competing changes before they can be broadcast. Versioned saves and removal reject changes to a deleted game.
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
    final DebugAccess debugAccess;

    static String gameTopic(String gameId) {
        return LOBBY_TOPIC + "/" + gameId;
    }

    /** Creates a game in the lobby with its creator as the first player */
    public Game create(User user) {
        var game = new Game();
        game.setId(java.util.UUID.randomUUID().toString());
        game.setCreator(user.userId());
        game.setCreatedAt(System.currentTimeMillis());
        return commit(game, List.of(addPlayer(game, user)), true).game();
    }

    public Game createDebug(User user, int playerCount, GameSettings settings) {
        debugAccess.require(user);
        if (playerCount < 2 || playerCount > MAX_PLAYERS) throw new RuleViolation("playerCount must be 2–6");
        if (settings.loanLimit() == null) throw new RuleViolation("loanLimit is required");
        var game = new Game(GameMode.DEBUG);
        game.setId(java.util.UUID.randomUUID().toString());
        game.setCreator(user.userId());
        game.setCreatedAt(System.currentTimeMillis());
        game.getState().setSettings(settings);
        var events = new ArrayList<GameEvent>();
        events.add(addPlayer(game, user));
        for (int seat = 2; seat <= playerCount; seat++) {
            events.add(addPlayer(game, new User("debug:" + game.getId() + ":seat:" + seat,
                    null, "Debug player " + seat, null)));
        }
        events.add(new GameEvent.SettingsChanged(settings));
        return commit(game, events, true).game();
    }

    public void deleteDebug(String id, User user) {
        var game = get(id);
        debugAccess.requireOwner(game, user);
        // Versioned removal makes a concurrent save fail rather than recreate the document.
        gameRepository.delete(game);
        messagingService.send(gameTopic(id), new GameDeleted(id, true));
    }

    public record GameDeleted(String gameId, boolean deleted) {}

    public Game get(String id, User user) {
        var game = get(id);
        debugAccess.requireRead(game, user);
        return game;
    }

    public List<GameLogEntry> events(String id, int after, User user) {
        return events(get(id, user), after);
    }

    static void requireNormal(Game game) {
        if (game.getMode() != GameMode.NORMAL) {
            throw new NotAllowedException("Use debug controls for this game");
        }
    }

    /** Games in the lobby and games the user is in, newest first */
    public List<Game> list(User user) {
        return gameRepository.findByStatusOrPlayer(GameStatus.LOBBY, user.userId(), Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream().filter(game -> game.getMode() == GameMode.NORMAL
                        || (game.getCreator().equals(user.userId()) && debugAccess.allowed(user))).toList();
    }

    public Game get(String id) {
        return gameRepository.findById(id).orElseThrow(() -> new GameNotFoundException(id));
    }

    public List<GameLogEntry> events(String id, int after) {
        return events(get(id), after);
    }

    private List<GameLogEntry> events(Game game, int after) {
        return gameLogRepository.findByGameIdAndSeqGreaterThanOrderBySeq(game.getId(), after);
    }

    public Game join(String id, User user) {
        var game = get(id);
        requireNormal(game);
        requireLobby(game);
        if (game.getState().player(user.userId()).isPresent()) {
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
        requireNormal(game);
        requireLobby(game);
        var player = game.getState().player(user.userId()).orElseThrow(() -> new RuleViolation("Not in the game"));
        var players = game.getState().getPlayers();
        if (players.size() == 1) {
            gameRepository.delete(game);
            messagingService.send(LOBBY_TOPIC, new LobbyChange(id, null));
            return;
        }
        players.remove(player);
        if (game.getCreator().equals(user.userId())) {
            game.setCreator(players.getFirst().getPlayerId());
        }
        commit(game, List.of(new GameEvent.PlayerLeft(user.userId())), true);
    }

    /** Sets the house rules of a game in the lobby */
    public Game changeSettings(String id, User user, GameSettings settings) {
        var game = get(id, user);
        requireCreator(game, user, "Only the creator can change the settings");
        requireLobby(game);
        game.getState().setSettings(settings);
        return commit(game, List.of(new GameEvent.SettingsChanged(settings)), true).game();
    }

    public Game start(String id, User user) {
        var game = get(id, user);
        requireCreator(game, user, "Only the creator can start the game");
        requireLobby(game);
        var events = gameSetup.start(game.getState(), diceSource.forGame(id), gameRandom);
        game.setStatus(GameStatus.RUNNING);
        return commit(game, events, true).game();
    }

    /** Runs a player's command and returns the events it caused */
    public List<GameLogEntry> command(String id, User user, GameCommand command) {
        var game = get(id);
        requireNormal(game);
        return execute(game, user, user.userId(), command, diceSource.forGame(id));
    }

    public List<GameLogEntry> debugCommand(String id, User user, String actor, Long expectedVersion,
                                                        GameCommand command, List<Integer> dice) {
        var game = get(id);
        debugAccess.requireOwner(game, user);
        requireVersion(game, expectedVersion);
        if (actor == null || !actor.equals(game.getState().actor())) {
            throw new RuleViolation("The acting player changed; reload and submit a fresh action");
        }
        if (command == null) throw new RuleViolation("command is required");
        var values = dice == null ? List.<Integer>of() : dice;
        if (values.size() > 32 || values.stream().anyMatch(value -> value == null || value < 1 || value > 6)) {
            throw new RuleViolation("dice must contain at most 32 values from 1 to 6");
        }
        var rolls = new ArrayDeque<>(values);
        var fallback = diceSource.forGame(id);
        return execute(game, user, actor, command, () -> rolls.isEmpty() ? fallback.roll() : rolls.removeFirst());
    }

    public Game nextCard(String id, User user, String deckName, String card, Long expectedVersion) {
        var game = get(id);
        debugAccess.requireOwner(game, user);
        requireVersion(game, expectedVersion);
        if (game.getStatus() != GameStatus.RUNNING || !game.getState().getPendingDecisions().isEmpty()) {
            throw new RuleViolation("Cards can only be selected in a running game without pending decisions");
        }
        var deck = switch (deckName == null ? "" : deckName) {
            case "FINANCE_NEWS" -> Deck.FINANCE_NEWS;
            case "STOCK_TIP" -> Deck.STOCK_TIP;
            default -> throw new RuleViolation("Unknown deck; use FINANCE_NEWS or STOCK_TIP");
        };
        var known = gameData.cards(deck).stream().map(Card::id).toList();
        if (!known.contains(card)) throw new RuleViolation("Unknown card for this deck");
        var state = game.getState();
        var drawDeck = switch (deck) {
            case FINANCE_NEWS -> state.getFinanceNewsDeck();
            case STOCK_TIP -> state.getStockTipDeck();
        };
        if (!drawDeck.contains(card)) throw new RuleViolation("The selected Stock Tip is held by a player");
        var held = deck == Deck.STOCK_TIP
                ? state.getPlayers().stream().flatMap(player -> player.getHeldStockTips().stream()).toList()
                : List.<String>of();
        var all = Stream.concat(drawDeck.stream(), held.stream()).toList();
        if (all.size() != known.size() || !new HashSet<>(all).equals(new HashSet<>(known))) {
            throw new RuleViolation("The deck has inconsistent card membership");
        }
        var reordered = new ArrayList<>(drawDeck);
        reordered.remove(card);
        reordered.addFirst(card);
        switch (deck) {
            case FINANCE_NEWS -> state.setFinanceNewsDeck(reordered);
            case STOCK_TIP -> state.setStockTipDeck(reordered);
        }
        return commit(game, List.of(new GameEvent.DebugDeckChanged(user.userId(), deckName, card)), false).game();
    }

    static void requireVersion(Game game, Long expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals(game.getVersion())) {
            throw new RuleViolation("The game changed; reload and submit a fresh action");
        }
    }

    private List<GameLogEntry> execute(Game game, User user, String actor, GameCommand command,
                                      Dice dice) {
        if (game.getStatus() != GameStatus.RUNNING) throw new RuleViolation("The game is not running");
        List<GameEvent> events;
        if (command instanceof GameCommand.EndGame) {
            requireCreator(game, user, "Only the creator can end the game");
            events = gameEngine.endWithoutWinner(game.getState());
            game.setStatus(GameStatus.FINISHED);
        } else {
            events = gameEngine.handle(game.getState(), actor, command, dice);
            if (game.getState().isFinished()) game.setStatus(GameStatus.FINISHED);
        }
        return commit(game, events, false).entries();
    }

    /** The player the user acts as: their own seat, or in a debug game whichever seat must act next */
    public String actingPlayer(Game game, User user) {
        return switch (game.getMode()) {
            case NORMAL -> user.userId();
            case DEBUG -> game.getState().actor();
        };
    }

    /** Command types the user may send right now, in a game loaded with {@link #get(String, User)} */
    public List<String> allowedCommands(Game game, User user) {
        return switch (game.getStatus()) {
            case RUNNING -> {
                var allowed = new ArrayList<>(gameEngine.allowedCommands(game.getState(), actingPlayer(game, user)));
                if (game.getCreator().equals(user.userId())) allowed.add(GameCommand.EndGame.class.getSimpleName());
                allowed.sort(String::compareTo);
                yield List.copyOf(allowed);
            }
            case LOBBY, FINISHED -> List.of();
        };
    }

    static void requireCreator(Game game, User user, String reason) {
        if (!game.getCreator().equals(user.userId())) {
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
        players.add(new PlayerState(user.userId(), user.name(), user.photoUrl(), piece));
        return new GameEvent.PlayerJoined(user.userId(), user.name(), piece);
    }

    record Committed(Game game, List<GameLogEntry> entries) {}

    /** Saves state and events atomically, then publishes the committed update. */
    Committed commit(Game game, List<GameEvent> events, boolean lobbyChange) {
        var firstSeq = game.getLastEventSeq() + 1;
        var time = System.currentTimeMillis();
        var entries = IntStream.range(0, events.size())
                .mapToObj(i -> GameLogEntry.of(game.getId(), firstSeq + i, time, events.get(i)))
                .toList();
        game.setLastEventSeq(game.getLastEventSeq() + events.size());
        var saved = gameRepository.saveWithEvents(game, entries);

        messagingService.send(gameTopic(saved.getId()), new GameUpdate(saved.getId(), saved.getVersion(), entries));
        if (lobbyChange && saved.getMode() == GameMode.NORMAL) {
            messagingService.send(LOBBY_TOPIC, new LobbyChange(saved.getId(), saved));
        }
        return new Committed(saved, entries);
    }

}
