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
import fi.bizhop.finanssi2.game.engine.GameEvent.TurnEnded;
import fi.bizhop.finanssi2.game.engine.GameEvent.TurnStarted;
import fi.bizhop.finanssi2.game.engine.PendingDecision.RaiseFunds;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static fi.bizhop.finanssi2.game.data.GameConstants.BAIL;
import static fi.bizhop.finanssi2.game.data.GameConstants.BOND_COUNT;
import static fi.bizhop.finanssi2.game.data.GameConstants.LOAN_REPAYMENT_SQUARE;
import static fi.bizhop.finanssi2.game.data.GameConstants.WINNING_CASH;
import static fi.bizhop.finanssi2.game.data.GameConstants.WINNING_COMPLETE_GROUPS;
import static fi.bizhop.finanssi2.game.data.GameConstants.BOND_PRICE;
import static fi.bizhop.finanssi2.game.data.GameConstants.CAR_PRICE;
import static fi.bizhop.finanssi2.game.data.GameConstants.CAR_SELL_BACK_PRICE;
import static fi.bizhop.finanssi2.game.data.GameConstants.LOAN_AMOUNT;
import static fi.bizhop.finanssi2.game.data.GameConstants.SQUARE_COUNT;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_06;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_10;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_12;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_16;
import static fi.bizhop.finanssi2.game.engine.StockTipCard.PV_01;
import static fi.bizhop.finanssi2.game.engine.StockTipCard.PV_07;
import static fi.bizhop.finanssi2.game.engine.StockTipCard.PV_25;
import static fi.bizhop.finanssi2.game.engine.StockTipCard.PV_29;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

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
            Map.entry(GameCommand.ChooseNewsDirection.class, Timing.DECISION),
            Map.entry(GameCommand.ChooseStockTipOption.class, Timing.DECISION),
            Map.entry(GameCommand.UseHeldStockTip.class, Timing.OWN_TURN),
            Map.entry(GameCommand.BidAsset.class, Timing.DECISION),
            Map.entry(GameCommand.CallShareholdersMeeting.class, Timing.BEFORE_ROLL),
            Map.entry(GameCommand.Resign.class, Timing.OWN_TURN),
            Map.entry(GameCommand.EndGame.class, Timing.OWN_TURN));

    /** Commands allowed while a decision of this type is pending, in or out of turn */
    static Set<Class<? extends GameCommand>> decisionCommands(PendingDecision decision) {
        return switch (decision) {
            case RaiseFunds ignored -> Set.of(TakeLoan.class, SellCar.class, Mortgage.class, SellBackProperty.class, SellBackShare.class, Pay.class, DeclareBankruptcy.class);
            case PendingDecision.BondOffer ignored -> Set.of(GameCommand.BuyBond.class, GameCommand.Pass.class);
            case PendingDecision.BondAuction ignored -> Set.of(GameCommand.BidBond.class);
            case PendingDecision.NewsDirection ignored -> Set.of(GameCommand.ChooseNewsDirection.class);
            case PendingDecision.StockTipChoice ignored -> Set.of(GameCommand.ChooseStockTipOption.class);
            case PendingDecision.AssetAuction ignored -> Set.of(GameCommand.BidAsset.class);
        };
    }

    // Commands without parameters, checked as they are for allowedCommands
    static final List<GameCommand> SIMPLE_COMMANDS = List.of(new Roll(), new EndTurn(), new BuyCar(), new SellCar(), new TakeLoan(),
            new RepayLoan(), new Pay(), new DeclareBankruptcy(), new GameCommand.Resign());

    static final int BRANCH_OFFICE_SQUARE = 11;
    static final int HEAD_OFFICE_FIRST_SQUARE = 35;
    static final int JAIL_SQUARE = 24;

    final GameData gameData;
    final Rules rules;
    final Payments payments = new Payments();
    final Bonds bonds;
    // Every command with every parameter value, for allowedCommands
    final List<GameCommand> candidateCommands;
    final StockTips stockTips;
    final FinanceNews financeNews;
    final ShareholdersMeetings shareholdersMeetings;
    final Bankruptcy bankruptcy;
    final CommandValidator validator;


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
                        new GameCommand.ChooseNewsDirection(false), new GameCommand.ChooseStockTipOption(""),
                        new GameCommand.UseHeldStockTip(PV_25.id()), new GameCommand.BidAsset(0), new GameCommand.EndGame()));
        var meetingCommands = gameData.groups().stream().map(group -> (GameCommand) new GameCommand.CallShareholdersMeeting(group.id(), 20_000));
        candidateCommands = Stream.of(SIMPLE_COMMANDS.stream(), propertyCommands, shareCommands, bondCommands, meetingCommands)
                .flatMap(commands -> commands)
                .toList();
        this.stockTips = new StockTips(this);
        this.financeNews = new FinanceNews(this);
        this.shareholdersMeetings = new ShareholdersMeetings(this);
        this.bankruptcy = new Bankruptcy(this);
        this.validator = new CommandValidator(this);
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
            case FINANCE_NEWS -> financeNews::financeNews;
            case STOCK_TIP -> stockTips::stockTip;
            case BRANCH_OFFICE, CONSTRUCTION -> GameEngine::notImplemented;
        };
    }

    public List<GameEvent> handle(GameState state, String playerId, GameCommand command, Dice dice) {
        validator.validate(state, playerId, command);
        var player = state.player(playerId).orElseThrow();
        var events = new ArrayList<>(switch (command) {
            case Roll ignored -> roll(state, player, dice);
            case EndTurn ignored -> endTurn(state, player, dice);
            case BuyCar ignored -> buyCar(player);
            case SellCar ignored -> sellCar(player);
            case TakeLoan ignored -> takeLoan(player);
            case RepayLoan ignored -> repayLoan(player);
            case Pay ignored -> pay(state);
            case DeclareBankruptcy ignored -> bankruptcy.declareBankruptcy(state, player, dice);
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
            case GameCommand.ChooseNewsDirection choose -> financeNews.chooseNewsDirection(state, player, choose.forward(), dice);
            case GameCommand.ChooseStockTipOption choose -> stockTips.resolveStockTipChoice(state, player, choose.option(), dice);
            case GameCommand.UseHeldStockTip use -> stockTips.useHeldStockTip(state, player, use.card(), dice);
            case GameCommand.BidAsset bid -> stockTips.bidAsset(state, player, bid.amount());
            case GameCommand.CallShareholdersMeeting meeting -> shareholdersMeetings.callShareholdersMeeting(state, player, meeting.group(), meeting.brokerageFee(), dice);
            case GameCommand.Resign ignored -> bankruptcy.resign(state, player, dice);
            case GameCommand.EndGame ignored -> throw new RuleViolation("Only the game service can end a game");
        });
        if (!state.isFinished() && state.getPendingDecisions().isEmpty()) events.addAll(checkGameEnd(state));
        return List.copyOf(events);
    }

    /** Command types ({@code type} values) the player may send right now */
    public List<String> allowedCommands(GameState state, String playerId) {
        return candidateCommands.stream()
                .filter(command -> isAllowed(state, playerId, command))
                .map(command -> command.getClass().getSimpleName())
                .distinct()
                .sorted()
                .toList();
    }

    boolean isAllowed(GameState state, String playerId, GameCommand command) {
        if (command instanceof GameCommand.ChooseStockTipOption) {
            return playerId.equals(state.actor()) && !state.getPendingDecisions().isEmpty()
                    && state.getPendingDecisions().getFirst() instanceof PendingDecision.StockTipChoice;
        }
        try {
            validator.validate(state, playerId, command);
            return true;
        } catch (RuleViolation | NotYourTurn e) {
            return false;
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
        require(property.isPresent() && player.getPlayerId().equals(property.get().getOwner()), "Not your property");
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
        if (player.getPosition() != LOAN_REPAYMENT_SQUARE && !rules.loansStopped(state)) {
            funds += rules.loansAvailable(state, player) * LOAN_AMOUNT;
        }
        var ownership = new Ownership(gameData, state);
        funds += ownership.propertiesOf(player.getPlayerId()).stream().filter(property -> !property.isMortgaged())
                .mapToInt(property -> {
                    var deed = gameData.titleDeed(property.getSquare());
                    var mortgage = rules.mortgageValue(state, deed, property);
                    var buyBack = rules.propertyBuyBack(state, deed, property);
                    return Math.max(mortgage == null ? 0 : mortgage, buyBack == null ? 0 : buyBack);
                }).sum();
        funds += ownership.sharesOf(player.getPlayerId()).stream().mapToInt(share -> rules.shareBuyBack(state, share)).sum();
        return funds;
    }

    private List<GameEvent> checkGameEnd(GameState state) {
        var active = state.getPlayers().stream().filter(player -> !player.isOut()).toList();
        var ownership = new Ownership(gameData, state);
        var eligible = active.stream().filter(player -> player.getCash() >= WINNING_CASH
                        && gameData.groups().stream().filter(group -> ownership.ownsCompleteGroup(player.getPlayerId(), group.id())).count() >= WINNING_COMPLETE_GROUPS)
                .map(PlayerState::getPlayerId).collect(Collectors.toSet());
        String winner = null;
        if (!eligible.isEmpty()) {
            var order = state.getTurnOrder();
            var currentIndex = Math.max(0, order.indexOf(state.getCurrentPlayer()));
            winner = IntStream.range(0, order.size()).mapToObj(i -> order.get((currentIndex + i) % order.size()))
                    .filter(eligible::contains).findFirst().orElseThrow();
        } else if (active.size() == 1) {
            winner = active.getFirst().getPlayerId();
        } else if (active.isEmpty()) {
            return finish(state, null);
        }
        return winner == null ? List.of() : finish(state, winner);
    }

    private List<GameEvent> finish(GameState state, @Nullable String winner) {
        var standings = state.getPlayers().stream().map(player -> {
            var properties = new Ownership(gameData, state).propertiesOf(player.getPlayerId());
            var propertyValue = properties.stream().mapToInt(property -> {
                var deed = gameData.titleDeed(property.getSquare());
                return deed.value(property.isBuilt());
            }).sum();
            var shareValue = new Ownership(gameData, state).sharesOf(player.getPlayerId()).stream().mapToInt(Share::value).sum();
            var bondValue = (int) state.getBonds().stream().filter(bond -> player.getPlayerId().equals(bond.getOwner())).count() * BOND_PRICE;
            var carValue = player.isCar() ? CAR_PRICE : 0;
            var netWorth = player.getCash() + propertyValue + shareValue + bondValue + carValue - player.getLoans() * LOAN_AMOUNT;
            var groups = gameData.groups().stream().filter(group -> new Ownership(gameData, state).ownsCompleteGroup(player.getPlayerId(), group.id()))
                    .map(group -> group.id()).toList();
            return new PlayerStanding(player.getPlayerId(), player.getCash(), netWorth, groups);
        }).toList();
        state.finish(winner, standings);
        return List.of(new GameEvent.GameEnded(winner, standings));
    }

    public List<GameEvent> endWithoutWinner(GameState state) {
        require(!state.isFinished(), "The game is finished");
        return finish(state, null);
    }

    List<GameEvent> roll(GameState state, PlayerState player, Dice dice) {
        var events = new ArrayList<GameEvent>();
        if (player.isBailRollPending()) {
            var bailDice = rollDice(dice, 2);
            var returned = bailDice.getFirst().equals(bailDice.getLast());
            events.add(new GameEvent.BailRoll(player.getPlayerId(), bailDice, returned));
            if (returned) events.add(payments.fromBank(player, BAIL, MoneyReason.STOCK_TIP));
            player.setBailRollPending(false);
        }
        var roll = rollDice(dice, rules.movementDice(state, player));
        events.add(new DiceRolled(player.getPlayerId(), roll));
        var from = player.getPosition();
        var pips = FL_06.isActive(state) ? roll.stream().mapToInt(Integer::intValue).min().orElse(0)
                : roll.stream().mapToInt(Integer::intValue).sum();
        if (FL_16.isActive(state)) pips *= 2;
        var to = moveForward(from, pips);
        player.setPosition(to);
        events.add(new PieceMoved(player.getPlayerId(), from, to));
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
        return moveTo(state, player, target, dice, true, true);
    }

    List<GameEvent> moveTo(GameState state, PlayerState player, int target, Dice dice, boolean drawFinanceNews, boolean drawStockTip) {
        var events = new ArrayList<GameEvent>();
        var from = player.getPosition();
        player.setPosition(target);
        events.add(new PieceMoved(player.getPlayerId(), from, target));
        events.addAll(land(state, player, dice, drawFinanceNews, drawStockTip));
        return List.copyOf(events);
    }

    List<GameEvent> land(GameState state, PlayerState player, Dice dice) {
        return land(state, player, dice, true);
    }

    List<GameEvent> land(GameState state, PlayerState player, Dice dice, boolean drawFinanceNews) {
        return land(state, player, dice, drawFinanceNews, true);
    }

    List<GameEvent> land(GameState state, PlayerState player, Dice dice, boolean drawFinanceNews, boolean drawStockTip) {
        var events = new ArrayList<GameEvent>();
        var square = gameData.square(player.getPosition());
        events.add(new LandedOn(player.getPlayerId(), square.number()));
        if (square.type() == SquareType.BANK_EXIT) {
            events.addAll(bankExit(state, player, square, dice, drawStockTip));
        } else if ((drawFinanceNews || square.type() != SquareType.FINANCE_NEWS)
                && (drawStockTip || square.type() != SquareType.STOCK_TIP)) {
            events.addAll(squareHandler(square.type()).land(state, player, square, dice));
        }
        return List.copyOf(events);
    }

    static List<GameEvent> notImplemented(GameState state, PlayerState player, Square square, Dice dice) {
        return List.of(new NotImplemented(player.getPlayerId(), square.number(), square.type()));
    }

    /** Charges every player still in the game in turn order from the drawer, skipping those who owe nothing */
    List<GameEvent> chargePlayers(GameState state, PlayerState drawer, ToIntFunction<PlayerState> amount, MoneyReason reason) {
        var result = new ArrayList<GameEvent>();
        for (var target : state.playersFrom(drawer.getPlayerId())) {
            if (target.isOut()) continue;
            var due = amount.applyAsInt(target);
            if (due > 0) result.addAll(payments.charge(state, target, null, List.of(new Charge(due, reason))));
        }
        return List.copyOf(result);
    }

    /** Square 1: interest on every loan. Square 36 affects the player again. */
    List<GameEvent> bankExit(GameState state, PlayerState player, Square square, Dice dice) {
        return bankExit(state, player, square, dice, true);
    }

    List<GameEvent> bankExit(GameState state, PlayerState player, Square square, Dice dice, boolean drawTip) {
        player.setJailExemption(false);
        var events = new ArrayList<GameEvent>();
        if (player.getLoans() == 0) {
            if (drawTip) events.addAll(stockTips.drawStockTip(state, player, dice));
            return List.copyOf(events);
        }
        var interest = new Charge(player.getLoans() * rules.loanInterest(state), MoneyReason.LOAN_INTEREST);
        events.addAll(payments.charge(state, player, null, List.of(interest)));
        if (drawTip) events.addAll(stockTips.drawStockTip(state, player, dice));
        return List.copyOf(events);
    }

    /** Square 34: the player rolls both dice, car or not, and the bank pays a reward by the pips */
    List<GameEvent> bankEntrance(GameState state, PlayerState player, Square square, Dice dice) {
        if (FL_10.isActive(state)) return moveTo(state, player, 1, dice);
        var roll = rollDice(dice, 2);
        return List.of(
                new BankEntranceRoll(player.getPlayerId(), roll),
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
        player.setMissedTurnsInJail(true);
        return List.of(new JailRoll(player.getPlayerId(), die, missedTurns));
    }

    /** Square 36: one die, 1–2 goes to jail. Not for a player who has left jail and not been on square 1 since. */
    List<GameEvent> goToJailChance(GameState state, PlayerState player, Square square, Dice dice) {
        if (player.isJailExemption()) {
            return List.of(new JailExempt(player.getPlayerId()));
        }
        var events = new ArrayList<GameEvent>();
        var die = dice.roll();
        var jailed = die <= 2;
        events.add(new GoToJailRoll(player.getPlayerId(), die, jailed));
        if (jailed) {
            events.addAll(moveTo(state, player, JAIL_SQUARE, dice));
        }
        return List.copyOf(events);
    }

    /** Squares 16, 28, 39 and 42: the printed dividend of the player's shares, on 39 and 42 only those of the square's class */
    List<GameEvent> bankDividend(GameState state, PlayerState player, Square square, Dice dice) {
        var shares = new Ownership(gameData, state).sharesOf(player.getPlayerId()).stream()
                .filter(share -> square.shareClass() == null || share.dividendPercent() == square.shareClass())
                .toList();
        var amount = shares.stream().mapToInt(share -> rules.bankDividend(state, share)).sum();
        if (amount == 0) {
            return List.of();
        }
        return List.of(
                new BankDividend(player.getPlayerId(), square.number(), shares.stream().map(Share::id).toList(), amount),
                payments.fromBank(player, amount, MoneyReason.BANK_DIVIDEND));
    }

    /**
     * Square 41: each other player gets the square's percent of their share capital in the groups where the player owns a property.
     * Charged one by one in turn order from the player.
     */
    List<GameEvent> playerDividend(GameState state, PlayerState player, Square square, Dice dice) {
        var ownership = new Ownership(gameData, state);
        var groups = ownership.groupsWithPropertiesOf(player.getPlayerId());
        var events = new ArrayList<GameEvent>();
        for (var shareholder : state.playersFrom(player.getPlayerId()).stream().skip(1).toList()) {
            if (shareholder.isOut()) {
                continue;
            }
            var shares = ownership.sharesOf(shareholder.getPlayerId()).stream()
                    .filter(share -> share.group() != null && groups.contains(share.group()))
                    .toList();
            var amount = rules.playerDividend(state, square, shares.stream().mapToInt(Share::value).sum());
            if (amount <= 0) {
                continue;
            }
            events.add(new PlayerDividendCharged(player.getPlayerId(), shareholder.getPlayerId(), square.number(),
                    shares.stream().map(Share::id).toList(), amount));
            events.addAll(payments.charge(state, player, shareholder.getPlayerId(), List.of(new Charge(amount, MoneyReason.PLAYER_DIVIDEND))));
        }
        return List.copyOf(events);
    }

    /** Square 35: the square's percent of the share capital outside complete groups, fund shares included */
    List<GameEvent> shareCrash(GameState state, PlayerState player, Square square, Dice dice) {
        var amount = rules.shareCrash(state, square, new Ownership(gameData, state).shareCapitalOutsideCompleteGroups(player.getPlayerId()));
        if (amount == 0) {
            return List.of();
        }
        return payments.charge(state, player, null, List.of(new Charge(amount, MoneyReason.SHARE_CRASH)));
    }

    /** Square 46: dividend on all shares as on 16 and 28; the bond purchase comes with step 08 */
    List<GameEvent> bondPurchaseAndDividend(GameState state, PlayerState player, Square square, Dice dice) {
        var events = new ArrayList<GameEvent>();
        if (FL_16.isActive(state)) {
            for (var recipient : state.playersFrom(player.getPlayerId())) {
                if (!recipient.isOut()) events.addAll(bankDividend(state, recipient, square, dice));
            }
        } else {
            events.addAll(bankDividend(state, player, square, dice));
        }
        if (bonds.available(state)) {
            state.enqueueDecision(new PendingDecision.BondOffer(player.getPlayerId(), BondContinuation.NONE));
        }
        return List.copyOf(events);
    }

    /** Finance News can trigger this without landing on a square. */
    List<GameEvent> grandBondDraw(GameState state, PlayerState drawer, Dice dice) {
        return bonds.grandBondDraw(state, drawer, dice);
    }

    List<GameEvent> endTurn(GameState state, PlayerState player, Dice dice) {
        var events = new ArrayList<GameEvent>();
        events.add(new TurnEnded(player.getPlayerId()));
        events.addAll(passTurn(state, dice));
        return List.copyOf(events);
    }

    /**
     * Starts the next player's turn. Players with turns to miss in jail are skipped, one missed turn each (R6); a player whose last
     * missed turn passes has left jail.
     */
    List<GameEvent> passTurn(GameState state, Dice dice) {
        var events = new ArrayList<GameEvent>();
        var next = state.player(nextPlayer(state)).orElseThrow();
        while (next.getMissedTurns() > 0) {
            next.setMissedTurns(next.getMissedTurns() - 1);
            if (next.getMissedTurns() == 0) {
                if (next.isMissedTurnsInJail()) next.setJailExemption(true);
                next.setMissedTurnsInJail(false);
                if (PV_29.heldBy(next)) next.setTransportNewsDue(true);
            }
            events.add(new TurnSkipped(next.getPlayerId(), next.getMissedTurns()));
            state.setCurrentPlayer(next.getPlayerId());
            next = state.player(nextPlayer(state)).orElseThrow();
        }
        state.setCurrentPlayer(next.getPlayerId());
        state.setPhase(TurnPhase.BEFORE_ROLL);
        next.setNoMovementRollThisTurn(false);
        state.setBoughtThisTurn(false);
        events.add(new TurnStarted(next.getPlayerId()));
        if (next.isTransportNewsDue()) {
            next.setTransportNewsDue(false);
            next.getHeldStockTips().remove(PV_29.id());
            state.getStockTipDeck().addLast(PV_29.id());
            events.add(new GameEvent.StockTipUsed(next.getPlayerId(), PV_29.id()));
            events.addAll(financeNews.financeNews(state, next, gameData.square(next.getPosition()), dice));
        }
        return List.copyOf(events);
    }

    /** The next player in turn order who is still in the game */
    static String nextPlayer(GameState state) {
        var others = state.playersFrom(state.getCurrentPlayer()).stream().skip(1);
        return Stream.concat(others, Stream.of(state.current()))
                .filter(candidate -> !candidate.isOut())
                .map(PlayerState::getPlayerId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No players left"));
    }

    /** Landing on a property: rent to its owner, if another player owns it and it is not mortgaged */
    List<GameEvent> property(GameState state, PlayerState player, Square square, Dice dice) {
        var property = state.property(square.number());
        var owner = property.getOwner();
        if (owner == null || owner.equals(player.getPlayerId()) || property.isMortgaged()) {
            return List.of();
        }
        var deed = gameData.titleDeed(square.number());
        var completeGroup = new Ownership(gameData, state).ownsCompleteGroup(owner, deed.group());
        var rent = rules.rent(state, deed, property, player, completeGroup);
        if (rent == 0) {
            return List.of();
        }
        var events = new ArrayList<GameEvent>();
        events.add(new RentCharged(player.getPlayerId(), owner, square.number(), rent, completeGroup));
        events.addAll(payments.charge(state, player, owner, List.of(new Charge(rent, MoneyReason.RENT))));
        return List.copyOf(events);
    }

    List<GameEvent> buyProperty(GameState state, PlayerState player, int square) {
        var payment = payments.toBank(player, rules.propertyPrice(state, deed(square)), MoneyReason.PROPERTY_PURCHASE);
        state.property(square).setOwner(player.getPlayerId());
        state.setBoughtThisTurn(true);
        var events = new ArrayList<GameEvent>();
        events.add(payment);
        events.add(new PropertyBought(player.getPlayerId(), square));
        events.addAll(stockTips.consumePurchaseCertificate(state, player, true));
        return List.copyOf(events);
    }

    List<GameEvent> buyShare(GameState state, PlayerState player, String id) {
        var payment = payments.toBank(player, rules.sharePrice(state, share(id)), MoneyReason.SHARE_PURCHASE);
        state.share(id).setOwner(player.getPlayerId());
        state.setBoughtThisTurn(true);
        var events = new ArrayList<GameEvent>();
        events.add(payment);
        events.add(new ShareBought(player.getPlayerId(), id));
        events.addAll(stockTips.consumePurchaseCertificate(state, player, false));
        return List.copyOf(events);
    }

    List<GameEvent> mortgage(GameState state, PlayerState player, int square) {
        var property = state.property(square);
        var value = Objects.requireNonNull(rules.mortgageValue(state, deed(square), property));
        property.setMortgaged(true);
        return List.of(new PropertyMortgaged(player.getPlayerId(), square), payments.fromBank(player, value, MoneyReason.MORTGAGE));
    }

    List<GameEvent> redeem(GameState state, PlayerState player, int square) {
        var property = state.property(square);
        var payment = payments.toBank(player, rules.redemptionPrice(state, deed(square), property), MoneyReason.REDEMPTION);
        property.setMortgaged(false);
        return List.of(payment, new PropertyRedeemed(player.getPlayerId(), square));
    }

    List<GameEvent> sellBackProperty(GameState state, PlayerState player, int square) {
        var property = state.property(square);
        var value = Objects.requireNonNull(rules.propertyBuyBack(state, deed(square), property));
        property.setOwner(null);
        property.setBuilt(false);
        return List.of(new PropertySoldBack(player.getPlayerId(), square), payments.fromBank(player, value, MoneyReason.PROPERTY_SALE));
    }

    List<GameEvent> sellBackShare(GameState state, PlayerState player, String id) {
        state.share(id).setOwner(null);
        return List.of(new ShareSoldBack(player.getPlayerId(), id),
                payments.fromBank(player, rules.shareBuyBack(state, share(id)), MoneyReason.SHARE_SALE));
    }

    List<GameEvent> build(GameState state, PlayerState player, List<Integer> squares) {
        var events = new ArrayList<GameEvent>();
        for (var square : squares) {
            var deed = deed(square);
            events.add(payments.toBank(player, rules.buildingPrice(state, deed), MoneyReason.CONSTRUCTION));
            state.property(square).setBuilt(true);
            events.add(new PropertyBuilt(player.getPlayerId(), square, deed.building().industrial()));
        }
        var permit = buildingPermit(state, player);
        if (permit != null) events.addAll(stockTips.consumeHeldTip(state, player, permit.id()));
        return List.copyOf(events);
    }

    @Nullable StockTipCard buildingPermit(GameState state, PlayerState player) {
        var onProject = rules.canBuildAtProject(state, player);
        var hasProjectPermit = PV_01.heldBy(player);
        var hasSinglePermit = PV_07.heldBy(player);
        if (FL_12.isActive(state)) {
            if (onProject && hasProjectPermit) return PV_01;
            return hasSinglePermit ? PV_07 : null;
        }
        if (state.getPhase() == TurnPhase.AFTER_ROLL) {
            if (onProject && hasProjectPermit) return PV_01;
            return hasSinglePermit ? PV_07 : null;
        }
        return !onProject && hasSinglePermit ? PV_07 : null;
    }

    List<GameEvent> buyCar(PlayerState player) {
        player.setCar(true);
        return List.of(payments.toBank(player, CAR_PRICE, MoneyReason.CAR_PURCHASE), new CarBought(player.getPlayerId()));
    }

    List<GameEvent> sellCar(PlayerState player) {
        player.setCar(false);
        return List.of(new CarSold(player.getPlayerId()), payments.fromBank(player, CAR_SELL_BACK_PRICE, MoneyReason.CAR_SALE));
    }

    List<GameEvent> takeLoan(PlayerState player) {
        player.setLoans(player.getLoans() + 1);
        return List.of(new LoanTaken(player.getPlayerId(), player.getLoans()), payments.fromBank(player, LOAN_AMOUNT, MoneyReason.LOAN));
    }

    List<GameEvent> repayLoan(PlayerState player) {
        var payment = payments.toBank(player, LOAN_AMOUNT, MoneyReason.LOAN_REPAYMENT);
        player.setLoans(player.getLoans() - 1);
        return List.of(payment, new LoanRepaid(player.getPlayerId(), player.getLoans()));
    }

    List<GameEvent> pay(GameState state) {
        var decision = (RaiseFunds) state.popDecision();
        return payments.settle(state, decision);
    }
}
