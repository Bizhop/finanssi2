package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.data.Share;
import fi.bizhop.finanssi2.game.data.Square;
import fi.bizhop.finanssi2.game.engine.GameEvent.BankDividend;
import fi.bizhop.finanssi2.game.engine.GameEvent.DiceRolled;
import fi.bizhop.finanssi2.game.engine.GameEvent.LandedOn;
import fi.bizhop.finanssi2.game.engine.GameEvent.PieceMoved;
import fi.bizhop.finanssi2.game.engine.GameEvent.PropertySoldBack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static fi.bizhop.finanssi2.game.data.GameConstants.BAIL;
import static fi.bizhop.finanssi2.game.data.GameConstants.BANK_FIRST_SQUARE;
import static fi.bizhop.finanssi2.game.data.GameConstants.LOAN_REPAYMENT_SQUARE;
import static fi.bizhop.finanssi2.game.data.GameConstants.PROJECT_SQUARES;
import static fi.bizhop.finanssi2.game.engine.GameEngine.JAIL_SQUARE;
import static fi.bizhop.finanssi2.game.engine.GameEngine.rollDice;

/** Stock Tip cards: drawing, resolving and the choices and auctions they cause. */
class StockTips {
    final GameEngine engine;
    final GameData gameData;
    final Rules rules;
    final Payments payments;

    StockTips(GameEngine engine) {
        this.engine = engine;
        this.gameData = engine.gameData;
        this.rules = engine.rules;
        this.payments = engine.payments;
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
        events.add(new GameEvent.StockTipDrawn(player.getPlayerId(), id, held));
        if (id.equals("PV-29")) {
            player.setMissedTurns(2);
            player.setMissedTurnsInJail(false);
        }
        if (!held) events.addAll(resolveStockTip(state, player, id, dice));
        return List.copyOf(events);
    }

    List<GameEvent> useHeldStockTip(GameState state, PlayerState player, String card, Dice dice) {
        var events = new ArrayList<GameEvent>(consumeHeldTip(state, player, card));
        events.addAll(engine.moveTo(state, player, PROJECT_SQUARES.getFirst(), dice));
        player.setNoMovementRollThisTurn(true);
        state.setPhase(TurnPhase.AFTER_ROLL);
        return List.copyOf(events);
    }

    List<GameEvent> consumePurchaseCertificate(GameState state, PlayerState player, boolean property) {
        if (property ? !rules.propertiesTradingStopped(state) : !rules.shareTradingStopped(state)) return List.of();
        var card = player.getHeldStockTips().stream().filter(c -> c.equals("PV-02") || c.equals("PV-08")).findFirst();
        return card.map(value -> consumeHeldTip(state, player, value)).orElseGet(List::of);
    }

    List<GameEvent> consumeHeldTip(GameState state, PlayerState player, String card) {
        if (!player.getHeldStockTips().remove(card)) return List.of();
        state.getStockTipDeck().addLast(card);
        return List.of(new GameEvent.StockTipUsed(player.getPlayerId(), card));
    }

    boolean hasPurchaseCertificate(PlayerState player) {
        return player.getHeldStockTips().stream().anyMatch(card -> card.equals("PV-02") || card.equals("PV-08"));
    }

    List<GameEvent> resolveStockTip(GameState state, PlayerState player, String card, Dice dice) {
        var events = new ArrayList<GameEvent>();
        var ownership = new Ownership(gameData, state);
        switch (card) {
            case "PV-03", "PV-09" -> issueFundShare(state, player, card.equals("PV-03") ? 20 : 25, dice, events);
            case "PV-04" -> {
                if (player.isCar()) { player.setCar(false); events.add(new GameEvent.CarLost(player.getPlayerId())); }
            }
            case "PV-05" -> {
                events.addAll(engine.moveTo(state, player, 10, dice));
                if (hasOtherActivePlayer(state, player.getPlayerId())) {
                    var left = relativePlayer(state, player.getPlayerId(), 1);
                    events.addAll(engine.moveTo(state, left, 10, dice));
                }
            }
            case "PV-06" -> events.addAll(engine.chargePlayers(state, player, p -> p.isCar() ? 15_000 : 0, MoneyReason.STOCK_TIP));
            case "PV-10" -> queueChoice(state, player, card, fireOptions(ownership.builtPropertiesOf(player.getPlayerId())), events);
            case "PV-11" -> {
                var amount = (int) (ownership.plantCount(player.getPlayerId()) * 40_000 + ownership.otherBuildingCount(player.getPlayerId()) * 20_000);
                if (amount > 0) events.addAll(payments.charge(state, player, null, List.of(new Charge(amount, MoneyReason.STOCK_TIP))));
            }
            case "PV-12" -> {
                if (player.isCar()) events.addAll(engine.moveTo(state, player, 10, dice));
                else events.addAll(extraRoll(state, player, 1, dice));
            }
            case "PV-13", "PV-14", "PV-15" -> events.addAll(engine.playerDividend(state, player, gameData.square(41), dice));
            case "PV-16" -> events.addAll(bondOneWins(state, player));
            case "PV-17" -> queueChoice(state, player, card, gameData.titleDeeds().stream().map(d -> Integer.toString(d.square())).toList(), events);
            case "PV-18" -> events.addAll(player.getPosition() == 20
                    ? moveToWithoutLanding(state, player, BANK_FIRST_SQUARE) : engine.moveTo(state, player, 20, dice, true, false));
            case "PV-19" -> { events.addAll(engine.moveTo(state, player, 8, dice)); events.addAll(engine.financeNews.financeNews(state, player, gameData.square(player.getPosition()), dice)); }
            case "PV-20" -> { events.addAll(engine.moveTo(state, player, 34, dice)); events.addAll(engine.financeNews.financeNews(state, player, gameData.square(player.getPosition()), dice)); }
            case "PV-21" -> events.addAll(neighbourDividend(state, player));
            case "PV-22" -> { if (player.getPosition() == 1) events.addAll(allShareDividend(state, player)); }
            case "PV-23" -> events.addAll(allShareDividend(state, player));
            case "PV-24" -> freeBond(state, player, events);
            case "PV-26" -> queueChoice(state, player, card, List.of("PAY", "PASS"), events);
            case "PV-27", "PV-28" -> events.addAll(player.getLoans() > 0
                    ? engine.moveTo(state, player, LOAN_REPAYMENT_SQUARE, dice) : engine.moveTo(state, player, BANK_FIRST_SQUARE, dice));
            case "PV-30" -> {
                if (player.getCash() < BAIL) events.addAll(goToJailFromTip(state, player, dice));
                else queueChoice(state, player, card, List.of("JAIL", "BAIL"), events);
            }
            case "PV-31" -> {
                var roll = rollDice(dice, 2);
                events.add(new DiceRolled(player.getPlayerId(), roll));
                if (roll.stream().mapToInt(Integer::intValue).sum() <= 7) {
                    var eligible = ownership.propertiesOf(player.getPlayerId()).stream()
                            .filter(p -> !p.isMortgaged() && !ownership.ownsCompleteGroup(player.getPlayerId(), gameData.titleDeed(p.getSquare()).group()))
                            .map(p -> Integer.toString(p.getSquare())).toList();
                    if (!eligible.isEmpty()) queueChoice(state, player, card, eligible, events);
                }
            }
            case "PV-32", "PV-33" -> events.addAll(engine.financeNews.financeNews(state, player, gameData.square(player.getPosition()), dice));
            case "PV-34" -> { events.add(payments.fromBank(player, 50_000, MoneyReason.STOCK_TIP)); events.addAll(engine.moveTo(state, player, 1, dice, true, false)); }
            case "PV-35" -> {
                if (hasOtherActivePlayer(state, player.getPlayerId()))
                    queueChoice(state, player, card, transferableAssets(state, player, ownership), events);
            }
            case "PV-36" -> {
                if (state.getTurnOrder().stream().filter(playerId -> !state.player(playerId).orElseThrow().isOut()).count() > 2)
                    queueChoice(state, player, card, transferableAssets(state, player, ownership), events);
            }
            case "PV-37" -> {
                events.add(payments.fromBank(player, engine.financeNews.roundUp500(player.getCash(), 5), MoneyReason.STOCK_TIP));
                events.addAll(engine.financeNews.financeNews(state, player, gameData.square(player.getPosition()), dice));
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

    void queueChoice(GameState state, PlayerState player, String card, List<String> options, List<GameEvent> events) {
        if (!options.isEmpty()) state.getPendingDecisions().add(new PendingDecision.StockTipChoice(player.getPlayerId(), card, options));
    }

    List<GameEvent> resolveStockTipChoice(GameState state, PlayerState player, String option, Dice dice) {
        var decision = (PendingDecision.StockTipChoice) state.getPendingDecisions().removeFirst();
        var events = new ArrayList<GameEvent>();
        events.add(new GameEvent.StockTipUsed(player.getPlayerId(), decision.card()));
        switch (decision.card()) {
            case "PV-10" -> {
                var squares = option.split(",");
                var burned = Arrays.stream(squares).map(Integer::parseInt).toList();
                for (var square : burned) state.property(square).setBuilt(false);
                events.add(new GameEvent.BuildingsBurned(player.getPlayerId(), burned));
                if (squares.length > 0) {
                    var die = dice.roll();
                    events.add(new DiceRolled(player.getPlayerId(), List.of(die)));
                    events.add(payments.fromBank(player, die * 10_000, MoneyReason.STOCK_TIP));
                }
            }
            case "PV-17" -> events.addAll(engine.moveTo(state, player, Integer.parseInt(option), dice));
            case "PV-24" -> {
                var parts = option.split(":");
                if (parts[0].equals("B")) {
                    var bond = state.bond(Integer.parseInt(parts[1]));
                    if (bond.getOwner() == null) {
                        bond.setOwner(player.getPlayerId());
                        events.add(new GameEvent.BondGranted(player.getPlayerId(), bond.getNumber()));
                    }
                }
                else {
                    var donor = state.player(parts[1]).orElseThrow();
                    var bond = state.bond(Integer.parseInt(parts[2]));
                    if (!donor.isOut() && donor.getPlayerId().equals(bond.getOwner())) {
                        bond.setOwner(player.getPlayerId());
                        events.add(new GameEvent.BondTransferred(donor.getPlayerId(), player.getPlayerId(), bond.getNumber()));
                    }
                }
            }
            case "PV-26" -> {
                if (option.equals("PAY") && player.getCash() >= 25_000) {
                    events.add(payments.toBank(player, 25_000, MoneyReason.STOCK_TIP));
                    events.addAll(engine.moveTo(state, player, BANK_FIRST_SQUARE, dice));
                }
            }
            case "PV-30" -> {
                if (option.equals("JAIL") || player.getCash() < BAIL) events.addAll(goToJailFromTip(state, player, dice));
                else {
                    events.add(payments.toBank(player, BAIL, MoneyReason.STOCK_TIP));
                    player.setBailRollPending(true);
                }
            }
            case "PV-31" -> {
                var square = Integer.parseInt(option);
                var property = state.property(square);
                if (!player.getPlayerId().equals(property.getOwner())) break;
                var deed = gameData.titleDeed(square);
                var price = deed.value(property.isBuilt());
                if (property.isMortgaged()) price -= rules.redemptionPrice(state, deed, property);
                property.setOwner(null);
                property.setBuilt(false);
                property.setMortgaged(false);
                events.add(new GameEvent.PropertySoldBack(player.getPlayerId(), square));
                events.add(payments.fromBank(player, Math.max(price, 0), MoneyReason.PROPERTY_SALE));
            }
            case "PV-35" -> {
                if (!player.getPlayerId().equals(assetOwner(state, option))) break;
                var receiver = relativePlayer(state, player.getPlayerId(), 1);
                transferAsset(state, player, receiver, option);
                events.addAll(payments.charge(state, receiver, player.getPlayerId(), List.of(new Charge(10_000, MoneyReason.STOCK_TIP))));
                events.add(new GameEvent.AssetTransferred(player.getPlayerId(), receiver.getPlayerId(), option));
            }
            case "PV-36" -> {
                if (player.getPlayerId().equals(assetOwner(state, option))) startCompulsoryAuction(state, player, option, events);
            }
            case "PV-38" -> {
                if (!option.equals("PASS")) {
                    var parts = option.split("\\|");
                    var otherShare = state.share(parts[2]);
                    var ownShare = state.share(parts[0]);
                    if (!player.getPlayerId().equals(ownShare.getOwner()) || !parts[1].equals(otherShare.getOwner())) break;
                    ownShare.setOwner(parts[1]);
                    otherShare.setOwner(player.getPlayerId());
                    events.add(new GameEvent.SharesSwapped(player.getPlayerId(), parts[0], parts[1], parts[2]));
                }
            }
            default -> throw new IllegalArgumentException("No Stock Tip choice effect for " + decision.card());
        }
        return List.copyOf(events);
    }

    List<GameEvent> moveToWithoutLanding(GameState state, PlayerState player, int target) {
        var from = player.getPosition();
        player.setPosition(target);
        return List.of(new PieceMoved(player.getPlayerId(), from, target), new LandedOn(player.getPlayerId(), target));
    }

    List<GameEvent> extraRoll(GameState state, PlayerState player, int diceCount, Dice dice) {
        var roll = rollDice(dice, diceCount);
        var pips = roll.stream().mapToInt(Integer::intValue).sum();
        if ("FL-16".equals(state.getActiveFinanceNews())) pips *= 2;
        var from = player.getPosition();
        var to = engine.moveForward(from, pips);
        player.setPosition(to);
        var events = new ArrayList<GameEvent>();
        events.add(new DiceRolled(player.getPlayerId(), roll));
        events.add(new PieceMoved(player.getPlayerId(), from, to));
        events.addAll(engine.land(state, player, dice));
        return List.copyOf(events);
    }

    void issueFundShare(GameState state, PlayerState player, int dividendPercent, Dice dice, List<GameEvent> events) {
        var share = gameData.fundShares().stream().filter(s -> s.dividendPercent() == dividendPercent).findFirst().orElseThrow();
        var currentOwner = state.share(share.id()).getOwner();
        if (currentOwner == null) {
            state.share(share.id()).setOwner(player.getPlayerId());
            events.add(new GameEvent.ShareIssued(player.getPlayerId(), share.id()));
        } else if (!currentOwner.equals(player.getPlayerId())) {
            var roll = rollDice(dice, 2);
            events.add(new DiceRolled(player.getPlayerId(), roll));
            if (roll.stream().mapToInt(Integer::intValue).sum() >= 7) {
                state.share(share.id()).setOwner(player.getPlayerId());
                events.add(new GameEvent.ShareTaken(player.getPlayerId(), currentOwner, share.id()));
            }
        }
    }

    List<GameEvent> allShareDividend(GameState state, PlayerState player) {
        var events = new ArrayList<GameEvent>();
        var shares = new Ownership(gameData, state).sharesOf(player.getPlayerId());
        var amount = shares.stream().mapToInt(share -> rules.bankDividend(state, share)).sum();
        if (amount == 0) return List.of();
        events.add(new GameEvent.BankDividend(player.getPlayerId(), player.getPosition(), shares.stream().map(Share::id).toList(), amount));
        events.add(payments.fromBank(player, amount, MoneyReason.BANK_DIVIDEND));
        return List.copyOf(events);
    }

    List<GameEvent> neighbourDividend(GameState state, PlayerState drawer) {
        if (!hasOtherActivePlayer(state, drawer.getPlayerId())) return List.of();
        var neighbour = relativePlayer(state, drawer.getPlayerId(), -1);
        var groups = new Ownership(gameData, state).groupsWithPropertiesOf(drawer.getPlayerId());
        var shares = new Ownership(gameData, state).sharesOf(neighbour.getPlayerId()).stream()
                .filter(share -> share.group() != null && groups.contains(share.group())).toList();
        var multiplier = "FL-16".equals(state.getActiveFinanceNews()) ? 2 : 1;
        var amount = shares.stream().mapToInt(share -> share.dividend() * multiplier).sum();
        if (amount == 0) return List.of();
        var events = new ArrayList<GameEvent>();
        events.add(new GameEvent.StockTipDividendCharged(drawer.getPlayerId(), neighbour.getPlayerId(),
                shares.stream().map(Share::id).toList(), amount));
        events.addAll(payments.charge(state, drawer, neighbour.getPlayerId(), List.of(new Charge(amount, MoneyReason.PLAYER_DIVIDEND))));
        return List.copyOf(events);
    }

    void freeBond(GameState state, PlayerState player, List<GameEvent> events) {
        var bank = state.getBonds().stream().filter(bond -> bond.getOwner() == null).map(b -> "B:" + b.getNumber()).toList();
        if (!bank.isEmpty()) { queueChoice(state, player, "PV-24", bank, events); return; }
        if (!hasOtherActivePlayer(state, player.getPlayerId())) return;
        var neighbour = relativePlayer(state, player.getPlayerId(), 1);
        var owned = state.getBonds().stream().filter(bond -> neighbour.getPlayerId().equals(bond.getOwner()))
                .map(b -> "P:" + neighbour.getPlayerId() + ":" + b.getNumber()).toList();
        queueChoice(state, player, "PV-24", owned, events);
    }

    List<GameEvent> goToJailFromTip(GameState state, PlayerState player, Dice dice) {
        var events = new ArrayList<GameEvent>();
        events.addAll(engine.moveTo(state, player, JAIL_SQUARE, dice));
        return List.copyOf(events);
    }

    List<String> transferableAssets(GameState state, PlayerState player, Ownership ownership) {
        var assets = new ArrayList<String>();
        ownership.propertiesOf(player.getPlayerId()).stream()
                .filter(p -> !ownership.ownsCompleteGroup(player.getPlayerId(), gameData.titleDeed(p.getSquare()).group()))
                .map(p -> "P:" + p.getSquare()).forEach(assets::add);
        ownership.sharesOf(player.getPlayerId()).stream()
                .filter(s -> !ownership.ownsCompleteGroup(player.getPlayerId(), s.group()))
                .map(s -> "S:" + s.id()).forEach(assets::add);
        return assets;
    }

    List<String> fireOptions(List<PropertyState> built) {
        var squares = built.stream().map(PropertyState::getSquare).toList();
        if (squares.size() <= 1) return squares.stream().map(String::valueOf).toList();
        var options = new ArrayList<String>();
        for (int i = 0; i < squares.size(); i++) {
            for (int j = i + 1; j < squares.size(); j++) options.add(squares.get(i) + "," + squares.get(j));
        }
        return options;
    }

    List<String> shareSwapOptions(GameState state, PlayerState player, Ownership ownership) {
        var options = new ArrayList<String>();
        var myShares = ownership.sharesOf(player.getPlayerId()).stream()
                .filter(s -> !ownership.ownsCompleteGroup(player.getPlayerId(), s.group())).toList();
        for (var mine : myShares) {
            for (var other : state.getPlayers()) {
                if (other.isOut() || other.getPlayerId().equals(player.getPlayerId())) continue;
                ownership.sharesOf(other.getPlayerId()).stream().filter(s -> s.value() == mine.value()
                                && !ownership.ownsCompleteGroup(other.getPlayerId(), s.group()))
                        .forEach(s -> options.add(mine.id() + "|" + other.getPlayerId() + "|" + s.id()));
            }
        }
        return options;
    }

    void transferAsset(GameState state, PlayerState from, PlayerState to, String asset) {
        var parts = asset.split(":");
        if (parts[0].equals("P")) state.property(Integer.parseInt(parts[1])).setOwner(to.getPlayerId());
        else state.share(parts[1]).setOwner(to.getPlayerId());
    }

    String assetOwner(GameState state, String asset) {
        var parts = asset.split(":");
        return parts[0].equals("P") ? state.property(Integer.parseInt(parts[1])).getOwner()
                : state.share(parts[1]).getOwner();
    }

    void startCompulsoryAuction(GameState state, PlayerState seller, String asset, List<GameEvent> events) {
        var order = state.playersFrom(seller.getPlayerId()).stream().skip(1).filter(player -> !player.isOut())
                .map(PlayerState::getPlayerId).toList();
        if (order.isEmpty()) return;
        var minimum = rules.compulsorySaleMinimumBid(state, asset);
        state.getPendingDecisions().add(new PendingDecision.AssetAuction(seller.getPlayerId(), asset, minimum, order, 0, List.of()));
        events.add(new GameEvent.AssetAuctionStarted(seller.getPlayerId(), asset, minimum, order));
    }

    List<GameEvent> bidAsset(GameState state, PlayerState bidder, int amount) {
        var auction = (PendingDecision.AssetAuction) state.getPendingDecisions().removeFirst();
        var bids = new ArrayList<>(auction.bids());
        bids.add(new PendingDecision.Bid(bidder.getPlayerId(), amount));
        var nextIndex = auction.index() + 1;
        if (nextIndex < auction.order().size()) {
            state.getPendingDecisions().addFirst(new PendingDecision.AssetAuction(auction.seller(), auction.asset(),
                    auction.minimumBid(), auction.order(), nextIndex, bids));
            return List.of();
        }
        var winner = bids.stream().filter(bid -> bid.amount() > 0)
                .sorted(Comparator.comparingInt(PendingDecision.Bid::amount).reversed()
                        .thenComparingInt(bid -> auction.order().indexOf(bid.player())))
                .findFirst();
        if (winner.isEmpty()) return List.of(new GameEvent.AssetAuctionCompleted(auction.seller(), auction.asset(), 0, null, bids));
        var winningBid = winner.orElseThrow();
        var seller = state.player(auction.seller()).orElseThrow();
        var buyer = state.player(winningBid.player()).orElseThrow();
        transferAsset(state, seller, buyer, auction.asset());
        var payment = payments.transfer(buyer, seller, winningBid.amount(), MoneyReason.ASSET_AUCTION);
        return List.of(payment, new GameEvent.AssetAuctionCompleted(seller.getPlayerId(), auction.asset(),
                winningBid.amount(), buyer.getPlayerId(), bids));
    }

    PlayerState relativePlayer(GameState state, String playerId, int direction) {
        var order = state.getTurnOrder();
        var start = order.indexOf(playerId);
        for (int i = 1; i < order.size(); i++) {
            var index = (start + direction * i + order.size() * i) % order.size();
            var candidate = state.player(order.get(index)).orElseThrow();
            if (!candidate.isOut()) return candidate;
        }
        throw new IllegalStateException("No other active player");
    }

    boolean hasOtherActivePlayer(GameState state, String playerId) {
        return state.getPlayers().stream().anyMatch(candidate -> !candidate.isOut() && !candidate.getPlayerId().equals(playerId));
    }

    void returnSharesByPercent(GameState state, PlayerState player, int dividendPercent, List<GameEvent> events) {
        var ownership = new Ownership(gameData, state);
        var shares = ownership.sharesOf(player.getPlayerId()).stream().filter(share -> share.dividendPercent() == dividendPercent
                && !ownership.ownsCompleteGroup(player.getPlayerId(), share.group())).toList();
        for (var share : shares) {
            state.share(share.id()).setOwner(null);
            events.add(new GameEvent.ShareLost(player.getPlayerId(), share.id()));
        }
    }

    List<GameEvent> payPerShareClass(GameState state, PlayerState player) {
        var shares = new Ownership(gameData, state).sharesOf(player.getPlayerId());
        var charges = shares.stream().map(share -> new Charge(switch (share.dividendPercent()) {
            case 20, 25 -> 5_000;
            case 30, 40 -> 10_000;
            case 50 -> 15_000;
            default -> 0;
        }, MoneyReason.STOCK_TIP)).filter(charge -> charge.amount() > 0).toList();
        return charges.isEmpty() ? List.of() : payments.charge(state, player, null, charges);
    }

    List<GameEvent> bondOneWins(GameState state, PlayerState drawer) {
        var bond = state.bond(1);
        var owner = bond.getOwner();
        if (owner == null) return List.of();
        bond.setOwner(null);
        var events = new ArrayList<GameEvent>();
        events.add(new GameEvent.BondOneWon(drawer.getPlayerId(), owner, 25_000));
        if (owner.equals(drawer.getPlayerId())) events.add(payments.fromBank(drawer, 25_000, MoneyReason.BOND_PRIZE));
        else events.addAll(payments.charge(state, drawer, owner, List.of(new Charge(25_000, MoneyReason.BOND_PRIZE))));
        return List.copyOf(events);
    }
}
