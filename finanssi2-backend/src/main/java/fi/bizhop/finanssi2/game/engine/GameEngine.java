package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.data.Square;
import fi.bizhop.finanssi2.game.data.SquareType;
import fi.bizhop.finanssi2.game.data.Share;
import fi.bizhop.finanssi2.game.data.TitleDeed;
import fi.bizhop.finanssi2.game.engine.GameCommand.Build;
import fi.bizhop.finanssi2.game.engine.GameCommand.BuyCar;
import fi.bizhop.finanssi2.game.engine.GameCommand.BuyProperty;
import fi.bizhop.finanssi2.game.engine.GameCommand.BuyShare;
import fi.bizhop.finanssi2.game.engine.GameCommand.Mortgage;
import fi.bizhop.finanssi2.game.engine.GameCommand.Redeem;
import fi.bizhop.finanssi2.game.engine.GameCommand.SellBackProperty;
import fi.bizhop.finanssi2.game.engine.GameCommand.SellBackShare;
import fi.bizhop.finanssi2.game.engine.GameCommand.DeclareBankruptcy;
import fi.bizhop.finanssi2.game.engine.GameCommand.EndTurn;
import fi.bizhop.finanssi2.game.engine.GameCommand.Pay;
import fi.bizhop.finanssi2.game.engine.GameCommand.RepayLoan;
import fi.bizhop.finanssi2.game.engine.GameCommand.Roll;
import fi.bizhop.finanssi2.game.engine.GameCommand.SellCar;
import fi.bizhop.finanssi2.game.engine.GameCommand.TakeLoan;
import fi.bizhop.finanssi2.game.engine.GameEvent.AssetsReturned;
import fi.bizhop.finanssi2.game.engine.GameEvent.BankEntranceRoll;
import fi.bizhop.finanssi2.game.engine.GameEvent.PropertyBought;
import fi.bizhop.finanssi2.game.engine.GameEvent.PropertyBuilt;
import fi.bizhop.finanssi2.game.engine.GameEvent.PropertyMortgaged;
import fi.bizhop.finanssi2.game.engine.GameEvent.PropertyRedeemed;
import fi.bizhop.finanssi2.game.engine.GameEvent.PropertySoldBack;
import fi.bizhop.finanssi2.game.engine.GameEvent.RentCharged;
import fi.bizhop.finanssi2.game.engine.GameEvent.ShareBought;
import fi.bizhop.finanssi2.game.engine.GameEvent.ShareSoldBack;
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

    static final Map<Class<? extends GameCommand>, Timing> TIMING = Map.ofEntries(
            Map.entry(Roll.class, Timing.BEFORE_ROLL),
            Map.entry(EndTurn.class, Timing.AFTER_ROLL),
            Map.entry(BuyCar.class, Timing.BEFORE_ROLL),
            Map.entry(SellCar.class, Timing.OWN_TURN),
            Map.entry(TakeLoan.class, Timing.OWN_TURN),
            Map.entry(RepayLoan.class, Timing.OWN_TURN),
            Map.entry(Pay.class, Timing.DECISION),
            Map.entry(DeclareBankruptcy.class, Timing.DECISION),
            Map.entry(BuyProperty.class, Timing.BEFORE_ROLL),
            Map.entry(BuyShare.class, Timing.BEFORE_ROLL),
            Map.entry(Mortgage.class, Timing.OWN_TURN),
            Map.entry(Redeem.class, Timing.BEFORE_ROLL),
            Map.entry(SellBackProperty.class, Timing.OWN_TURN),
            Map.entry(SellBackShare.class, Timing.OWN_TURN),
            Map.entry(Build.class, Timing.BEFORE_ROLL));

    /** Commands allowed while a decision of this type is pending, in or out of turn */
    static Set<Class<? extends GameCommand>> decisionCommands(PendingDecision decision) {
        return switch (decision) {
            case RaiseFunds ignored -> Set.of(TakeLoan.class, SellCar.class, Mortgage.class, SellBackProperty.class, SellBackShare.class,
                    Pay.class, DeclareBankruptcy.class);
        };
    }

    // Commands without parameters, checked as they are for allowedCommands
    static final List<GameCommand> SIMPLE_COMMANDS = List.of(new Roll(), new EndTurn(), new BuyCar(), new SellCar(), new TakeLoan(),
            new RepayLoan(), new Pay(), new DeclareBankruptcy());

    static final int REPAY_LOAN_SQUARE = 43;
    static final int BRANCH_OFFICE_SQUARE = 11;
    static final int HEAD_OFFICE_FIRST_SQUARE = 35;

    final GameData gameData;
    final Rules rules;
    final Payments payments = new Payments();
    final Map<SquareType, SquareHandler> squareHandlers = new EnumMap<>(SquareType.class);
    // Every command with every parameter value, for allowedCommands
    final List<GameCommand> candidateCommands;

    public GameEngine(GameData gameData, Rules rules) {
        this.gameData = gameData;
        this.rules = rules;
        squareHandlers.put(SquareType.BANK_EXIT, this::bankExit);
        squareHandlers.put(SquareType.BANK_ENTRANCE, this::bankEntrance);
        squareHandlers.put(SquareType.REPAY_LOAN, this::repayLoanSquare);
        squareHandlers.put(SquareType.PROPERTY, this::property);
        candidateCommands = new ArrayList<>(SIMPLE_COMMANDS);
        for (var deed : gameData.titleDeeds()) {
            candidateCommands.addAll(List.of(new BuyProperty(deed.square()), new Mortgage(deed.square()), new Redeem(deed.square()),
                    new SellBackProperty(deed.square()), new Build(List.of(deed.square()))));
        }
        for (var share : gameData.shares()) {
            candidateCommands.addAll(List.of(new BuyShare(share.id()), new SellBackShare(share.id())));
        }
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
            case BuyProperty buy -> buyProperty(state, player, buy.square());
            case BuyShare buy -> buyShare(state, player, buy.share());
            case Mortgage mortgage -> mortgage(state, player, mortgage.square());
            case Redeem redeem -> redeem(state, player, redeem.square());
            case SellBackProperty sell -> sellBackProperty(state, player, sell.square());
            case SellBackShare sell -> sellBackShare(state, player, sell.share());
            case Build build -> build(state, player, build.squares());
        };
    }

    /** Command types ({@code type} values) the player may send right now */
    public List<String> allowedCommands(GameState state, String uid) {
        return candidateCommands.stream()
                .filter(command -> isAllowed(state, uid, command))
                .map(command -> command.getClass().getSimpleName())
                .distinct()
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
            case BuyProperty buy -> {
                var deed = deed(buy.square());
                requirePurchase(state, player);
                require(state.property(deed.square()).getOwner() == null, "The property is not for sale");
                require(player.getCash() >= rules.propertyPrice(state, deed), "Not enough cash");
            }
            case BuyShare buy -> {
                var share = share(buy.share());
                requirePurchase(state, player);
                require(state.share(share.id()).getOwner() == null, "The share is not for sale");
                require(player.getCash() >= rules.sharePrice(state, share), "Not enough cash");
            }
            case Mortgage mortgage -> {
                var property = ownProperty(state, player, mortgage.square());
                require(!property.isMortgaged(), "Already mortgaged");
                require(rules.mortgageValue(state, deed(property.getSquare()), property) != null, "This property cannot be mortgaged");
            }
            case Redeem redeem -> {
                var property = ownProperty(state, player, redeem.square());
                require(property.isMortgaged(), "Not mortgaged");
                require(player.getCash() >= rules.redemptionPrice(state, deed(property.getSquare()), property), "Not enough cash");
            }
            case SellBackProperty sell -> {
                var property = ownProperty(state, player, sell.square());
                require(!property.isMortgaged(), "Redeem the mortgage first");
                require(rules.propertyBuyBack(state, deed(property.getSquare()), property) != null,
                        "The bank does not buy this property back");
            }
            case SellBackShare sell -> {
                var share = share(sell.share());
                require(player.getUid().equals(state.share(share.id()).getOwner()), "Not your share");
            }
            case Build build -> {
                require(rules.canBuild(state, player), "Building is allowed only on squares 17 and 40");
                require(!build.squares().isEmpty(), "Nothing to build");
                require(build.squares().stream().distinct().count() == build.squares().size(), "One building per property");
                var cost = 0;
                for (var square : build.squares()) {
                    var property = ownProperty(state, player, square);
                    var deed = deed(square);
                    require(deed.building() != null, deed.name() + " cannot be built on");
                    require(!property.isBuilt(), deed.name() + " is already built");
                    require(!property.isMortgaged(), "Redeem " + deed.name() + " before building");
                    cost += rules.buildingPrice(state, deed);
                }
                require(player.getCash() >= cost, "Not enough cash");
            }
        }
    }

    TitleDeed deed(int square) {
        require(square >= 1 && square <= SQUARE_COUNT && gameData.square(square).type() == SquareType.PROPERTY, "No property on square " + square);
        return gameData.titleDeed(square);
    }

    Share share(String id) {
        require(gameData.shares().stream().anyMatch(share -> share.id().equals(id)), "No share " + id);
        return gameData.share(id);
    }

    static PropertyState ownProperty(GameState state, PlayerState player, int square) {
        var property = state.getProperties().stream().filter(p -> p.getSquare() == square).findFirst();
        require(property.isPresent() && player.getUid().equals(property.get().getOwner()), "Not your property");
        return property.get();
    }

    /** Purchases: on the branch office (11) or in the head office (35–46), one per turn */
    static void requirePurchase(GameState state, PlayerState player) {
        var position = player.getPosition();
        require(position == BRANCH_OFFICE_SQUARE || position >= HEAD_OFFICE_FIRST_SQUARE,
                "Properties and shares are sold only on square 11 and squares 35–46");
        require(!state.isBoughtThisTurn(), "Only one purchase per turn");
    }

    static void require(boolean condition, String reason) {
        if (!condition) {
            throw new RuleViolation(reason);
        }
    }

    /**
     * Cash the player could have after selling and borrowing everything they can. Each unmortgaged property is either mortgaged or
     * sold back, whichever gives more; mortgaged ones cannot be redeemed while raising funds, so they add nothing.
     */
    int fundsAvailable(GameState state, PlayerState player) {
        var funds = player.getCash();
        if (player.isCar()) {
            funds += CAR_SELL_BACK_PRICE;
        }
        if (player.getPosition() != REPAY_LOAN_SQUARE) {
            funds += rules.loansAvailable(state, player) * LOAN_AMOUNT;
        }
        var ownership = new Ownership(gameData, state);
        for (var property : ownership.propertiesOf(player.getUid())) {
            if (!property.isMortgaged()) {
                var deed = gameData.titleDeed(property.getSquare());
                var mortgage = rules.mortgageValue(state, deed, property);
                var buyBack = rules.propertyBuyBack(state, deed, property);
                funds += Math.max(mortgage == null ? 0 : mortgage, buyBack == null ? 0 : buyBack);
            }
        }
        for (var share : ownership.sharesOf(player.getUid())) {
            funds += rules.shareBuyBack(state, share);
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
        return List.of(new TurnEnded(player.getUid()), passTurn(state));
    }

    /** Starts the next player's turn */
    static GameEvent passTurn(GameState state) {
        var next = nextPlayer(state);
        state.setCurrentPlayer(next);
        state.setPhase(TurnPhase.BEFORE_ROLL);
        state.setBoughtThisTurn(false);
        return new TurnStarted(next);
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

    /** Landing on a property: rent to its owner, if another player owns it and it is not mortgaged */
    List<GameEvent> property(GameState state, PlayerState player, Square square, Dice dice) {
        var property = state.property(square.number());
        var owner = property.getOwner();
        if (owner == null || owner.equals(player.getUid()) || property.isMortgaged()) {
            return List.of();
        }
        var deed = gameData.titleDeed(square.number());
        var completeGroup = new Ownership(gameData, state).ownsCompleteGroup(owner, deed.group());
        var rent = rules.rent(state, deed, property, player, completeGroup);
        if (rent == 0) {
            return List.of();
        }
        var events = new ArrayList<GameEvent>();
        events.add(new RentCharged(player.getUid(), owner, square.number(), rent, completeGroup));
        events.addAll(payments.charge(state, player, owner, List.of(new Charge(rent, MoneyReason.RENT))));
        return events;
    }

    List<GameEvent> buyProperty(GameState state, PlayerState player, int square) {
        var payment = payments.toBank(player, rules.propertyPrice(state, deed(square)), MoneyReason.PROPERTY_PURCHASE);
        state.property(square).setOwner(player.getUid());
        state.setBoughtThisTurn(true);
        return List.of(payment, new PropertyBought(player.getUid(), square));
    }

    List<GameEvent> buyShare(GameState state, PlayerState player, String id) {
        var payment = payments.toBank(player, rules.sharePrice(state, share(id)), MoneyReason.SHARE_PURCHASE);
        state.share(id).setOwner(player.getUid());
        state.setBoughtThisTurn(true);
        return List.of(payment, new ShareBought(player.getUid(), id));
    }

    List<GameEvent> mortgage(GameState state, PlayerState player, int square) {
        var property = state.property(square);
        var value = rules.mortgageValue(state, deed(square), property);
        property.setMortgaged(true);
        return List.of(new PropertyMortgaged(player.getUid(), square), payments.fromBank(player, value, MoneyReason.MORTGAGE));
    }

    List<GameEvent> redeem(GameState state, PlayerState player, int square) {
        var property = state.property(square);
        var payment = payments.toBank(player, rules.redemptionPrice(state, deed(square), property), MoneyReason.REDEMPTION);
        property.setMortgaged(false);
        return List.of(payment, new PropertyRedeemed(player.getUid(), square));
    }

    List<GameEvent> sellBackProperty(GameState state, PlayerState player, int square) {
        var property = state.property(square);
        var value = rules.propertyBuyBack(state, deed(square), property);
        property.setOwner(null);
        property.setBuilt(false);
        return List.of(new PropertySoldBack(player.getUid(), square), payments.fromBank(player, value, MoneyReason.PROPERTY_SALE));
    }

    List<GameEvent> sellBackShare(GameState state, PlayerState player, String id) {
        state.share(id).setOwner(null);
        return List.of(new ShareSoldBack(player.getUid(), id),
                payments.fromBank(player, rules.shareBuyBack(state, share(id)), MoneyReason.SHARE_SALE));
    }

    List<GameEvent> build(GameState state, PlayerState player, List<Integer> squares) {
        var events = new ArrayList<GameEvent>();
        for (var square : squares) {
            var deed = deed(square);
            events.add(payments.toBank(player, rules.buildingPrice(state, deed), MoneyReason.CONSTRUCTION));
            state.property(square).setBuilt(true);
            events.add(new PropertyBuilt(player.getUid(), square, deed.building().industrial()));
        }
        return events;
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
        var properties = new ArrayList<Integer>();
        for (var property : state.getProperties()) {
            if (player.getUid().equals(property.getOwner())) {
                property.setOwner(null);
                property.setMortgaged(false);
                property.setBuilt(false);
                properties.add(property.getSquare());
            }
        }
        var shares = new ArrayList<String>();
        for (var share : state.getShares()) {
            if (player.getUid().equals(share.getOwner())) {
                share.setOwner(null);
                shares.add(share.getId());
            }
        }
        if (!properties.isEmpty() || !shares.isEmpty()) {
            events.add(new AssetsReturned(player.getUid(), properties, shares));
        }
        if (player.getUid().equals(state.getCurrentPlayer())) {
            events.add(passTurn(state));
        }
        return events;
    }
}
