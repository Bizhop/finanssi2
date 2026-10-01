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
import fi.bizhop.finanssi2.game.engine.GameEvent.BankDividend;
import fi.bizhop.finanssi2.game.engine.GameEvent.BankEntranceRoll;
import fi.bizhop.finanssi2.game.engine.GameEvent.GoToJailRoll;
import fi.bizhop.finanssi2.game.engine.GameEvent.JailExempt;
import fi.bizhop.finanssi2.game.engine.GameEvent.JailRoll;
import fi.bizhop.finanssi2.game.engine.GameEvent.PlayerDividendCharged;
import fi.bizhop.finanssi2.game.engine.GameEvent.TurnSkipped;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static fi.bizhop.finanssi2.game.data.GameConstants.BOND_COUNT;
import static fi.bizhop.finanssi2.game.data.GameConstants.BOND_PRICE;
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
            Map.entry(Build.class, Timing.BEFORE_ROLL),
            Map.entry(GameCommand.BuyBond.class, Timing.DECISION),
            Map.entry(GameCommand.Pass.class, Timing.DECISION),
            Map.entry(GameCommand.BidBond.class, Timing.DECISION),
            Map.entry(GameCommand.ChooseNewsDirection.class, Timing.DECISION));

    /** Commands allowed while a decision of this type is pending, in or out of turn */
    static Set<Class<? extends GameCommand>> decisionCommands(PendingDecision decision) {
        return switch (decision) {
            case RaiseFunds ignored -> Set.of(TakeLoan.class, SellCar.class, Mortgage.class, SellBackProperty.class, SellBackShare.class, Pay.class, DeclareBankruptcy.class);
            case PendingDecision.BondOffer ignored -> Set.of(GameCommand.BuyBond.class, GameCommand.Pass.class);
            case PendingDecision.BondAuction ignored -> Set.of(GameCommand.BidBond.class);
            case PendingDecision.NewsDirection ignored -> Set.of(GameCommand.ChooseNewsDirection.class);
        };
    }

    // Commands without parameters, checked as they are for allowedCommands
    static final List<GameCommand> SIMPLE_COMMANDS = List.of(new Roll(), new EndTurn(), new BuyCar(), new SellCar(), new TakeLoan(),
            new RepayLoan(), new Pay(), new DeclareBankruptcy());

    static final int REPAY_LOAN_SQUARE = 43;
    static final int BRANCH_OFFICE_SQUARE = 11;
    static final int HEAD_OFFICE_FIRST_SQUARE = 35;
    static final int JAIL_SQUARE = 24;

    final GameData gameData;
    final Rules rules;
    final Payments payments = new Payments();
    final Bonds bonds;
    // Every command with every parameter value, for allowedCommands
    final List<GameCommand> candidateCommands;

    public GameEngine(GameData gameData, Rules rules) {
        this.gameData = gameData;
        this.rules = rules;
        this.bonds = new Bonds(rules, payments);
        var propertyCommands = gameData.titleDeeds().stream().flatMap(deed -> Stream.<GameCommand>of(
                new BuyProperty(deed.square()), new Mortgage(deed.square()), new Redeem(deed.square()),
                new SellBackProperty(deed.square()), new Build(List.of(deed.square()))));
        var shareCommands = gameData.shares().stream().flatMap(share -> Stream.<GameCommand>of(
                new BuyShare(share.id()), new SellBackShare(share.id())));
        var bondCommands = Stream.concat(
                IntStream.rangeClosed(1, BOND_COUNT).mapToObj(number -> (GameCommand) new GameCommand.BuyBond(number)),
                Stream.of(new GameCommand.Pass(), new GameCommand.BidBond(0), new GameCommand.ChooseNewsDirection(true),
                        new GameCommand.ChooseNewsDirection(false)));
        candidateCommands = Stream.of(SIMPLE_COMMANDS.stream(), propertyCommands, shareCommands, bondCommands)
                .flatMap(commands -> commands)
                .toList();
    }

    SquareHandler squareHandler(SquareType type) {
        return switch (type) {
            case BANK_EXIT -> this::bankExit;
            case BANK_ENTRANCE -> this::bankEntrance;
            case REPAY_LOAN -> this::repayLoanSquare;
            case PROPERTY -> this::property;
            case MOVE_TO -> this::moveToSquare;
            case JAIL -> this::jail;
            case GO_TO_JAIL_CHANCE -> this::goToJailChance;
            case BANK_DIVIDEND -> this::bankDividend;
            case PLAYER_DIVIDEND -> this::playerDividend;
            case SHARE_CRASH -> this::shareCrash;
            case BOND_PURCHASE_AND_DIVIDEND -> this::bondPurchaseAndDividend;
            case SMALL_BOND_DRAW -> bonds::smallBondDraw;
            case BOND_AUCTION -> bonds::bondAuction;
            case FINANCE_NEWS -> this::financeNews;
            case STOCK_TIP, BRANCH_OFFICE, CONSTRUCTION -> GameEngine::notImplemented;
        };
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
            case GameCommand.BuyBond buy -> bonds.buyBond(state, player, buy.number(), dice);
            case GameCommand.Pass ignored -> bonds.passBond(state, dice);
            case GameCommand.BidBond bid -> bonds.bidBond(state, player, bid.amount(), dice);
            case GameCommand.ChooseNewsDirection choose -> chooseNewsDirection(state, player, choose.forward(), dice);
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
        validateTiming(state, command);
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
                require(!rules.loansStopped(state), "No new loans while Finance News is active");
                require(player.getPosition() != REPAY_LOAN_SQUARE, "No new loans on square " + REPAY_LOAN_SQUARE);
                require(rules.loansAvailable(state, player) > 0, "No loans available");
            }
            case RepayLoan ignored -> {
                require(player.getLoans() > 0, "You have no loans");
                require(player.getCash() >= LOAN_AMOUNT, "Not enough cash to repay a loan");
            }
            case Pay ignored -> {
                require(pending.getFirst() instanceof RaiseFunds, "No payment to settle");
                var decision = (RaiseFunds) pending.getFirst();
                require(player.getCash() >= decision.amount(), "Not enough cash; raise funds first");
            }
            case DeclareBankruptcy ignored -> {
                require(pending.getFirst() instanceof RaiseFunds, "No payment to settle");
                var decision = (RaiseFunds) pending.getFirst();
                require(fundsAvailable(state, player) < decision.amount(), "You can still raise enough funds");
            }
            case BuyProperty buy -> {
                var deed = deed(buy.square());
                requirePurchase(state, player);
                require(state.property(deed.square()).getOwner() == null, "The property is not for sale");
                require(!rules.propertiesTradingStopped(state), "The bank is not selling properties");
                require(player.getCash() >= rules.propertyPrice(state, deed), "Not enough cash");
            }
            case BuyShare buy -> {
                var share = share(buy.share());
                requirePurchase(state, player);
                require(state.share(share.id()).getOwner() == null, "The share is not for sale");
                require(!rules.shareTradingStopped(state), "The bank is not selling shares");
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
                var cost = build.squares().stream().mapToInt(square -> {
                    var property = ownProperty(state, player, square);
                    var deed = deed(square);
                    require(deed.building() != null, deed.name() + " cannot be built on");
                    require(!property.isBuilt(), deed.name() + " is already built");
                    require(!property.isMortgaged(), "Redeem " + deed.name() + " before building");
                    return rules.buildingPrice(state, deed);
                }).sum();
                require(player.getCash() >= cost, "Not enough cash");
            }
            case GameCommand.BuyBond buy -> require(pending.getFirst() instanceof PendingDecision.BondOffer
                    && buy.number() >= 1 && buy.number() <= BOND_COUNT && player.getCash() >= rules.bondPrice(state)
                    && state.getBonds().stream().anyMatch(bond -> bond.getNumber() == buy.number() && bond.getOwner() == null),
                    "Bond unavailable or insufficient cash");
            case GameCommand.Pass ignored -> require(pending.getFirst() instanceof PendingDecision.BondOffer, "No bond offer");
            case GameCommand.BidBond bid -> require(pending.getFirst() instanceof PendingDecision.BondAuction
                    && bid.amount() >= 0 && bid.amount() % BOND_PRICE == 0 && bid.amount() <= player.getCash(), "Invalid bid");
            case GameCommand.ChooseNewsDirection ignored -> require(pending.getFirst() instanceof PendingDecision.NewsDirection,
                    "No Finance News direction to choose");
        }
    }

    static void validateTiming(GameState state, GameCommand command) {
        var pending = state.getPendingDecisions();
        if (!pending.isEmpty()) {
            require(decisionCommands(pending.getFirst()).contains(command.getClass()), "Resolve the pending decision first");
            return;
        }
        var phase = state.getPhase();
        switch (TIMING.get(command.getClass())) {
            case BEFORE_ROLL -> require(phase == TurnPhase.BEFORE_ROLL, "Only allowed before rolling");
            case AFTER_ROLL -> require(phase == TurnPhase.AFTER_ROLL, "Only allowed after rolling");
            case OWN_TURN -> {}
            case DECISION -> throw new RuleViolation("No pending decision");
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
        funds += ownership.propertiesOf(player.getUid()).stream().filter(property -> !property.isMortgaged())
                .mapToInt(property -> {
                    var deed = gameData.titleDeed(property.getSquare());
                    var mortgage = rules.mortgageValue(state, deed, property);
                    var buyBack = rules.propertyBuyBack(state, deed, property);
                    return Math.max(mortgage == null ? 0 : mortgage, buyBack == null ? 0 : buyBack);
                }).sum();
        funds += ownership.sharesOf(player.getUid()).stream().mapToInt(share -> rules.shareBuyBack(state, share)).sum();
        return funds;
    }

    List<GameEvent> roll(GameState state, PlayerState player, Dice dice) {
        var events = new ArrayList<GameEvent>();
        var roll = rollDice(dice, rules.movementDice(state, player));
        events.add(new DiceRolled(player.getUid(), roll));
        var from = player.getPosition();
        var pips = "FL-06".equals(state.getActiveFinanceNews()) ? roll.stream().mapToInt(Integer::intValue).min().orElse(0)
                : roll.stream().mapToInt(Integer::intValue).sum();
        if ("FL-16".equals(state.getActiveFinanceNews())) pips *= 2;
        var to = moveForward(from, pips);
        player.setPosition(to);
        events.add(new PieceMoved(player.getUid(), from, to));
        events.addAll(land(state, player, dice));
        state.setPhase(TurnPhase.AFTER_ROLL);
        return List.copyOf(events);
    }

    static List<Integer> rollDice(Dice dice, int count) {
        return IntStream.range(0, count).mapToObj(ignored -> dice.roll()).toList();
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

    /**
     * Moves the piece straight to the target, which takes effect as a landing (R3). Squares on the way have no effect and mandatory
     * stops do not apply (R4).
     */
    List<GameEvent> moveTo(GameState state, PlayerState player, int target, Dice dice) {
        var events = new ArrayList<GameEvent>();
        var from = player.getPosition();
        player.setPosition(target);
        events.add(new PieceMoved(player.getUid(), from, target));
        events.addAll(land(state, player, dice));
        return List.copyOf(events);
    }

    List<GameEvent> land(GameState state, PlayerState player, Dice dice) {
        return land(state, player, dice, true);
    }

    List<GameEvent> land(GameState state, PlayerState player, Dice dice, boolean drawFinanceNews) {
        var events = new ArrayList<GameEvent>();
        var square = gameData.square(player.getPosition());
        events.add(new LandedOn(player.getUid(), square.number()));
        if (drawFinanceNews || square.type() != SquareType.FINANCE_NEWS) {
            events.addAll(squareHandler(square.type()).land(state, player, square, dice));
        }
        return List.copyOf(events);
    }

    static List<GameEvent> notImplemented(GameState state, PlayerState player, Square square, Dice dice) {
        return List.of(new NotImplemented(player.getUid(), square.number(), square.type()));
    }

    /** Draw a Finance News card, replacing the previous active card and preserving the shuffled deck order. */
    List<GameEvent> financeNews(GameState state, PlayerState player, Square square, Dice dice) {
        if (state.getFinanceNewsDeck().isEmpty()) return List.of();
        var deck = state.getFinanceNewsDeck();
        var id = deck.removeFirst();
        deck.addLast(id);
        var replaced = state.getActiveFinanceNews();
        state.setActiveFinanceNews(switch (id) {
            case "FL-01", "FL-05", "FL-06", "FL-08", "FL-09", "FL-10", "FL-11", "FL-12", "FL-14", "FL-15",
                    "FL-16", "FL-17", "FL-20", "FL-21" -> id;
            default -> null;
        });
        var events = new ArrayList<GameEvent>();
        events.add(new GameEvent.FinanceNewsDrawn(player.getUid(), id, replaced));
        if (id.equals("FL-02") || id.equals("FL-03")) events.addAll(grandBondDraw(state, player, dice));
        if (id.equals("FL-01")) events.addAll(chargePlayers(state, player, p -> p.isCar() ? 5_000 : 0));
        if (id.equals("FL-07")) events.addAll(chargePlayers(state, player, p -> roundUp500(p.getCash(), 2)));
        if (id.equals("FL-13")) events.addAll(chargePlayers(state, player, p -> roundUp500(
                Math.min(p.getCash(), 100_000) / 10 + Math.max(0, p.getCash() - 100_000) / 4, 1)));
        if (id.equals("FL-04")) state.getPendingDecisions().add(new PendingDecision.NewsDirection(player.getUid(), id));
        if (id.equals("FL-10")) {
            for (var uid : List.copyOf(state.getTurnOrder())) {
                var standing = state.player(uid).orElseThrow();
                if (!standing.isOut() && standing.getPosition() == 34) events.addAll(moveTo(state, standing, 1, dice));
            }
        }
        if (id.equals("FL-19")) events.addAll(energyTax(state, player, dice));
        if (id.equals("FL-18")) {
            for (var shareholder : state.getTurnOrder()) {
                var recipient = state.player(shareholder).orElseThrow();
                if (recipient.isOut()) continue;
                var dividendEvents = bankDividend(state, recipient, gameData.square(46), dice);
                events.addAll(dividendEvents);
            }
        }
        return List.copyOf(events);
    }

    List<GameEvent> chooseNewsDirection(GameState state, PlayerState drawer, boolean forward, Dice dice) {
        var decision = (PendingDecision.NewsDirection) state.getPendingDecisions().removeFirst();
        var events = new ArrayList<GameEvent>();
        var from = drawer.getPosition();
        var to = from;
        var direction = forward ? 1 : -1;
        for (int i = 0; i < 3; i++) {
            to = (to - 1 + direction + SQUARE_COUNT) % SQUARE_COUNT + 1;
            if (gameData.square(to).mandatoryStop()) break;
        }
        drawer.setPosition(to);
        events.add(new PieceMoved(drawer.getUid(), from, to));
        events.addAll(land(state, drawer, dice, false));
        var order = state.getTurnOrder();
        var index = order.indexOf(drawer.getUid());
        for (int i = 1; i < order.size(); i++) {
            var other = state.player(order.get((index + i) % order.size())).orElseThrow();
            if (!other.isOut() && other.getMissedTurns() == 0) events.addAll(moveOneStep(state, other, true, dice));
        }
        return List.copyOf(events);
    }

    private List<GameEvent> moveOneStep(GameState state, PlayerState player, boolean forward, Dice dice) {
        var from = player.getPosition();
        var to = forward ? from % SQUARE_COUNT + 1 : (from + SQUARE_COUNT - 2) % SQUARE_COUNT + 1;
        player.setPosition(to);
        var events = new ArrayList<GameEvent>();
        events.add(new PieceMoved(player.getUid(), from, to));
        events.addAll(land(state, player, dice, false));
        return List.copyOf(events);
    }

    private List<GameEvent> energyTax(GameState state, PlayerState drawer, Dice dice) {
        var events = new ArrayList<GameEvent>();
        var order = state.getTurnOrder();
        var index = order.indexOf(drawer.getUid());
        for (int i = 0; i < order.size(); i++) {
            var target = state.player(order.get((index + i) % order.size())).orElseThrow();
            if (target.isOut()) continue;
            var built = state.getProperties().stream().filter(p -> target.getUid().equals(p.getOwner()) && p.isBuilt()).toList();
            if (built.isEmpty()) continue;
            var passedBankEntrance = target.getPosition() < 34;
            var amount = built.stream().mapToInt(p -> List.of(26, 27, 29, 30, 32, 33).contains(p.getSquare()) ? 30_000 : 20_000).sum();
            events.addAll(moveTo(state, target, 1, dice));
            if (passedBankEntrance) {
                var roll = rollDice(dice, 2);
                events.add(new BankEntranceRoll(target.getUid(), roll));
                events.add(payments.fromBank(target, rules.bankEntranceReward(state, roll), MoneyReason.BANK_ENTRANCE_REWARD));
            }
            events.addAll(payments.charge(state, target, null, List.of(new Charge(amount, MoneyReason.FINANCE_NEWS))));
        }
        return List.copyOf(events);
    }

    private List<GameEvent> chargePlayers(GameState state, PlayerState drawer, java.util.function.ToIntFunction<PlayerState> amount) {
        var result = new ArrayList<GameEvent>();
        var order = state.getTurnOrder();
        var start = order.indexOf(drawer.getUid());
        for (int i = 0; i < order.size(); i++) {
            var target = state.player(order.get((start + i) % order.size())).orElseThrow();
            if (target.isOut()) continue;
            var due = amount.applyAsInt(target);
            if (due > 0) result.addAll(payments.charge(state, target, null,
                    List.of(new Charge(due, MoneyReason.FINANCE_NEWS))));
        }
        return result;
    }

    private static int roundUp500(int amount, int divisor) {
        var value = (amount + divisor - 1) / divisor;
        return ((value + 499) / 500) * 500;
    }

    /** Square 1: interest on every loan. Square 36 affects the player again. */
    List<GameEvent> bankExit(GameState state, PlayerState player, Square square, Dice dice) {
        player.setJailExemption(false);
        if (player.getLoans() == 0) {
            return List.of();
        }
        var interest = new Charge(player.getLoans() * rules.loanInterest(state), MoneyReason.LOAN_INTEREST);
        return payments.charge(state, player, null, List.of(interest));
    }

    /** Square 34: the player rolls both dice, car or not, and the bank pays a reward by the pips */
    List<GameEvent> bankEntrance(GameState state, PlayerState player, Square square, Dice dice) {
        if ("FL-10".equals(state.getActiveFinanceNews())) return moveTo(state, player, 1, dice);
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

    /** Squares 2, 37 and 44 */
    List<GameEvent> moveToSquare(GameState state, PlayerState player, Square square, Dice dice) {
        return moveTo(state, player, square.target(), dice);
    }

    /** Square 24: one die for the turns to miss, 1–2 one, 3–4 two, 5–6 three */
    List<GameEvent> jail(GameState state, PlayerState player, Square square, Dice dice) {
        var die = dice.roll();
        var missedTurns = (die + 1) / 2;
        player.setMissedTurns(missedTurns);
        return List.of(new JailRoll(player.getUid(), die, missedTurns));
    }

    /** Square 36: one die, 1–2 goes to jail. Not for a player who has left jail and not been on square 1 since. */
    List<GameEvent> goToJailChance(GameState state, PlayerState player, Square square, Dice dice) {
        if (player.isJailExemption()) {
            return List.of(new JailExempt(player.getUid()));
        }
        var events = new ArrayList<GameEvent>();
        var die = dice.roll();
        var jailed = die <= 2;
        events.add(new GoToJailRoll(player.getUid(), die, jailed));
        if (jailed) {
            events.addAll(moveTo(state, player, JAIL_SQUARE, dice));
        }
        return List.copyOf(events);
    }

    /** Squares 16, 28, 39 and 42: the printed dividend of the player's shares, on 39 and 42 only those of the square's class */
    List<GameEvent> bankDividend(GameState state, PlayerState player, Square square, Dice dice) {
        var shares = new Ownership(gameData, state).sharesOf(player.getUid()).stream()
                .filter(share -> square.shareClass() == null || share.dividendPercent() == square.shareClass())
                .toList();
        var amount = shares.stream().mapToInt(share -> rules.bankDividend(state, share)).sum();
        if (amount == 0) {
            return List.of();
        }
        return List.of(
                new BankDividend(player.getUid(), square.number(), shares.stream().map(Share::id).toList(), amount),
                payments.fromBank(player, amount, MoneyReason.BANK_DIVIDEND));
    }

    /**
     * Square 41: each other player gets the square's percent of their share capital in the groups where the player owns a property.
     * Charged one by one in turn order from the player.
     */
    List<GameEvent> playerDividend(GameState state, PlayerState player, Square square, Dice dice) {
        var ownership = new Ownership(gameData, state);
        var groups = ownership.groupsWithPropertiesOf(player.getUid());
        var events = new ArrayList<GameEvent>();
        var order = state.getTurnOrder();
        var index = order.indexOf(player.getUid());
        for (int i = 1; i < order.size(); i++) {
            var shareholder = state.player(order.get((index + i) % order.size())).orElseThrow();
            if (shareholder.isOut()) {
                continue;
            }
            var shares = ownership.sharesOf(shareholder.getUid()).stream()
                    .filter(share -> share.group() != null && groups.contains(share.group()))
                    .toList();
            var amount = rules.playerDividend(state, square, shares.stream().mapToInt(Share::value).sum());
            if (amount <= 0) {
                continue;
            }
            events.add(new PlayerDividendCharged(player.getUid(), shareholder.getUid(), square.number(),
                    shares.stream().map(Share::id).toList(), amount));
            events.addAll(payments.charge(state, player, shareholder.getUid(), List.of(new Charge(amount, MoneyReason.PLAYER_DIVIDEND))));
        }
        return List.copyOf(events);
    }

    /** Square 35: the square's percent of the share capital outside complete groups, fund shares included */
    List<GameEvent> shareCrash(GameState state, PlayerState player, Square square, Dice dice) {
        var amount = rules.shareCrash(state, square, new Ownership(gameData, state).shareCapitalOutsideCompleteGroups(player.getUid()));
        if (amount == 0) {
            return List.of();
        }
        return payments.charge(state, player, null, List.of(new Charge(amount, MoneyReason.SHARE_CRASH)));
    }

    /** Square 46: dividend on all shares as on 16 and 28; the bond purchase comes with step 08 */
    List<GameEvent> bondPurchaseAndDividend(GameState state, PlayerState player, Square square, Dice dice) {
        var events = new ArrayList<GameEvent>();
        if ("FL-16".equals(state.getActiveFinanceNews())) {
            var order = state.getTurnOrder();
            var index = order.indexOf(player.getUid());
            for (int i = 0; i < order.size(); i++) {
                var recipient = state.player(order.get((index + i) % order.size())).orElseThrow();
                if (!recipient.isOut()) events.addAll(bankDividend(state, recipient, square, dice));
            }
        } else {
            events.addAll(bankDividend(state, player, square, dice));
        }
        if (bonds.available(state)) {
            state.getPendingDecisions().add(new PendingDecision.BondOffer(player.getUid(), BondContinuation.NONE));
        }
        return List.copyOf(events);
    }

    /** Finance News can trigger this without landing on a square. */
    List<GameEvent> grandBondDraw(GameState state, PlayerState drawer, Dice dice) {
        return bonds.grandBondDraw(state, drawer, dice);
    }

    List<GameEvent> endTurn(GameState state, PlayerState player) {
        var events = new ArrayList<GameEvent>();
        events.add(new TurnEnded(player.getUid()));
        events.addAll(passTurn(state));
        return List.copyOf(events);
    }

    /**
     * Starts the next player's turn. Players with turns to miss in jail are skipped, one missed turn each (R6); a player whose last
     * missed turn passes has left jail.
     */
    static List<GameEvent> passTurn(GameState state) {
        var events = new ArrayList<GameEvent>();
        var next = state.player(nextPlayer(state)).orElseThrow();
        while (next.getMissedTurns() > 0) {
            next.setMissedTurns(next.getMissedTurns() - 1);
            if (next.getMissedTurns() == 0) {
                next.setJailExemption(true);
            }
            events.add(new TurnSkipped(next.getUid(), next.getMissedTurns()));
            state.setCurrentPlayer(next.getUid());
            next = state.player(nextPlayer(state)).orElseThrow();
        }
        state.setCurrentPlayer(next.getUid());
        state.setPhase(TurnPhase.BEFORE_ROLL);
        state.setBoughtThisTurn(false);
        events.add(new TurnStarted(next.getUid()));
        return List.copyOf(events);
    }

    /** The next player in turn order who is still in the game */
    static String nextPlayer(GameState state) {
        var order = state.getTurnOrder();
        var index = order.indexOf(state.getCurrentPlayer());
        return IntStream.rangeClosed(1, order.size())
                .mapToObj(i -> order.get((index + i) % order.size()))
                .filter(candidate -> !state.player(candidate).orElseThrow().isOut())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No players left"));
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
        return List.copyOf(events);
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
        return List.copyOf(events);
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
     * The creditor gets the player's cash and the rest of the debt is written off (R13); the player's other pending payments are
     * dropped, so their creditors get nothing. Loans are cancelled and the car goes back to the bank. If it was the player's turn,
     * the turn passes on.
     */
    List<GameEvent> declareBankruptcy(GameState state, PlayerState player) {
        var decision = (RaiseFunds) state.getPendingDecisions().removeFirst();
        state.getPendingDecisions().removeIf(pending -> pending.player().equals(player.getUid()));
        var events = new ArrayList<GameEvent>();
        if (player.getCash() > 0) {
            var creditor = decision.creditor() == null ? null : state.player(decision.creditor()).orElseThrow();
            events.add(payments.transfer(player, creditor, player.getCash(), MoneyReason.BANKRUPTCY));
        }
        player.setLoans(0);
        player.setCar(false);
        player.setOut(true);
        events.add(new PlayerBankrupt(player.getUid(), decision.creditor()));
        var properties = state.getProperties().stream().filter(property -> player.getUid().equals(property.getOwner())).toList();
        properties.forEach(property -> {
            property.setOwner(null);
            property.setMortgaged(false);
            property.setBuilt(false);
        });
        var shares = state.getShares().stream().filter(share -> player.getUid().equals(share.getOwner())).toList();
        shares.forEach(share -> share.setOwner(null));
        var returnedBonds = bonds.returnOwnedBy(state, player.getUid());
        if (!properties.isEmpty() || !shares.isEmpty() || !returnedBonds.isEmpty()) {
            events.add(new AssetsReturned(player.getUid(), properties.stream().map(PropertyState::getSquare).toList(),
                    shares.stream().map(ShareState::getId).toList(), returnedBonds));
        }
        if (player.getUid().equals(state.getCurrentPlayer())) {
            events.addAll(passTurn(state));
        }
        return List.copyOf(events);
    }
}
