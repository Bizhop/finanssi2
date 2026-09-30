package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.data.Square;
import fi.bizhop.finanssi2.game.data.SquareType;
import fi.bizhop.finanssi2.game.engine.GameCommand.BuyCar;
import fi.bizhop.finanssi2.game.engine.GameCommand.DeclareBankruptcy;
import fi.bizhop.finanssi2.game.engine.GameCommand.EndTurn;
import fi.bizhop.finanssi2.game.engine.GameCommand.Pay;
import fi.bizhop.finanssi2.game.engine.GameCommand.RepayLoan;
import fi.bizhop.finanssi2.game.engine.GameCommand.Roll;
import fi.bizhop.finanssi2.game.engine.GameCommand.SellCar;
import fi.bizhop.finanssi2.game.engine.GameCommand.TakeLoan;
import fi.bizhop.finanssi2.game.engine.GameEvent.BankEntranceRoll;
import fi.bizhop.finanssi2.game.engine.GameEvent.CarBought;
import fi.bizhop.finanssi2.game.engine.GameEvent.CarSold;
import fi.bizhop.finanssi2.game.engine.GameEvent.DiceRolled;
import fi.bizhop.finanssi2.game.engine.GameEvent.LandedOn;
import fi.bizhop.finanssi2.game.engine.GameEvent.LoanRepaid;
import fi.bizhop.finanssi2.game.engine.GameEvent.LoanTaken;
import fi.bizhop.finanssi2.game.engine.GameEvent.NotImplemented;
import fi.bizhop.finanssi2.game.engine.GameEvent.PieceMoved;
import fi.bizhop.finanssi2.game.engine.GameEvent.PlayerBankrupt;
import fi.bizhop.finanssi2.game.engine.GameEvent.TurnEnded;
import fi.bizhop.finanssi2.game.engine.GameEvent.TurnStarted;
import fi.bizhop.finanssi2.game.engine.PendingDecision.RaiseFunds;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static fi.bizhop.finanssi2.game.data.GameConstants.CAR_PRICE;
import static fi.bizhop.finanssi2.game.data.GameConstants.CAR_SELL_BACK_PRICE;
import static fi.bizhop.finanssi2.game.data.GameConstants.LOAN_AMOUNT;
import static fi.bizhop.finanssi2.game.data.GameConstants.SQUARE_COUNT;

/**
 * Applies player commands to a game state. Each command is first validated without changes and then applied, so a rejected command
 * ({@link RuleViolation}, {@link NotYourTurn}) leaves the state untouched.
 */
public class GameEngine {
    /** When a command may be sent while no decision is pending, as the rules put it */
    enum Timing {
        BEFORE_ROLL,
        AFTER_ROLL,
        // Any time during one's own turn, before or after rolling
        OWN_TURN,
        // Only to resolve a pending decision
        DECISION
    }

    static final Map<Class<? extends GameCommand>, Timing> TIMING = Map.of(
            Roll.class, Timing.BEFORE_ROLL,
            EndTurn.class, Timing.AFTER_ROLL,
            BuyCar.class, Timing.BEFORE_ROLL,
            SellCar.class, Timing.OWN_TURN,
            TakeLoan.class, Timing.OWN_TURN,
            RepayLoan.class, Timing.OWN_TURN,
            Pay.class, Timing.DECISION,
            DeclareBankruptcy.class, Timing.DECISION);

    /** Commands allowed while a decision of this type is pending, in or out of turn */
    static Set<Class<? extends GameCommand>> decisionCommands(PendingDecision decision) {
        return switch (decision) {
            case RaiseFunds ignored -> Set.of(TakeLoan.class, SellCar.class, Pay.class, DeclareBankruptcy.class);
        };
    }

    // Commands without parameters, checked as they are for allowedCommands
    static final List<GameCommand> SIMPLE_COMMANDS = List.of(new Roll(), new EndTurn(), new BuyCar(), new SellCar(), new TakeLoan(),
            new RepayLoan(), new Pay(), new DeclareBankruptcy());

    static final int REPAY_LOAN_SQUARE = 43;

    final GameData gameData;
    final Rules rules;
    final Payments payments = new Payments();
    final Map<SquareType, SquareHandler> squareHandlers = new EnumMap<>(SquareType.class);

    public GameEngine(GameData gameData, Rules rules) {
        this.gameData = gameData;
        this.rules = rules;
        squareHandlers.put(SquareType.BANK_EXIT, this::bankExit);
        squareHandlers.put(SquareType.BANK_ENTRANCE, this::bankEntrance);
        squareHandlers.put(SquareType.REPAY_LOAN, this::repayLoanSquare);
    }

    public List<GameEvent> handle(GameState state, String uid, GameCommand command, Dice dice) {
        validate(state, uid, command);
        var player = state.player(uid).orElseThrow();
        return switch (command) {
            case Roll ignored -> roll(state, player, dice);
            case EndTurn ignored -> endTurn(state, player);
            case BuyCar ignored -> buyCar(player);
            case SellCar ignored -> sellCar(player);
            case TakeLoan ignored -> takeLoan(player);
            case RepayLoan ignored -> repayLoan(player);
            case Pay ignored -> pay(state);
            case DeclareBankruptcy ignored -> declareBankruptcy(state, player);
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
        if (!uid.equals(state.actor())) {
            throw new NotYourTurn();
        }
        var pending = state.getPendingDecisions();
        if (!pending.isEmpty()) {
            require(decisionCommands(pending.getFirst()).contains(command.getClass()), "Resolve the pending decision first");
        } else {
            var phase = state.getPhase();
            switch (TIMING.get(command.getClass())) {
                case BEFORE_ROLL -> require(phase == TurnPhase.BEFORE_ROLL, "Only allowed before rolling");
                case AFTER_ROLL -> require(phase == TurnPhase.AFTER_ROLL, "Only allowed after rolling");
                case OWN_TURN -> {}
                case DECISION -> throw new RuleViolation("No pending decision");
            }
        }
        var player = state.player(uid).orElseThrow();
        switch (command) {
            case Roll ignored -> {}
            case EndTurn ignored -> {}
            case BuyCar ignored -> {
                // With at most 6 players and one car each, the 6 car certificates never run out
                require(!player.isCar(), "You already have a car");
                require(player.getCash() >= CAR_PRICE, "Not enough cash for a car");
            }
            case SellCar ignored -> require(player.isCar(), "You have no car");
            case TakeLoan ignored -> {
                require(player.getPosition() != REPAY_LOAN_SQUARE, "No new loans on square " + REPAY_LOAN_SQUARE);
                require(rules.loansAvailable(state, player) > 0, "No loans available");
            }
            case RepayLoan ignored -> {
                require(player.getLoans() > 0, "You have no loans");
                require(player.getCash() >= LOAN_AMOUNT, "Not enough cash to repay a loan");
            }
            case Pay ignored -> {
                var decision = (RaiseFunds) pending.getFirst();
                require(player.getCash() >= decision.amount(), "Not enough cash; raise funds first");
            }
            case DeclareBankruptcy ignored -> {
                var decision = (RaiseFunds) pending.getFirst();
                require(fundsAvailable(state, player) < decision.amount(), "You can still raise enough funds");
            }
        }
    }

    static void require(boolean condition, String reason) {
        if (!condition) {
            throw new RuleViolation(reason);
        }
    }

    /** Cash the player could have after selling and borrowing everything they can */
    int fundsAvailable(GameState state, PlayerState player) {
        var funds = player.getCash();
        if (player.isCar()) {
            funds += CAR_SELL_BACK_PRICE;
        }
        if (player.getPosition() != REPAY_LOAN_SQUARE) {
            funds += rules.loansAvailable(state, player) * LOAN_AMOUNT;
        }
        return funds;
    }

    List<GameEvent> roll(GameState state, PlayerState player, Dice dice) {
        var events = new ArrayList<GameEvent>();
        var roll = rollDice(dice, rules.movementDice(state, player));
        events.add(new DiceRolled(player.getUid(), roll));
        var from = player.getPosition();
        var to = moveForward(from, roll.stream().mapToInt(Integer::intValue).sum());
        player.setPosition(to);
        events.add(new PieceMoved(player.getUid(), from, to));
        events.addAll(land(state, player, dice));
        state.setPhase(TurnPhase.AFTER_ROLL);
        return events;
    }

    static List<Integer> rollDice(Dice dice, int count) {
        var roll = new ArrayList<Integer>();
        for (int i = 0; i < count; i++) {
            roll.add(dice.roll());
        }
        return List.copyOf(roll);
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

    List<GameEvent> land(GameState state, PlayerState player, Dice dice) {
        var events = new ArrayList<GameEvent>();
        var square = gameData.square(player.getPosition());
        events.add(new LandedOn(player.getUid(), square.number()));
        events.addAll(squareHandlers.getOrDefault(square.type(), GameEngine::notImplemented).land(state, player, square, dice));
        return events;
    }

    static List<GameEvent> notImplemented(GameState state, PlayerState player, Square square, Dice dice) {
        return List.of(new NotImplemented(player.getUid(), square.number(), square.type()));
    }

    /** Square 1: interest on every loan */
    List<GameEvent> bankExit(GameState state, PlayerState player, Square square, Dice dice) {
        if (player.getLoans() == 0) {
            return List.of();
        }
        var interest = new Charge(player.getLoans() * rules.loanInterest(state), MoneyReason.LOAN_INTEREST);
        return payments.charge(state, player, null, List.of(interest));
    }

    /** Square 34: the player rolls both dice, car or not, and the bank pays a reward by the pips */
    List<GameEvent> bankEntrance(GameState state, PlayerState player, Square square, Dice dice) {
        var roll = rollDice(dice, 2);
        return List.of(
                new BankEntranceRoll(player.getUid(), roll),
                payments.fromBank(player, rules.bankEntranceReward(state, roll), MoneyReason.BANK_ENTRANCE_REWARD));
    }

    /** Square 43: repay one loan, if the player has any, with interest on it */
    List<GameEvent> repayLoanSquare(GameState state, PlayerState player, Square square, Dice dice) {
        if (player.getLoans() == 0) {
            return List.of();
        }
        return payments.charge(state, player, null, List.of(
                new Charge(LOAN_AMOUNT, MoneyReason.LOAN_REPAYMENT),
                new Charge(rules.loanInterest(state), MoneyReason.LOAN_INTEREST)));
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
        player.setCar(true);
        return List.of(payments.toBank(player, CAR_PRICE, MoneyReason.CAR_PURCHASE), new CarBought(player.getUid()));
    }

    List<GameEvent> sellCar(PlayerState player) {
        player.setCar(false);
        return List.of(new CarSold(player.getUid()), payments.fromBank(player, CAR_SELL_BACK_PRICE, MoneyReason.CAR_SALE));
    }

    List<GameEvent> takeLoan(PlayerState player) {
        player.setLoans(player.getLoans() + 1);
        return List.of(new LoanTaken(player.getUid(), player.getLoans()), payments.fromBank(player, LOAN_AMOUNT, MoneyReason.LOAN));
    }

    List<GameEvent> repayLoan(PlayerState player) {
        var payment = payments.toBank(player, LOAN_AMOUNT, MoneyReason.LOAN_REPAYMENT);
        player.setLoans(player.getLoans() - 1);
        return List.of(payment, new LoanRepaid(player.getUid(), player.getLoans()));
    }

    List<GameEvent> pay(GameState state) {
        var decision = (RaiseFunds) state.getPendingDecisions().removeFirst();
        return payments.settle(state, decision);
    }

    /**
     * The creditor gets the player's cash and the rest of the debt is written off (R13). Loans are cancelled and the car goes back
     * to the bank. If it was the player's turn, the turn passes on.
     */
    List<GameEvent> declareBankruptcy(GameState state, PlayerState player) {
        var decision = (RaiseFunds) state.getPendingDecisions().removeFirst();
        var events = new ArrayList<GameEvent>();
        if (player.getCash() > 0) {
            var creditor = decision.creditor() == null ? null : state.player(decision.creditor()).orElseThrow();
            events.add(payments.transfer(player, creditor, player.getCash(), MoneyReason.BANKRUPTCY));
        }
        player.setLoans(0);
        player.setCar(false);
        player.setOut(true);
        events.add(new PlayerBankrupt(player.getUid(), decision.creditor()));
        if (player.getUid().equals(state.getCurrentPlayer())) {
            var next = nextPlayer(state);
            state.setCurrentPlayer(next);
            state.setPhase(TurnPhase.BEFORE_ROLL);
            events.add(new TurnStarted(next));
        }
        return events;
    }
}
