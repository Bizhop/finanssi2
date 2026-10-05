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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
                        new GameCommand.ChooseNewsDirection(false), new GameCommand.ChooseStockTipOption(""),
                        new GameCommand.UseHeldStockTip("PV-25"), new GameCommand.BidAsset(0), new GameCommand.EndGame()));
        var meetingCommands = gameData.groups().stream().map(group -> (GameCommand) new GameCommand.CallShareholdersMeeting(group.id(), 20_000));
        candidateCommands = Stream.of(SIMPLE_COMMANDS.stream(), propertyCommands, shareCommands, bondCommands, meetingCommands)
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
            case STOCK_TIP -> this::stockTip;
            case BRANCH_OFFICE, CONSTRUCTION -> GameEngine::notImplemented;
        };
    }

    public List<GameEvent> handle(GameState state, String uid, GameCommand command, Dice dice) {
        validate(state, uid, command);
        var player = state.player(uid).orElseThrow();
        var events = new ArrayList<>(switch (command) {
            case Roll ignored -> roll(state, player, dice);
            case EndTurn ignored -> endTurn(state, player, dice);
            case BuyCar ignored -> buyCar(player);
            case SellCar ignored -> sellCar(player);
            case TakeLoan ignored -> takeLoan(player);
            case RepayLoan ignored -> repayLoan(player);
            case Pay ignored -> pay(state);
            case DeclareBankruptcy ignored -> declareBankruptcy(state, player, dice);
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
            case GameCommand.ChooseStockTipOption choose -> resolveStockTipChoice(state, player, choose.option(), dice);
            case GameCommand.UseHeldStockTip use -> useHeldStockTip(state, player, use.card(), dice);
            case GameCommand.BidAsset bid -> bidAsset(state, player, bid.amount());
            case GameCommand.CallShareholdersMeeting meeting -> callShareholdersMeeting(state, player, meeting.group(), meeting.brokerageFee(), dice);
            case GameCommand.Resign ignored -> resign(state, player, dice);
            case GameCommand.EndGame ignored -> throw new RuleViolation("Only the game service can end a game");
        });
        if (!state.isFinished() && state.getPendingDecisions().isEmpty()) events.addAll(checkGameEnd(state));
        return List.copyOf(events);
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
        if (command instanceof GameCommand.ChooseStockTipOption) {
            return uid.equals(state.actor()) && !state.getPendingDecisions().isEmpty()
                    && state.getPendingDecisions().getFirst() instanceof PendingDecision.StockTipChoice;
        }
        try {
            validate(state, uid, command);
            return true;
        } catch (RuleViolation | NotYourTurn e) {
            return false;
        }
    }

    /** Throws if the command is not allowed; changes nothing */
    void validate(GameState state, String uid, GameCommand command) {
        require(!state.isFinished(), "The game is finished");
        require(!(command instanceof GameCommand.EndGame), "Only the game service can end a game");
        if (command instanceof GameCommand.Resign) {
            var player = state.player(uid).orElseThrow();
            require(!player.isOut(), "You are already out of the game");
            require(state.getPendingDecisions().isEmpty()
                            || state.getPendingDecisions().getFirst() instanceof RaiseFunds funds && funds.player().equals(uid),
                    "Resolve the pending decision before resigning");
            return;
        }
        if (!uid.equals(state.actor())) {
            throw new NotYourTurn();
        }
        var player = state.player(uid).orElseThrow();
        var pending = state.getPendingDecisions();
        validateTiming(state, command);
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
                require(!rules.propertiesTradingStopped(state) || hasPurchaseCertificate(player), "The bank is not selling properties");
                require(player.getCash() >= rules.propertyPrice(state, deed), "Not enough cash");
            }
            case BuyShare buy -> {
                var share = share(buy.share());
                requirePurchase(state, player);
                require(state.share(share.id()).getOwner() == null, "The share is not for sale");
                require(!rules.shareTradingStopped(state) || hasPurchaseCertificate(player), "The bank is not selling shares");
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
                if ("PV-07".equals(buildingPermit(state, player)))
                    require(build.squares().size() == 1, "This permit covers one property");
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
            case GameCommand.ChooseStockTipOption choose -> {
                require(pending.getFirst() instanceof PendingDecision.StockTipChoice, "No Stock Tip choice to make");
                require(((PendingDecision.StockTipChoice) pending.getFirst()).options().contains(choose.option()), "Invalid Stock Tip choice");
            }
            case GameCommand.UseHeldStockTip use -> require(use.card().equals("PV-25")
                    && player.getHeldStockTips().contains(use.card()) && state.getPhase() == TurnPhase.BEFORE_ROLL,
                    "This held Stock Tip cannot be used now");
            case GameCommand.BidAsset bid -> {
                require(pending.getFirst() instanceof PendingDecision.AssetAuction, "No asset auction is running");
                var auction = (PendingDecision.AssetAuction) pending.getFirst();
                require(bid.amount() >= 0 && bid.amount() % 500 == 0 && bid.amount() <= player.getCash()
                                && (bid.amount() == 0 || bid.amount() >= auction.minimumBid()),
                        "Invalid asset auction bid");
            }
            case GameCommand.CallShareholdersMeeting meeting -> {
                var groupExists = gameData.groups().stream().anyMatch(group -> group.id().equals(meeting.group()));
                require(groupExists, "Unknown business group");
                require(player.getPosition() >= HEAD_OFFICE_FIRST_SQUARE && player.getPosition() <= SQUARE_COUNT,
                        "Shareholders' meetings are held inside the bank");
                require(rules.shareholdersMeetingsAllowed(state), "Shareholders' meetings are stopped by Finance News");
                require(meeting.brokerageFee() >= 20_000 && meeting.brokerageFee() <= 120_000
                                && meeting.brokerageFee() % 10_000 == 0, "Invalid brokerage fee");
                require(ownershipOwnsGroupAsset(state, player.getUid(), meeting.group()), "You own no asset in this group");
                require(groupHasOtherOwner(state, player.getUid(), meeting.group()), "Other players own no assets in this group");
                // House rule (the default): a meeting takes over all of the group from the other owners, so nothing may be left in the bank
                require(state.getSettings().shareholdersMeeting() != ShareholdersMeeting.ALL_ASSETS_BOUGHT || groupBoughtFromBank(state, meeting.group()),
                        "Every property and share of the group must be bought from the bank first");
                require(player.getCash() >= shareholdersMeetingTakeoverSum(state, meeting.group(), player.getUid())
                                + meeting.brokerageFee(), "Not enough cash for the takeover and brokerage fee");
            }
            case GameCommand.Resign ignored -> throw new IllegalStateException("Resign validated above");
            case GameCommand.EndGame ignored -> throw new RuleViolation("Only the game service can end a game");
        }
    }

    static void validateTiming(GameState state, GameCommand command) {
        var pending = state.getPendingDecisions();
        if (!pending.isEmpty()) {
            require(decisionCommands(pending.getFirst()).contains(command.getClass()), "Resolve the pending decision first");
            return;
        }
        var phase = state.getPhase();
        if (command instanceof Build && phase == TurnPhase.AFTER_ROLL) {
            var player = state.current();
            if (player.isNoMovementRollThisTurn() && player.getPosition() == 17) return;
            if (player.getHeldStockTips().contains("PV-07")
                    || player.getHeldStockTips().contains("PV-01") && (player.getPosition() == 17 || player.getPosition() == 40)) return;
        }
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
        if (player.getPosition() != REPAY_LOAN_SQUARE && !rules.loansStopped(state)) {
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

    private List<GameEvent> checkGameEnd(GameState state) {
        var active = state.getPlayers().stream().filter(player -> !player.isOut()).toList();
        var ownership = new Ownership(gameData, state);
        var eligible = active.stream().filter(player -> player.getCash() >= 1_000_000
                        && gameData.groups().stream().filter(group -> ownership.ownsCompleteGroup(player.getUid(), group.id())).count() >= 2)
                .map(PlayerState::getUid).collect(java.util.stream.Collectors.toSet());
        String winner = null;
        if (!eligible.isEmpty()) {
            var order = state.getTurnOrder();
            var currentIndex = Math.max(0, order.indexOf(state.getCurrentPlayer()));
            winner = IntStream.range(0, order.size()).mapToObj(i -> order.get((currentIndex + i) % order.size()))
                    .filter(eligible::contains).findFirst().orElseThrow();
        } else if (active.size() == 1) {
            winner = active.getFirst().getUid();
        } else if (active.isEmpty()) {
            return finish(state, null);
        }
        return winner == null ? List.of() : finish(state, winner);
    }

    private List<GameEvent> finish(GameState state, String winner) {
        var standings = state.getPlayers().stream().map(player -> {
            var properties = new Ownership(gameData, state).propertiesOf(player.getUid());
            var propertyValue = properties.stream().mapToInt(property -> {
                var deed = gameData.titleDeed(property.getSquare());
                return deed.price() + (property.isBuilt() ? deed.building().price() : 0);
            }).sum();
            var shareValue = new Ownership(gameData, state).sharesOf(player.getUid()).stream().mapToInt(Share::value).sum();
            var bondValue = (int) state.getBonds().stream().filter(bond -> player.getUid().equals(bond.getOwner())).count() * BOND_PRICE;
            var carValue = player.isCar() ? CAR_PRICE : 0;
            var netWorth = player.getCash() + propertyValue + shareValue + bondValue + carValue - player.getLoans() * LOAN_AMOUNT;
            var groups = gameData.groups().stream().filter(group -> new Ownership(gameData, state).ownsCompleteGroup(player.getUid(), group.id()))
                    .map(group -> group.id()).toList();
            return new PlayerStanding(player.getUid(), player.getCash(), netWorth, groups);
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
            events.add(new GameEvent.BailRoll(player.getUid(), bailDice, returned));
            if (returned) events.add(payments.fromBank(player, 30_000, MoneyReason.STOCK_TIP));
            player.setBailRollPending(false);
        }
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
        return moveTo(state, player, target, dice, true, true);
    }

    List<GameEvent> moveTo(GameState state, PlayerState player, int target, Dice dice, boolean drawFinanceNews, boolean drawStockTip) {
        var events = new ArrayList<GameEvent>();
        var from = player.getPosition();
        player.setPosition(target);
        events.add(new PieceMoved(player.getUid(), from, target));
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
        events.add(new LandedOn(player.getUid(), square.number()));
        if (square.type() == SquareType.BANK_EXIT) {
            events.addAll(bankExit(state, player, square, dice, drawStockTip));
        } else if ((drawFinanceNews || square.type() != SquareType.FINANCE_NEWS)
                && (drawStockTip || square.type() != SquareType.STOCK_TIP)) {
            events.addAll(squareHandler(square.type()).land(state, player, square, dice));
        }
        return List.copyOf(events);
    }

    List<GameEvent> stockTip(GameState state, PlayerState player, Square square, Dice dice) {
        return drawStockTip(state, player, dice);
    }

    List<GameEvent> drawStockTip(GameState state, PlayerState player, Dice dice) {
        if (state.getStockTipDeck().isEmpty()) return List.of();
        var id = state.getStockTipDeck().removeFirst();
        var held = List.of("PV-01", "PV-02", "PV-07", "PV-08", "PV-25", "PV-29").contains(id);
        if (held) player.getHeldStockTips().add(id);
        else state.getStockTipDeck().addLast(id);
        var events = new ArrayList<GameEvent>();
        events.add(new GameEvent.StockTipDrawn(player.getUid(), id, held));
        if (id.equals("PV-29")) {
            player.setMissedTurns(2);
            player.setMissedTurnsInJail(false);
        }
        if (!held) events.addAll(resolveStockTip(state, player, id, dice));
        return List.copyOf(events);
    }

    List<GameEvent> useHeldStockTip(GameState state, PlayerState player, String card, Dice dice) {
        var events = new ArrayList<GameEvent>(consumeHeldTip(state, player, card));
        events.addAll(moveTo(state, player, 17, dice));
        player.setNoMovementRollThisTurn(true);
        state.setPhase(TurnPhase.AFTER_ROLL);
        return List.copyOf(events);
    }

    private List<GameEvent> consumePurchaseCertificate(GameState state, PlayerState player, boolean property) {
        if (property ? !rules.propertiesTradingStopped(state) : !rules.shareTradingStopped(state)) return List.of();
        var card = player.getHeldStockTips().stream().filter(c -> c.equals("PV-02") || c.equals("PV-08")).findFirst();
        return card.map(value -> consumeHeldTip(state, player, value)).orElseGet(List::of);
    }

    private List<GameEvent> consumeHeldTip(GameState state, PlayerState player, String card) {
        if (!player.getHeldStockTips().remove(card)) return List.of();
        state.getStockTipDeck().addLast(card);
        return List.of(new GameEvent.StockTipUsed(player.getUid(), card));
    }

    private boolean hasPurchaseCertificate(PlayerState player) {
        return player.getHeldStockTips().stream().anyMatch(card -> card.equals("PV-02") || card.equals("PV-08"));
    }

    List<GameEvent> resolveStockTip(GameState state, PlayerState player, String card, Dice dice) {
        var events = new ArrayList<GameEvent>();
        var ownership = new Ownership(gameData, state);
        switch (card) {
            case "PV-03", "PV-09" -> issueFundShare(state, player, card.equals("PV-03") ? 20 : 25, dice, events);
            case "PV-04" -> {
                if (player.isCar()) { player.setCar(false); events.add(new GameEvent.CarLost(player.getUid())); }
            }
            case "PV-05" -> {
                events.addAll(moveTo(state, player, 10, dice));
                if (hasOtherActivePlayer(state, player.getUid())) {
                    var left = relativePlayer(state, player.getUid(), 1);
                    events.addAll(moveTo(state, left, 10, dice));
                }
            }
            case "PV-06" -> events.addAll(chargeStockTipPlayers(state, player, p -> p.isCar() ? 15_000 : 0));
            case "PV-10" -> queueChoice(state, player, card, fireOptions(ownership.builtPropertiesOf(player.getUid())), events);
            case "PV-11" -> {
                var amount = (int) (ownership.plantCount(player.getUid()) * 40_000 + ownership.otherBuildingCount(player.getUid()) * 20_000);
                if (amount > 0) events.addAll(payments.charge(state, player, null, List.of(new Charge(amount, MoneyReason.STOCK_TIP))));
            }
            case "PV-12" -> {
                if (player.isCar()) events.addAll(moveTo(state, player, 10, dice));
                else events.addAll(extraRoll(state, player, 1, dice));
            }
            case "PV-13", "PV-14", "PV-15" -> events.addAll(playerDividend(state, player, gameData.square(41), dice));
            case "PV-16" -> events.addAll(bondOneWins(state, player));
            case "PV-17" -> queueChoice(state, player, card, gameData.titleDeeds().stream().map(d -> Integer.toString(d.square())).toList(), events);
            case "PV-18" -> events.addAll(player.getPosition() == 20
                    ? moveToWithoutLanding(state, player, 34) : moveTo(state, player, 20, dice, true, false));
            case "PV-19" -> { events.addAll(moveTo(state, player, 8, dice)); events.addAll(financeNews(state, player, gameData.square(player.getPosition()), dice)); }
            case "PV-20" -> { events.addAll(moveTo(state, player, 34, dice)); events.addAll(financeNews(state, player, gameData.square(player.getPosition()), dice)); }
            case "PV-21" -> events.addAll(neighbourDividend(state, player));
            case "PV-22" -> { if (player.getPosition() == 1) events.addAll(allShareDividend(state, player)); }
            case "PV-23" -> events.addAll(allShareDividend(state, player));
            case "PV-24" -> freeBond(state, player, events);
            case "PV-26" -> queueChoice(state, player, card, List.of("PAY", "PASS"), events);
            case "PV-27", "PV-28" -> events.addAll(player.getLoans() > 0
                    ? moveTo(state, player, 43, dice) : moveTo(state, player, 34, dice));
            case "PV-30" -> {
                if (player.getCash() < 30_000) events.addAll(goToJailFromTip(state, player, dice));
                else queueChoice(state, player, card, List.of("JAIL", "BAIL"), events);
            }
            case "PV-31" -> {
                var roll = rollDice(dice, 2);
                events.add(new DiceRolled(player.getUid(), roll));
                if (roll.stream().mapToInt(Integer::intValue).sum() <= 7) {
                    var eligible = ownership.propertiesOf(player.getUid()).stream()
                            .filter(p -> !p.isMortgaged() && !ownership.ownsCompleteGroup(player.getUid(), gameData.titleDeed(p.getSquare()).group()))
                            .map(p -> Integer.toString(p.getSquare())).toList();
                    if (!eligible.isEmpty()) queueChoice(state, player, card, eligible, events);
                }
            }
            case "PV-32", "PV-33" -> events.addAll(financeNews(state, player, gameData.square(player.getPosition()), dice));
            case "PV-34" -> { events.add(payments.fromBank(player, 50_000, MoneyReason.STOCK_TIP)); events.addAll(moveTo(state, player, 1, dice, true, false)); }
            case "PV-35" -> {
                if (hasOtherActivePlayer(state, player.getUid()))
                    queueChoice(state, player, card, transferableAssets(state, player, ownership), events);
            }
            case "PV-36" -> {
                if (state.getTurnOrder().stream().filter(uid -> !state.player(uid).orElseThrow().isOut()).count() > 2)
                    queueChoice(state, player, card, transferableAssets(state, player, ownership), events);
            }
            case "PV-37" -> {
                events.add(payments.fromBank(player, roundUp500(player.getCash(), 5), MoneyReason.STOCK_TIP));
                events.addAll(financeNews(state, player, gameData.square(player.getPosition()), dice));
            }
            case "PV-38" -> {
                var swaps = shareSwapOptions(state, player, ownership);
                if (!swaps.isEmpty()) {
                    swaps.add("PASS");
                    queueChoice(state, player, card, swaps, events);
                }
            }
            case "PV-39", "PV-40" -> returnSharesByPercent(state, player, card.equals("PV-39") ? 30 : 40, events);
            case "PV-41" -> events.addAll(payPerShareClass(state, player));
            default -> throw new IllegalArgumentException("No Stock Tip effect for " + card);
        }
        return List.copyOf(events);
    }

    private void queueChoice(GameState state, PlayerState player, String card, List<String> options, List<GameEvent> events) {
        if (!options.isEmpty()) state.getPendingDecisions().add(new PendingDecision.StockTipChoice(player.getUid(), card, options));
    }

    List<GameEvent> resolveStockTipChoice(GameState state, PlayerState player, String option, Dice dice) {
        var decision = (PendingDecision.StockTipChoice) state.getPendingDecisions().removeFirst();
        var events = new ArrayList<GameEvent>();
        events.add(new GameEvent.StockTipUsed(player.getUid(), decision.card()));
        switch (decision.card()) {
            case "PV-10" -> {
                var squares = option.split(",");
                var burned = java.util.Arrays.stream(squares).map(Integer::parseInt).toList();
                for (var square : burned) state.property(square).setBuilt(false);
                events.add(new GameEvent.BuildingsBurned(player.getUid(), burned));
                if (squares.length > 0) {
                    var die = dice.roll();
                    events.add(new DiceRolled(player.getUid(), List.of(die)));
                    events.add(payments.fromBank(player, die * 10_000, MoneyReason.STOCK_TIP));
                }
            }
            case "PV-17" -> events.addAll(moveTo(state, player, Integer.parseInt(option), dice));
            case "PV-24" -> {
                var parts = option.split(":");
                if (parts[0].equals("B")) {
                    var bond = state.bond(Integer.parseInt(parts[1]));
                    if (bond.getOwner() == null) {
                        bond.setOwner(player.getUid());
                        events.add(new GameEvent.BondGranted(player.getUid(), bond.getNumber()));
                    }
                }
                else {
                    var donor = state.player(parts[1]).orElseThrow();
                    var bond = state.bond(Integer.parseInt(parts[2]));
                    if (!donor.isOut() && donor.getUid().equals(bond.getOwner())) {
                        bond.setOwner(player.getUid());
                        events.add(new GameEvent.BondTransferred(donor.getUid(), player.getUid(), bond.getNumber()));
                    }
                }
            }
            case "PV-26" -> {
                if (option.equals("PAY") && player.getCash() >= 25_000) {
                    events.add(payments.toBank(player, 25_000, MoneyReason.STOCK_TIP));
                    events.addAll(moveTo(state, player, 34, dice));
                }
            }
            case "PV-30" -> {
                if (option.equals("JAIL") || player.getCash() < 30_000) events.addAll(goToJailFromTip(state, player, dice));
                else {
                    events.add(payments.toBank(player, 30_000, MoneyReason.STOCK_TIP));
                    player.setBailRollPending(true);
                }
            }
            case "PV-31" -> {
                var square = Integer.parseInt(option);
                var property = state.property(square);
                if (!player.getUid().equals(property.getOwner())) break;
                var deed = gameData.titleDeed(square);
                var price = deed.price() + (property.isBuilt() ? deed.building().price() : 0);
                if (property.isMortgaged()) price -= rules.redemptionPrice(state, deed, property);
                property.setOwner(null);
                property.setBuilt(false);
                property.setMortgaged(false);
                events.add(new GameEvent.PropertySoldBack(player.getUid(), square));
                events.add(payments.fromBank(player, Math.max(price, 0), MoneyReason.PROPERTY_SALE));
            }
            case "PV-35" -> {
                if (!player.getUid().equals(assetOwner(state, option))) break;
                var receiver = relativePlayer(state, player.getUid(), 1);
                transferAsset(state, player, receiver, option);
                events.addAll(payments.charge(state, receiver, player.getUid(), List.of(new Charge(10_000, MoneyReason.STOCK_TIP))));
                events.add(new GameEvent.AssetTransferred(player.getUid(), receiver.getUid(), option));
            }
            case "PV-36" -> {
                if (player.getUid().equals(assetOwner(state, option))) startCompulsoryAuction(state, player, option, events);
            }
            case "PV-38" -> {
                if (!option.equals("PASS")) {
                    var parts = option.split("\\|");
                    var otherShare = state.share(parts[2]);
                    var ownShare = state.share(parts[0]);
                    if (!player.getUid().equals(ownShare.getOwner()) || !parts[1].equals(otherShare.getOwner())) break;
                    ownShare.setOwner(parts[1]);
                    otherShare.setOwner(player.getUid());
                    events.add(new GameEvent.SharesSwapped(player.getUid(), parts[0], parts[1], parts[2]));
                }
            }
            default -> throw new IllegalArgumentException("No Stock Tip choice effect for " + decision.card());
        }
        return List.copyOf(events);
    }

    private List<GameEvent> moveToWithoutLanding(GameState state, PlayerState player, int target) {
        var from = player.getPosition();
        player.setPosition(target);
        return List.of(new PieceMoved(player.getUid(), from, target), new LandedOn(player.getUid(), target));
    }

    private List<GameEvent> extraRoll(GameState state, PlayerState player, int diceCount, Dice dice) {
        var roll = rollDice(dice, diceCount);
        var pips = roll.stream().mapToInt(Integer::intValue).sum();
        if ("FL-16".equals(state.getActiveFinanceNews())) pips *= 2;
        var from = player.getPosition();
        var to = moveForward(from, pips);
        player.setPosition(to);
        var events = new ArrayList<GameEvent>();
        events.add(new DiceRolled(player.getUid(), roll));
        events.add(new PieceMoved(player.getUid(), from, to));
        events.addAll(land(state, player, dice));
        return List.copyOf(events);
    }

    private void issueFundShare(GameState state, PlayerState player, int dividendPercent, Dice dice, List<GameEvent> events) {
        var share = gameData.fundShares().stream().filter(s -> s.dividendPercent() == dividendPercent).findFirst().orElseThrow();
        var currentOwner = state.share(share.id()).getOwner();
        if (currentOwner == null) {
            state.share(share.id()).setOwner(player.getUid());
            events.add(new GameEvent.ShareIssued(player.getUid(), share.id()));
        } else if (!currentOwner.equals(player.getUid())) {
            var roll = rollDice(dice, 2);
            events.add(new DiceRolled(player.getUid(), roll));
            if (roll.stream().mapToInt(Integer::intValue).sum() >= 7) {
                state.share(share.id()).setOwner(player.getUid());
                events.add(new GameEvent.ShareTaken(player.getUid(), currentOwner, share.id()));
            }
        }
    }

    private List<GameEvent> allShareDividend(GameState state, PlayerState player) {
        var events = new ArrayList<GameEvent>();
        var shares = new Ownership(gameData, state).sharesOf(player.getUid());
        var amount = shares.stream().mapToInt(share -> rules.bankDividend(state, share)).sum();
        if (amount == 0) return List.of();
        events.add(new GameEvent.BankDividend(player.getUid(), player.getPosition(), shares.stream().map(Share::id).toList(), amount));
        events.add(payments.fromBank(player, amount, MoneyReason.BANK_DIVIDEND));
        return List.copyOf(events);
    }

    private List<GameEvent> neighbourDividend(GameState state, PlayerState drawer) {
        if (!hasOtherActivePlayer(state, drawer.getUid())) return List.of();
        var neighbour = relativePlayer(state, drawer.getUid(), -1);
        var groups = new Ownership(gameData, state).groupsWithPropertiesOf(drawer.getUid());
        var shares = new Ownership(gameData, state).sharesOf(neighbour.getUid()).stream()
                .filter(share -> share.group() != null && groups.contains(share.group())).toList();
        var multiplier = "FL-16".equals(state.getActiveFinanceNews()) ? 2 : 1;
        var amount = shares.stream().mapToInt(share -> share.dividend() * multiplier).sum();
        if (amount == 0) return List.of();
        var events = new ArrayList<GameEvent>();
        events.add(new GameEvent.StockTipDividendCharged(drawer.getUid(), neighbour.getUid(),
                shares.stream().map(Share::id).toList(), amount));
        events.addAll(payments.charge(state, drawer, neighbour.getUid(), List.of(new Charge(amount, MoneyReason.PLAYER_DIVIDEND))));
        return List.copyOf(events);
    }

    private void freeBond(GameState state, PlayerState player, List<GameEvent> events) {
        var bank = state.getBonds().stream().filter(bond -> bond.getOwner() == null).map(b -> "B:" + b.getNumber()).toList();
        if (!bank.isEmpty()) { queueChoice(state, player, "PV-24", bank, events); return; }
        if (!hasOtherActivePlayer(state, player.getUid())) return;
        var neighbour = relativePlayer(state, player.getUid(), 1);
        var owned = state.getBonds().stream().filter(bond -> neighbour.getUid().equals(bond.getOwner()))
                .map(b -> "P:" + neighbour.getUid() + ":" + b.getNumber()).toList();
        queueChoice(state, player, "PV-24", owned, events);
    }

    private List<GameEvent> goToJailFromTip(GameState state, PlayerState player, Dice dice) {
        var events = new ArrayList<GameEvent>();
        events.addAll(moveTo(state, player, JAIL_SQUARE, dice));
        return List.copyOf(events);
    }

    private List<String> transferableAssets(GameState state, PlayerState player, Ownership ownership) {
        var assets = new ArrayList<String>();
        ownership.propertiesOf(player.getUid()).stream()
                .filter(p -> !ownership.ownsCompleteGroup(player.getUid(), gameData.titleDeed(p.getSquare()).group()))
                .map(p -> "P:" + p.getSquare()).forEach(assets::add);
        ownership.sharesOf(player.getUid()).stream()
                .filter(s -> !ownership.ownsCompleteGroup(player.getUid(), s.group()))
                .map(s -> "S:" + s.id()).forEach(assets::add);
        return assets;
    }

    private List<String> fireOptions(List<PropertyState> built) {
        var squares = built.stream().map(PropertyState::getSquare).toList();
        if (squares.size() <= 1) return squares.stream().map(String::valueOf).toList();
        var options = new ArrayList<String>();
        for (int i = 0; i < squares.size(); i++) {
            for (int j = i + 1; j < squares.size(); j++) options.add(squares.get(i) + "," + squares.get(j));
        }
        return options;
    }

    private List<String> shareSwapOptions(GameState state, PlayerState player, Ownership ownership) {
        var options = new ArrayList<String>();
        var myShares = ownership.sharesOf(player.getUid()).stream()
                .filter(s -> !ownership.ownsCompleteGroup(player.getUid(), s.group())).toList();
        for (var mine : myShares) {
            for (var other : state.getPlayers()) {
                if (other.isOut() || other.getUid().equals(player.getUid())) continue;
                ownership.sharesOf(other.getUid()).stream().filter(s -> s.value() == mine.value()
                                && !ownership.ownsCompleteGroup(other.getUid(), s.group()))
                        .forEach(s -> options.add(mine.id() + "|" + other.getUid() + "|" + s.id()));
            }
        }
        return options;
    }

    private void transferAsset(GameState state, PlayerState from, PlayerState to, String asset) {
        var parts = asset.split(":");
        if (parts[0].equals("P")) state.property(Integer.parseInt(parts[1])).setOwner(to.getUid());
        else state.share(parts[1]).setOwner(to.getUid());
    }

    private String assetOwner(GameState state, String asset) {
        var parts = asset.split(":");
        return parts[0].equals("P") ? state.property(Integer.parseInt(parts[1])).getOwner()
                : state.share(parts[1]).getOwner();
    }

    private void startCompulsoryAuction(GameState state, PlayerState seller, String asset, List<GameEvent> events) {
        var turnOrder = state.getTurnOrder();
        var sellerIndex = turnOrder.indexOf(seller.getUid());
        var order = IntStream.range(1, turnOrder.size())
                .mapToObj(i -> turnOrder.get((sellerIndex + i) % turnOrder.size()))
                .filter(uid -> !state.player(uid).orElseThrow().isOut()).toList();
        if (order.isEmpty()) return;
        var minimum = rules.compulsorySaleMinimumBid(state, asset);
        state.getPendingDecisions().add(new PendingDecision.AssetAuction(seller.getUid(), asset, minimum, order, 0, List.of()));
        events.add(new GameEvent.AssetAuctionStarted(seller.getUid(), asset, minimum, order));
    }

    List<GameEvent> bidAsset(GameState state, PlayerState bidder, int amount) {
        var auction = (PendingDecision.AssetAuction) state.getPendingDecisions().removeFirst();
        var bids = new ArrayList<>(auction.bids());
        bids.add(new PendingDecision.Bid(bidder.getUid(), amount));
        var nextIndex = auction.index() + 1;
        if (nextIndex < auction.order().size()) {
            state.getPendingDecisions().addFirst(new PendingDecision.AssetAuction(auction.seller(), auction.asset(),
                    auction.minimumBid(), auction.order(), nextIndex, bids));
            return List.of();
        }
        var winner = bids.stream().filter(bid -> bid.amount() > 0)
                .sorted(java.util.Comparator.comparingInt(PendingDecision.Bid::amount).reversed()
                        .thenComparingInt(bid -> auction.order().indexOf(bid.player())))
                .findFirst();
        if (winner.isEmpty()) return List.of(new GameEvent.AssetAuctionCompleted(auction.seller(), auction.asset(), 0, null, bids));
        var winningBid = winner.orElseThrow();
        var seller = state.player(auction.seller()).orElseThrow();
        var buyer = state.player(winningBid.player()).orElseThrow();
        transferAsset(state, seller, buyer, auction.asset());
        var payment = payments.transfer(buyer, seller, winningBid.amount(), MoneyReason.ASSET_AUCTION);
        return List.of(payment, new GameEvent.AssetAuctionCompleted(seller.getUid(), auction.asset(),
                winningBid.amount(), buyer.getUid(), bids));
    }

    private PlayerState relativePlayer(GameState state, String uid, int direction) {
        var order = state.getTurnOrder();
        var start = order.indexOf(uid);
        for (int i = 1; i < order.size(); i++) {
            var index = (start + direction * i + order.size() * i) % order.size();
            var candidate = state.player(order.get(index)).orElseThrow();
            if (!candidate.isOut()) return candidate;
        }
        throw new IllegalStateException("No other active player");
    }

    private boolean hasOtherActivePlayer(GameState state, String uid) {
        return state.getPlayers().stream().anyMatch(candidate -> !candidate.isOut() && !candidate.getUid().equals(uid));
    }

    private void returnSharesByPercent(GameState state, PlayerState player, int dividendPercent, List<GameEvent> events) {
        var ownership = new Ownership(gameData, state);
        var shares = ownership.sharesOf(player.getUid()).stream().filter(share -> share.dividendPercent() == dividendPercent
                && !ownership.ownsCompleteGroup(player.getUid(), share.group())).toList();
        for (var share : shares) {
            state.share(share.id()).setOwner(null);
            events.add(new GameEvent.ShareLost(player.getUid(), share.id()));
        }
    }

    private List<GameEvent> payPerShareClass(GameState state, PlayerState player) {
        var shares = new Ownership(gameData, state).sharesOf(player.getUid());
        var charges = shares.stream().map(share -> new Charge(switch (share.dividendPercent()) {
            case 20, 25 -> 5_000;
            case 30, 40 -> 10_000;
            case 50 -> 15_000;
            default -> 0;
        }, MoneyReason.STOCK_TIP)).filter(charge -> charge.amount() > 0).toList();
        return charges.isEmpty() ? List.of() : payments.charge(state, player, null, charges);
    }

    private List<GameEvent> chargeStockTipPlayers(GameState state, PlayerState drawer,
                                                   java.util.function.ToIntFunction<PlayerState> amount) {
        var events = new ArrayList<GameEvent>();
        var order = state.getTurnOrder();
        var start = order.indexOf(drawer.getUid());
        for (int i = 0; i < order.size(); i++) {
            var target = state.player(order.get((start + i) % order.size())).orElseThrow();
            if (target.isOut()) continue;
            var due = amount.applyAsInt(target);
            if (due > 0) events.addAll(payments.charge(state, target, null, List.of(new Charge(due, MoneyReason.STOCK_TIP))));
        }
        return List.copyOf(events);
    }

    private List<GameEvent> bondOneWins(GameState state, PlayerState drawer) {
        var bond = state.bond(1);
        var owner = bond.getOwner();
        if (owner == null) return List.of();
        bond.setOwner(null);
        var events = new ArrayList<GameEvent>();
        events.add(new GameEvent.BondOneWon(drawer.getUid(), owner, 25_000));
        if (owner.equals(drawer.getUid())) events.add(payments.fromBank(drawer, 25_000, MoneyReason.BOND_PRIZE));
        else events.addAll(payments.charge(state, drawer, owner, List.of(new Charge(25_000, MoneyReason.BOND_PRIZE))));
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
        return bankExit(state, player, square, dice, true);
    }

    List<GameEvent> bankExit(GameState state, PlayerState player, Square square, Dice dice, boolean drawTip) {
        player.setJailExemption(false);
        var events = new ArrayList<GameEvent>();
        if (player.getLoans() == 0) {
            if (drawTip) events.addAll(drawStockTip(state, player, dice));
            return List.copyOf(events);
        }
        var interest = new Charge(player.getLoans() * rules.loanInterest(state), MoneyReason.LOAN_INTEREST);
        events.addAll(payments.charge(state, player, null, List.of(interest)));
        if (drawTip) events.addAll(drawStockTip(state, player, dice));
        return List.copyOf(events);
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
        player.setMissedTurnsInJail(true);
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

    List<GameEvent> endTurn(GameState state, PlayerState player, Dice dice) {
        var events = new ArrayList<GameEvent>();
        events.add(new TurnEnded(player.getUid()));
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
                if (next.getHeldStockTips().contains("PV-29")) next.setTransportNewsDue(true);
            }
            events.add(new TurnSkipped(next.getUid(), next.getMissedTurns()));
            state.setCurrentPlayer(next.getUid());
            next = state.player(nextPlayer(state)).orElseThrow();
        }
        state.setCurrentPlayer(next.getUid());
        state.setPhase(TurnPhase.BEFORE_ROLL);
        next.setNoMovementRollThisTurn(false);
        state.setBoughtThisTurn(false);
        events.add(new TurnStarted(next.getUid()));
        if (next.isTransportNewsDue()) {
            next.setTransportNewsDue(false);
            next.getHeldStockTips().remove("PV-29");
            state.getStockTipDeck().addLast("PV-29");
            events.add(new GameEvent.StockTipUsed(next.getUid(), "PV-29"));
            events.addAll(financeNews(state, next, gameData.square(next.getPosition()), dice));
        }
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
        var events = new ArrayList<GameEvent>();
        events.add(payment);
        events.add(new PropertyBought(player.getUid(), square));
        events.addAll(consumePurchaseCertificate(state, player, true));
        return List.copyOf(events);
    }

    List<GameEvent> buyShare(GameState state, PlayerState player, String id) {
        var payment = payments.toBank(player, rules.sharePrice(state, share(id)), MoneyReason.SHARE_PURCHASE);
        state.share(id).setOwner(player.getUid());
        state.setBoughtThisTurn(true);
        var events = new ArrayList<GameEvent>();
        events.add(payment);
        events.add(new ShareBought(player.getUid(), id));
        events.addAll(consumePurchaseCertificate(state, player, false));
        return List.copyOf(events);
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

    private boolean ownershipOwnsGroupAsset(GameState state, String uid, String group) {
        var groupData = gameData.group(group);
        return groupData.properties().stream().anyMatch(square -> uid.equals(state.property(square).getOwner()))
                || gameData.sharesOf(group).stream().anyMatch(share -> uid.equals(state.share(share.id()).getOwner()));
    }

    private boolean groupBoughtFromBank(GameState state, String group) {
        return gameData.group(group).properties().stream().allMatch(square -> state.property(square).getOwner() != null)
                && gameData.sharesOf(group).stream().allMatch(share -> state.share(share.id()).getOwner() != null);
    }

    private boolean groupHasOtherOwner(GameState state, String uid, String group) {
        return groupDataHasActiveOwner(state, group, uid);
    }

    private boolean groupDataHasActiveOwner(GameState state, String group, String except) {
        var groupData = gameData.group(group);
        return groupData.properties().stream().map(square -> state.property(square).getOwner())
                .filter(owner -> owner != null && !owner.equals(except)).anyMatch(owner -> state.player(owner).filter(p -> !p.isOut()).isPresent())
                || gameData.sharesOf(group).stream().map(share -> state.share(share.id()).getOwner())
                .filter(owner -> owner != null && !owner.equals(except)).anyMatch(owner -> state.player(owner).filter(p -> !p.isOut()).isPresent());
    }

    private int shareholdersMeetingTakeoverSum(GameState state, String group, String caller) {
        var total = 0;
        for (var square : gameData.group(group).properties()) {
            var property = state.property(square);
            if (property.getOwner() != null && !caller.equals(property.getOwner())) {
                var deed = gameData.titleDeed(square);
                total += deed.price() + (property.isBuilt() ? deed.building().price() : 0);
            }
        }
        for (var share : gameData.sharesOf(group)) {
            var owner = state.share(share.id()).getOwner();
            if (owner != null && !caller.equals(owner)) total += share.value();
        }
        return total;
    }

    List<GameEvent> callShareholdersMeeting(GameState state, PlayerState caller, String group, int brokerageFee, Dice dice) {
        var diceResult = new ArrayList<Integer>();
        var success = brokerageFee == 120_000;
        if (!success) {
            diceResult.add(dice.roll());
            diceResult.add(dice.roll());
            success = diceResult.stream().mapToInt(Integer::intValue).sum() <= brokerageFee / 10_000;
        }
        var takeoverSum = shareholdersMeetingTakeoverSum(state, group, caller.getUid());
        var events = new ArrayList<GameEvent>();
        if (!diceResult.isEmpty()) events.add(new GameEvent.DiceRolled(caller.getUid(), diceResult));
        events.add(new GameEvent.ShareholdersMeetingResolved(caller.getUid(), group, brokerageFee, takeoverSum,
                diceResult, success));
        if (!success) {
            events.add(payments.toBank(caller, brokerageFee, MoneyReason.SHAREHOLDERS_MEETING));
            return List.copyOf(events);
        }

        var sellerIds = new LinkedHashSet<String>();
        for (var square : gameData.group(group).properties()) {
            var property = state.property(square);
            if (property.getOwner() != null && !caller.getUid().equals(property.getOwner())) sellerIds.add(property.getOwner());
        }
        for (var share : gameData.sharesOf(group)) {
            var owner = state.share(share.id()).getOwner();
            if (owner != null && !caller.getUid().equals(owner)) sellerIds.add(owner);
        }
        var baseBankFee = Math.min(30_000, brokerageFee);
        var shareOfFee = (brokerageFee - baseBankFee) / (double) sellerIds.size();
        var brokeragePayouts = sellerIds.stream().collect(java.util.stream.Collectors.toMap(uid -> uid,
                uid -> (int) (Math.floor((shareOfFee + 250) / 500) * 500), (a, b) -> a, LinkedHashMap::new));
        for (var square : gameData.group(group).properties()) {
            var property = state.property(square);
            if (property.getOwner() == null || caller.getUid().equals(property.getOwner())) continue;
            var seller = state.player(property.getOwner()).orElseThrow();
            var deed = gameData.titleDeed(square);
            var price = deed.price() + (property.isBuilt() ? deed.building().price() : 0);
            events.add(payments.transfer(caller, seller, price, MoneyReason.SHAREHOLDERS_MEETING));
            var previousOwner = property.getOwner();
            property.setOwner(caller.getUid());
            events.add(new GameEvent.AssetTransferred(previousOwner, caller.getUid(), "P:" + square));
        }
        for (var share : gameData.sharesOf(group)) {
            var shareState = state.share(share.id());
            if (shareState.getOwner() == null || caller.getUid().equals(shareState.getOwner())) continue;
            var seller = state.player(shareState.getOwner()).orElseThrow();
            events.add(payments.transfer(caller, seller, share.value(), MoneyReason.SHAREHOLDERS_MEETING));
            var previousOwner = shareState.getOwner();
            shareState.setOwner(caller.getUid());
            events.add(new GameEvent.AssetTransferred(previousOwner, caller.getUid(), "S:" + share.id()));
        }
        for (var sellerUid : sellerIds) {
            var seller = state.player(sellerUid).orElseThrow();
            var payout = brokeragePayouts.get(sellerUid);
            if (payout > 0) events.add(payments.transfer(caller, seller, payout, MoneyReason.SHAREHOLDERS_MEETING));
        }
        var bankFee = brokerageFee - brokeragePayouts.values().stream().mapToInt(Integer::intValue).sum();
        if (bankFee > 0) events.add(payments.toBank(caller, bankFee, MoneyReason.SHAREHOLDERS_MEETING));
        return List.copyOf(events);
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
        var permit = buildingPermit(state, player);
        if (permit != null) events.addAll(consumeHeldTip(state, player, permit));
        return List.copyOf(events);
    }

    private String buildingPermit(GameState state, PlayerState player) {
        var onProject = rules.canBuildAtProject(state, player);
        var hasProjectPermit = player.getHeldStockTips().contains("PV-01");
        var hasSinglePermit = player.getHeldStockTips().contains("PV-07");
        if ("FL-12".equals(state.getActiveFinanceNews())) {
            if (onProject && hasProjectPermit) return "PV-01";
            return hasSinglePermit ? "PV-07" : null;
        }
        if (state.getPhase() == TurnPhase.AFTER_ROLL) {
            if (onProject && hasProjectPermit) return "PV-01";
            return hasSinglePermit ? "PV-07" : null;
        }
        return !onProject && hasSinglePermit ? "PV-07" : null;
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
    List<GameEvent> declareBankruptcy(GameState state, PlayerState player, Dice dice) {
        var decision = (RaiseFunds) state.getPendingDecisions().removeFirst();
        return bankrupt(state, player, decision.creditor(), dice);
    }

    private List<GameEvent> resign(GameState state, PlayerState player, Dice dice) {
        var events = new ArrayList<GameEvent>();
        events.add(new GameEvent.PlayerResigned(player.getUid()));
        events.addAll(bankrupt(state, player, null, dice));
        return List.copyOf(events);
    }

    private List<GameEvent> bankrupt(GameState state, PlayerState player, String creditorUid, Dice dice) {
        var wasCurrent = player.getUid().equals(state.getCurrentPlayer());
        state.getPendingDecisions().removeIf(pending -> pending.player().equals(player.getUid()));
        var events = new ArrayList<GameEvent>();

        // Liquidate everything the bank will buy or lend on, and use every remaining loan before paying the creditor.
        if (!rules.loansStopped(state) && player.getPosition() != REPAY_LOAN_SQUARE) {
            var loans = rules.loansAvailable(state, player);
            for (int i = 0; i < loans; i++) {
                player.setLoans(player.getLoans() + 1);
                events.add(new LoanTaken(player.getUid(), player.getLoans()));
                events.add(payments.fromBank(player, LOAN_AMOUNT, MoneyReason.LOAN));
            }
        }
        if (player.isCar()) {
            player.setCar(false);
            events.add(new CarSold(player.getUid()));
            events.add(payments.fromBank(player, CAR_SELL_BACK_PRICE, MoneyReason.CAR_SALE));
        }
        for (var share : new Ownership(gameData, state).sharesOf(player.getUid())) {
            events.addAll(sellBackShare(state, player, share.id()));
        }
        for (var property : new Ownership(gameData, state).propertiesOf(player.getUid())) {
            if (property.isMortgaged()) {
                continue;
            }
            var deed = gameData.titleDeed(property.getSquare());
            var mortgage = rules.mortgageValue(state, deed, property);
            var buyBack = rules.propertyBuyBack(state, deed, property);
            if (buyBack != null && (mortgage == null || buyBack >= mortgage)) {
                events.addAll(sellBackProperty(state, player, property.getSquare()));
            } else if (mortgage != null) {
                property.setMortgaged(true);
                events.add(new PropertyMortgaged(player.getUid(), property.getSquare()));
                events.add(payments.fromBank(player, mortgage, MoneyReason.MORTGAGE));
            }
        }

        if (player.getCash() > 0) {
            var creditor = creditorUid == null ? null : state.player(creditorUid).orElseThrow();
            events.add(payments.transfer(player, creditor, player.getCash(), MoneyReason.BANKRUPTCY));
        }
        player.setLoans(0);
        player.setOut(true);
        events.add(new PlayerBankrupt(player.getUid(), creditorUid));
        var returnedProperties = new ArrayList<Integer>();
        for (var property : new Ownership(gameData, state).propertiesOf(player.getUid())) {
            returnedProperties.add(property.getSquare());
            property.setOwner(null);
            property.setMortgaged(false);
            property.setBuilt(false);
        }
        var shares = state.getShares().stream().filter(share -> player.getUid().equals(share.getOwner())).toList();
        shares.forEach(share -> share.setOwner(null));
        var returnedBonds = bonds.returnOwnedBy(state, player.getUid());
        var heldTips = List.copyOf(player.getHeldStockTips());
        state.getStockTipDeck().addAll(heldTips);
        player.getHeldStockTips().clear();
        if (!heldTips.isEmpty()) events.add(new GameEvent.HeldStockTipsReturned(player.getUid(), heldTips));
        if (!returnedProperties.isEmpty() || !shares.isEmpty() || !returnedBonds.isEmpty()) {
            events.add(new AssetsReturned(player.getUid(), returnedProperties,
                    shares.stream().map(ShareState::getId).toList(), returnedBonds));
        }
        if (wasCurrent && state.getPlayers().stream().anyMatch(p -> !p.isOut())) {
            events.addAll(passTurn(state, dice));
        }
        return List.copyOf(events);
    }
}
