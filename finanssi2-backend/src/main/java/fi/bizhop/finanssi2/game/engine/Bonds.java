package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.Square;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static fi.bizhop.finanssi2.game.data.GameConstants.BOND_COUNT;

/** Bond ownership, purchase offers, sealed auctions and prize draws. */
public class Bonds {
    final Rules rules;
    final Payments payments;

    public Bonds(Rules rules, Payments payments) {
        this.rules = rules;
        this.payments = payments;
    }

    public boolean available(GameState state) {
        return state.getBonds().stream().anyMatch(bond -> bond.getOwner() == null);
    }

    /** Returns the player's bonds to the bank without compensation. */
    public List<Integer> returnOwnedBy(GameState state, String uid) {
        var owned = state.getBonds().stream().filter(bond -> uid.equals(bond.getOwner())).toList();
        owned.forEach(bond -> bond.setOwner(null));
        return owned.stream().map(BondState::getNumber).toList();
    }

    List<GameEvent> smallBondDraw(GameState state, PlayerState player, Square square, Dice dice) {
        if (!available(state)) {
            return drawBonds(state, rules.smallBondPrizes(state), dice);
        }
        state.getPendingDecisions().add(new PendingDecision.BondOffer(player.getUid(), BondContinuation.SMALL_DRAW));
        return List.of();
    }

    List<GameEvent> bondAuction(GameState state, PlayerState player, Square square, Dice dice) {
        if (!available(state)) {
            return List.of();
        }
        var order = playersFrom(state, player.getUid())
                .filter(candidate -> !candidate.isOut() && candidate.getCash() > 0)
                .map(PlayerState::getUid)
                .toList();
        if (order.isEmpty()) {
            return List.of();
        }
        state.getPendingDecisions().add(new PendingDecision.BondAuction(order.getFirst(), order, 0, List.of()));
        return List.of();
    }

    List<GameEvent> buyBond(GameState state, PlayerState player, int number, Dice dice) {
        var bond = state.bond(number);
        bond.setOwner(player.getUid());
        var paid = payments.toBank(player, rules.bondPrice(state), MoneyReason.BOND_PURCHASE);
        var offer = (PendingDecision.BondOffer) state.getPendingDecisions().removeFirst();
        var events = new ArrayList<GameEvent>();
        events.add(paid);
        events.add(new GameEvent.BondBought(player.getUid(), number));
        events.addAll(afterBondOffer(state, offer, dice));
        return List.copyOf(events);
    }

    List<GameEvent> passBond(GameState state, Dice dice) {
        var offer = (PendingDecision.BondOffer) state.getPendingDecisions().removeFirst();
        return afterBondOffer(state, offer, dice);
    }

    List<GameEvent> afterBondOffer(GameState state, PendingDecision.BondOffer offer, Dice dice) {
        return switch (offer.after()) {
            case SMALL_DRAW -> drawBonds(state, rules.smallBondPrizes(state), dice);
            case GRAND_DRAW -> drawBonds(state, rules.grandBondPrizes(state), dice);
            case NONE, CONTINUE_GRAND_DRAW -> List.of();
        };
    }

    /** Players in turn order, starting with the given player */
    static Stream<PlayerState> playersFrom(GameState state, String uid) {
        var order = state.getTurnOrder();
        var start = order.indexOf(uid);
        return IntStream.range(0, order.size())
                .mapToObj(i -> state.player(order.get((start + i) % order.size())).orElseThrow());
    }

    /** Queues the grand drawing: the drawing player first, then everyone else in turn order. */
    List<GameEvent> grandBondDraw(GameState state, PlayerState drawer, Dice dice) {
        if (!available(state)) {
            return drawBonds(state, rules.grandBondPrizes(state), dice);
        }
        var offers = playersFrom(state, drawer.getUid())
                .filter(candidate -> !candidate.isOut() && candidate.getCash() >= rules.bondPrice(state))
                .map(candidate -> new PendingDecision.BondOffer(candidate.getUid(), BondContinuation.CONTINUE_GRAND_DRAW))
                .toList();
        var pending = state.getPendingDecisions();
        pending.addAll(offers);
        if (pending.isEmpty()) {
            return drawBonds(state, rules.grandBondPrizes(state), dice);
        }
        if (pending.getLast() instanceof PendingDecision.BondOffer last && last.after() == BondContinuation.CONTINUE_GRAND_DRAW) {
            pending.set(pending.size() - 1, new PendingDecision.BondOffer(last.player(), BondContinuation.GRAND_DRAW));
        }
        return List.of();
    }

    List<GameEvent> bidBond(GameState state, PlayerState player, int amount, Dice dice) {
        var auction = (PendingDecision.BondAuction) state.getPendingDecisions().removeFirst();
        var bids = Stream.concat(auction.bids().stream(), Stream.of(new PendingDecision.Bid(player.getUid(), amount))).toList();
        var next = IntStream.range(auction.index() + 1, auction.order().size())
                .filter(index -> bids.stream().noneMatch(bid -> bid.player().equals(auction.order().get(index))))
                .findFirst();
        if (next.isPresent()) {
            var index = next.getAsInt();
            state.getPendingDecisions().addFirst(new PendingDecision.BondAuction(auction.order().get(index), auction.order(), index, bids));
            return List.of();
        }
        var winner = bids.stream().filter(bid -> bid.amount() > 0)
                .sorted(Comparator.comparingInt(PendingDecision.Bid::amount).reversed())
                .findFirst();
        if (winner.isEmpty()) {
            return List.of(new GameEvent.BondAuctionCompleted(0, null, 0, bids));
        }
        var winningBid = winner.orElseThrow();
        var number = state.getBonds().stream().filter(bond -> bond.getOwner() == null).findFirst().orElseThrow().getNumber();
        state.bond(number).setOwner(winningBid.player());
        var paid = payments.toBank(state.player(winningBid.player()).orElseThrow(), winningBid.amount(), MoneyReason.BOND_PURCHASE);
        return List.of(paid, new GameEvent.BondAuctionCompleted(winningBid.amount(), winningBid.player(), number, bids));
    }

    List<GameEvent> drawBonds(GameState state, List<Integer> prizes, Dice dice) {
        var first = dice.roll();
        var second = dice.roll();
        var low = first == second ? (first == 1 ? BOND_COUNT : first - 1) : Math.min(first, second);
        var numbers = List.of(first + second, Math.max(first, second), low);
        var events = new ArrayList<GameEvent>();
        for (int i = 0; i < numbers.size(); i++) {
            var bond = state.bond(numbers.get(i));
            var owner = bond.getOwner();
            events.add(new GameEvent.BondDrawn(numbers.get(i), prizes.get(i), owner));
            if (owner == null) {
                continue;
            }
            payments.fromBank(state.player(owner).orElseThrow(), prizes.get(i), MoneyReason.BOND_PRIZE);
            bond.setOwner(null);
        }
        return List.copyOf(events);
    }

}
