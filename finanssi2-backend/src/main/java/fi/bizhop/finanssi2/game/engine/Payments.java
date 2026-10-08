package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.engine.GameEvent.LoanRepaid;
import fi.bizhop.finanssi2.game.engine.GameEvent.MoneyTransferred;
import fi.bizhop.finanssi2.game.engine.GameEvent.PaymentDue;
import fi.bizhop.finanssi2.game.engine.PendingDecision.RaiseFunds;

import java.util.ArrayList;
import java.util.List;

/** All changes in cash. The bank's money is unlimited, so only players' cash is tracked. */
public class Payments {
    /** Bank pays the player */
    public GameEvent fromBank(PlayerState player, int amount, MoneyReason reason) {
        player.setCash(player.getCash() + amount);
        return new MoneyTransferred(null, player.getPlayerId(), amount, reason);
    }

    /** The player pays the bank; the caller has checked the cash */
    public GameEvent toBank(PlayerState player, int amount, MoneyReason reason) {
        return transfer(player, null, amount, reason);
    }

    /** The player pays a player, or the bank when {@code to} is null; the caller has checked the cash */
    public GameEvent transfer(PlayerState from, PlayerState to, int amount, MoneyReason reason) {
        if (from.getCash() < amount) {
            throw new IllegalStateException(from.getPlayerId() + " cannot pay " + amount);
        }
        from.setCash(from.getCash() - amount);
        if (to != null) {
            to.setCash(to.getCash() + amount);
        }
        return new MoneyTransferred(from.getPlayerId(), to == null ? null : to.getPlayerId(), amount, reason);
    }

    /**
     * A payment the player must make: made now if the cash covers it, otherwise a {@link RaiseFunds} decision is queued and the
     * payment is made when the player pays it. A player who already has a payment pending gets the new one queued too, so payments
     * are made in order. {@code creditor} is null for the bank.
     */
    public List<GameEvent> charge(GameState state, PlayerState player, String creditor, List<Charge> charges) {
        var decision = new RaiseFunds(player.getPlayerId(), creditor, charges);
        var paymentPending = state.getPendingDecisions().stream()
                .anyMatch(pending -> pending instanceof RaiseFunds && pending.player().equals(player.getPlayerId()));
        if (!paymentPending && player.getCash() >= decision.amount()) {
            return settle(state, decision);
        }
        state.getPendingDecisions().add(decision);
        return List.of(new PaymentDue(player.getPlayerId(), creditor, decision.amount()));
    }

    /** Makes the payment of the decision; the caller has checked the cash */
    public List<GameEvent> settle(GameState state, RaiseFunds decision) {
        var player = state.player(decision.player()).orElseThrow();
        var creditor = decision.creditor() == null ? null : state.player(decision.creditor()).orElseThrow();
        var events = new ArrayList<GameEvent>();
        for (var charge : decision.charges()) {
            events.add(transfer(player, creditor, charge.amount(), charge.reason()));
            if (charge.reason() == MoneyReason.LOAN_REPAYMENT) {
                player.setLoans(player.getLoans() - 1);
                events.add(new LoanRepaid(player.getPlayerId(), player.getLoans()));
            }
        }
        return List.copyOf(events);
    }
}
