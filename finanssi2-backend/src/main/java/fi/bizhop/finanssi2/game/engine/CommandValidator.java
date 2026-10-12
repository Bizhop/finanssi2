package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.engine.GameCommand.Build;
import fi.bizhop.finanssi2.game.engine.GameCommand.BuyCar;
import fi.bizhop.finanssi2.game.engine.GameCommand.BuyProperty;
import fi.bizhop.finanssi2.game.engine.GameCommand.BuyShare;
import fi.bizhop.finanssi2.game.engine.GameCommand.DeclareBankruptcy;
import fi.bizhop.finanssi2.game.engine.GameCommand.EndTurn;
import fi.bizhop.finanssi2.game.engine.GameCommand.Mortgage;
import fi.bizhop.finanssi2.game.engine.GameCommand.Pay;
import fi.bizhop.finanssi2.game.engine.GameCommand.Redeem;
import fi.bizhop.finanssi2.game.engine.GameCommand.RepayLoan;
import fi.bizhop.finanssi2.game.engine.GameCommand.Roll;
import fi.bizhop.finanssi2.game.engine.GameCommand.SellBackProperty;
import fi.bizhop.finanssi2.game.engine.GameCommand.SellBackShare;
import fi.bizhop.finanssi2.game.engine.GameCommand.SellCar;
import fi.bizhop.finanssi2.game.engine.GameCommand.TakeLoan;
import fi.bizhop.finanssi2.game.engine.PendingDecision.RaiseFunds;

import static fi.bizhop.finanssi2.game.data.GameConstants.BOND_COUNT;
import static fi.bizhop.finanssi2.game.data.GameConstants.BOND_PRICE;
import static fi.bizhop.finanssi2.game.data.GameConstants.BROKERAGE_FEE_STEP;
import static fi.bizhop.finanssi2.game.data.GameConstants.CAR_PRICE;
import static fi.bizhop.finanssi2.game.data.GameConstants.LOAN_AMOUNT;
import static fi.bizhop.finanssi2.game.data.GameConstants.LOAN_REPAYMENT_SQUARE;
import static fi.bizhop.finanssi2.game.data.GameConstants.MAX_BROKERAGE_FEE;
import static fi.bizhop.finanssi2.game.data.GameConstants.MIN_BROKERAGE_FEE;
import static fi.bizhop.finanssi2.game.data.GameConstants.PROJECT_SQUARES;
import static fi.bizhop.finanssi2.game.data.GameConstants.SQUARE_COUNT;
import static fi.bizhop.finanssi2.game.engine.GameEngine.HEAD_OFFICE_FIRST_SQUARE;
import static fi.bizhop.finanssi2.game.engine.GameEngine.TIMING;
import static fi.bizhop.finanssi2.game.engine.GameEngine.decisionCommands;
import static fi.bizhop.finanssi2.game.engine.GameEngine.ownProperty;
import static fi.bizhop.finanssi2.game.engine.GameEngine.require;
import static fi.bizhop.finanssi2.game.engine.GameEngine.requirePurchase;

/** Checks whether a command is allowed right now, changing nothing. */
class CommandValidator {
    final GameEngine engine;
    final GameData gameData;
    final Rules rules;

    CommandValidator(GameEngine engine) {
        this.engine = engine;
        this.gameData = engine.gameData;
        this.rules = engine.rules;
    }

    /** Throws if the command is not allowed; changes nothing */
    void validate(GameState state, String playerId, GameCommand command) {
        require(!state.isFinished(), "The game is finished");
        require(!(command instanceof GameCommand.EndGame), "Only the game service can end a game");
        if (command instanceof GameCommand.Resign) {
            var player = state.player(playerId).orElseThrow();
            require(!player.isOut(), "You are already out of the game");
            require(state.getPendingDecisions().isEmpty()
                            || state.getPendingDecisions().getFirst() instanceof RaiseFunds funds && funds.player().equals(playerId),
                    "Resolve the pending decision before resigning");
            return;
        }
        if (!playerId.equals(state.actor())) {
            throw new NotYourTurn();
        }
        var player = state.player(playerId).orElseThrow();
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
                require(player.getPosition() != LOAN_REPAYMENT_SQUARE, "No new loans on square " + LOAN_REPAYMENT_SQUARE);
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
                require(engine.fundsAvailable(state, player) < decision.amount(), "You can still raise enough funds");
            }
            case BuyProperty buy -> {
                var deed = engine.deed(buy.square());
                requirePurchase(state, player);
                require(state.property(deed.square()).getOwner() == null, "The property is not for sale");
                require(!rules.propertiesTradingStopped(state) || engine.stockTips.hasPurchaseCertificate(player), "The bank is not selling properties");
                require(player.getCash() >= rules.propertyPrice(state, deed), "Not enough cash");
            }
            case BuyShare buy -> {
                var share = engine.share(buy.share());
                requirePurchase(state, player);
                require(state.share(share.id()).getOwner() == null, "The share is not for sale");
                require(!rules.shareTradingStopped(state) || engine.stockTips.hasPurchaseCertificate(player), "The bank is not selling shares");
                require(player.getCash() >= rules.sharePrice(state, share), "Not enough cash");
            }
            case Mortgage mortgage -> {
                var property = ownProperty(state, player, mortgage.square());
                require(!property.isMortgaged(), "Already mortgaged");
                require(rules.mortgageValue(state, engine.deed(property.getSquare()), property) != null, "This property cannot be mortgaged");
            }
            case Redeem redeem -> {
                var property = ownProperty(state, player, redeem.square());
                require(property.isMortgaged(), "Not mortgaged");
                require(player.getCash() >= rules.redemptionPrice(state, engine.deed(property.getSquare()), property), "Not enough cash");
            }
            case SellBackProperty sell -> {
                var property = ownProperty(state, player, sell.square());
                require(!property.isMortgaged(), "Redeem the mortgage first");
                require(rules.propertyBuyBack(state, engine.deed(property.getSquare()), property) != null,
                        "The bank does not buy this property back");
            }
            case SellBackShare sell -> {
                var share = engine.share(sell.share());
                require(player.getPlayerId().equals(state.share(share.id()).getOwner()), "Not your share");
            }
            case Build build -> {
                require(rules.canBuild(state, player), "Building is allowed only on squares 17 and 40");
                require(!build.squares().isEmpty(), "Nothing to build");
                if ("PV-07".equals(engine.buildingPermit(state, player)))
                    require(build.squares().size() == 1, "This permit covers one property");
                require(build.squares().stream().distinct().count() == build.squares().size(), "One building per property");
                var cost = build.squares().stream().mapToInt(square -> {
                    var property = ownProperty(state, player, square);
                    var deed = engine.deed(square);
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
                require(meeting.brokerageFee() >= MIN_BROKERAGE_FEE && meeting.brokerageFee() <= MAX_BROKERAGE_FEE
                                && meeting.brokerageFee() % BROKERAGE_FEE_STEP == 0, "Invalid brokerage fee");
                require(engine.shareholdersMeetings.ownershipOwnsGroupAsset(state, player.getPlayerId(), meeting.group()), "You own no asset in this group");
                require(engine.shareholdersMeetings.groupHasOtherOwner(state, player.getPlayerId(), meeting.group()), "Other players own no assets in this group");
                // House rule (the default): a meeting takes over all of the group from the other owners, so nothing may be left in the bank
                require(state.getSettings().shareholdersMeeting() != ShareholdersMeeting.ALL_ASSETS_BOUGHT || engine.shareholdersMeetings.groupBoughtFromBank(state, meeting.group()),
                        "Every property and share of the group must be bought from the bank first");
                require(player.getCash() >= engine.shareholdersMeetings.shareholdersMeetingTakeoverSum(state, meeting.group(), player.getPlayerId())
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
            if (player.isNoMovementRollThisTurn() && player.getPosition() == PROJECT_SQUARES.getFirst()) return;
            if (player.getHeldStockTips().contains("PV-07")
                    || player.getHeldStockTips().contains("PV-01") && PROJECT_SQUARES.contains(player.getPosition())) return;
        }
        switch (TIMING.get(command.getClass())) {
            case BEFORE_ROLL -> require(phase == TurnPhase.BEFORE_ROLL, "Only allowed before rolling");
            case AFTER_ROLL -> require(phase == TurnPhase.AFTER_ROLL, "Only allowed after rolling");
            case OWN_TURN -> {}
            case DECISION -> throw new RuleViolation("No pending decision");
        }
    }
}
