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
        return new MoneyTransferred(null, player.getUid(), amount, reason);
    }

    /** The player pays the bank; the caller has checked the cash */
    public GameEvent toBank(PlayerState player, int amount, MoneyReason reason) {
        return transfer(player, null, amount, reason);
    }

    /** The player pays a player, or the bank when {@code to} is null; the caller has checked the cash */
    public GameEvent transfer(PlayerState from, PlayerState to, int amount, MoneyReason reason) {
        if (from.getCash() < amount) {
            throw new IllegalStateException(from.getUid() + " cannot pay " + amount);
        }
        from.setCash(from.getCash() - amount);
        if (to != null) {
            to.setCash(to.getCash() + amount);
        }
        return new MoneyTransferred(from.getUid(), to == null ? null : to.getUid(), amount, reason);
    }

    /**
     * A payment the player must make: made now if the cash covers it, otherwise a {@link RaiseFunds} decision is queued and the
     * payment is made when the player pays it. {@code creditor} is null for the bank.
     */
    public List<GameEvent> charge(GameState state, PlayerState player, String creditor, List<Charge> charges) {
        var decision = new RaiseFunds(player.getUid(), creditor, charges);
        if (player.getCash() >= decision.amount()) {
            return settle(state, decision);
        }
        state.getPendingDecisions().add(decision);
        return List.of(new PaymentDue(player.getUid(), creditor, decision.amount()));
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
                events.add(new LoanRepaid(player.getUid(), player.getLoans()));
            }
        }
        return events;
    }
}
