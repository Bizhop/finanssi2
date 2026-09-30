package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.data.Square;
import fi.bizhop.finanssi2.game.data.SquareType;
import fi.bizhop.finanssi2.game.engine.GameCommand.BuyCar;
import fi.bizhop.finanssi2.game.engine.GameCommand.EndTurn;
import fi.bizhop.finanssi2.game.engine.GameCommand.Roll;
import fi.bizhop.finanssi2.game.engine.GameCommand.SellCar;
import fi.bizhop.finanssi2.game.engine.GameEvent.CarBought;
import fi.bizhop.finanssi2.game.engine.GameEvent.CarSold;
import fi.bizhop.finanssi2.game.engine.GameEvent.DiceRolled;
import fi.bizhop.finanssi2.game.engine.GameEvent.LandedOn;
import fi.bizhop.finanssi2.game.engine.GameEvent.NotImplemented;
import fi.bizhop.finanssi2.game.engine.GameEvent.PieceMoved;
import fi.bizhop.finanssi2.game.engine.GameEvent.TurnEnded;
import fi.bizhop.finanssi2.game.engine.GameEvent.TurnStarted;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static fi.bizhop.finanssi2.game.data.GameConstants.CAR_PRICE;
import static fi.bizhop.finanssi2.game.data.GameConstants.CAR_SELL_BACK_PRICE;
import static fi.bizhop.finanssi2.game.data.GameConstants.SQUARE_COUNT;

/**
 * Applies player commands to a game state. Each command is first validated without changes and then applied, so a rejected command
 * ({@link RuleViolation}, {@link NotYourTurn}) leaves the state untouched.
 */
public class GameEngine {
    /** When a command may be sent, as the rules put it */
    enum Timing {
        BEFORE_ROLL,
        AFTER_ROLL,
        // Any time during one's own turn, before or after rolling
        OWN_TURN
    }

    static final Map<Class<? extends GameCommand>, Timing> TIMING = Map.of(
            Roll.class, Timing.BEFORE_ROLL,
            EndTurn.class, Timing.AFTER_ROLL,
            BuyCar.class, Timing.BEFORE_ROLL,
            SellCar.class, Timing.OWN_TURN);

    final GameData gameData;
    final Rules rules;
    final Map<SquareType, SquareHandler> squareHandlers = new EnumMap<>(SquareType.class);

    public GameEngine(GameData gameData, Rules rules) {
        this.gameData = gameData;
        this.rules = rules;
        squareHandlers.put(SquareType.BANK_EXIT, (state, player, square) -> List.of());
        squareHandlers.put(SquareType.BANK_ENTRANCE, (state, player, square) -> List.of());
    }

    // Commands without parameters, checked as they are for allowedCommands
    static final List<GameCommand> SIMPLE_COMMANDS = List.of(new Roll(), new EndTurn(), new BuyCar(), new SellCar());

    public List<GameEvent> handle(GameState state, String uid, GameCommand command, Dice dice) {
        validate(state, uid, command);
        var player = state.current();
        return switch (command) {
            case Roll ignored -> roll(state, player, dice);
            case EndTurn ignored -> endTurn(state, player);
            case BuyCar ignored -> buyCar(player);
            case SellCar ignored -> sellCar(player);
        };
    }

    /** Command types ({@code type} values) the player may send right now */
    public List<String> allowedCommands(GameState state, String uid) {
        return SIMPLE_COMMANDS.stream()
                .filter(command -> isAllowed(state, uid, command))
                .map(command -> command.getClass().getSimpleName())
                .sorted()
                .toList();
    }

    boolean isAllowed(GameState state, String uid, GameCommand command) {
        try {
            validate(state, uid, command);
            return true;
        } catch (RuleViolation | NotYourTurn e) {
            return false;
        }
    }

    /** Throws if the command is not allowed; changes nothing */
    void validate(GameState state, String uid, GameCommand command) {
        if (!uid.equals(state.getCurrentPlayer())) {
            throw new NotYourTurn();
        }
        var phase = state.getPhase();
        switch (TIMING.get(command.getClass())) {
            case BEFORE_ROLL -> require(phase == TurnPhase.BEFORE_ROLL, "Only allowed before rolling");
            case AFTER_ROLL -> require(phase == TurnPhase.AFTER_ROLL, "Only allowed after rolling");
            case OWN_TURN -> {}
        }
        var player = state.current();
        switch (command) {
            case Roll ignored -> {}
            case EndTurn ignored -> {}
            case BuyCar ignored -> {
                // With at most 6 players and one car each, the 6 car certificates never run out
                require(!player.isCar(), "You already have a car");
                require(player.getCash() >= CAR_PRICE, "Not enough cash for a car");
            }
            case SellCar ignored -> require(player.isCar(), "You have no car");
        }
    }

    static void require(boolean condition, String reason) {
        if (!condition) {
            throw new RuleViolation(reason);
        }
    }

    List<GameEvent> roll(GameState state, PlayerState player, Dice dice) {
        var events = new ArrayList<GameEvent>();
        var roll = new ArrayList<Integer>();
        for (int i = 0; i < rules.movementDice(state, player); i++) {
            roll.add(dice.roll());
        }
        events.add(new DiceRolled(player.getUid(), List.copyOf(roll)));
        var from = player.getPosition();
        var to = moveForward(from, roll.stream().mapToInt(Integer::intValue).sum());
        player.setPosition(to);
        events.add(new PieceMoved(player.getUid(), from, to));
        events.addAll(land(state, player));
        state.setPhase(TurnPhase.AFTER_ROLL);
        return events;
    }

    /** Square reached by moving forward: one loop 1 → 46 → 1, stopping on mandatory stops (34 and 1) */
    int moveForward(int from, int steps) {
        var position = from;
        for (int i = 0; i < steps; i++) {
            position = position % SQUARE_COUNT + 1;
            if (gameData.square(position).mandatoryStop()) {
                break;
            }
        }
        return position;
    }

    List<GameEvent> land(GameState state, PlayerState player) {
        var events = new ArrayList<GameEvent>();
        var square = gameData.square(player.getPosition());
        events.add(new LandedOn(player.getUid(), square.number()));
        events.addAll(squareHandlers.getOrDefault(square.type(), GameEngine::notImplemented).land(state, player, square));
        return events;
    }

    static List<GameEvent> notImplemented(GameState state, PlayerState player, Square square) {
        return List.of(new NotImplemented(player.getUid(), square.number(), square.type()));
    }

    List<GameEvent> endTurn(GameState state, PlayerState player) {
        var next = nextPlayer(state);
        state.setCurrentPlayer(next);
        state.setPhase(TurnPhase.BEFORE_ROLL);
        return List.of(new TurnEnded(player.getUid()), new TurnStarted(next));
    }

    /** The next player in turn order who is still in the game */
    static String nextPlayer(GameState state) {
        var order = state.getTurnOrder();
        var index = order.indexOf(state.getCurrentPlayer());
        for (int i = 1; i <= order.size(); i++) {
            var candidate = order.get((index + i) % order.size());
            if (!state.player(candidate).orElseThrow().isOut()) {
                return candidate;
            }
        }
        throw new IllegalStateException("No players left");
    }

    List<GameEvent> buyCar(PlayerState player) {
        player.setCash(player.getCash() - CAR_PRICE);
        player.setCar(true);
        return List.of(new CarBought(player.getUid(), CAR_PRICE));
    }

    List<GameEvent> sellCar(PlayerState player) {
        player.setCash(player.getCash() + CAR_SELL_BACK_PRICE);
        player.setCar(false);
        return List.of(new CarSold(player.getUid(), CAR_SELL_BACK_PRICE));
    }
}
