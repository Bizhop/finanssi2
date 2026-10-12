package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.engine.GameEvent.AssetsReturned;
import fi.bizhop.finanssi2.game.engine.GameEvent.CarSold;
import fi.bizhop.finanssi2.game.engine.GameEvent.LoanTaken;
import fi.bizhop.finanssi2.game.engine.GameEvent.PlayerBankrupt;
import fi.bizhop.finanssi2.game.engine.GameEvent.PropertyMortgaged;
import fi.bizhop.finanssi2.game.engine.PendingDecision.RaiseFunds;

import java.util.ArrayList;
import java.util.List;

import static fi.bizhop.finanssi2.game.data.GameConstants.CAR_SELL_BACK_PRICE;
import static fi.bizhop.finanssi2.game.data.GameConstants.LOAN_AMOUNT;
import static fi.bizhop.finanssi2.game.data.GameConstants.LOAN_REPAYMENT_SQUARE;
import org.jspecify.annotations.Nullable;

/** Going bankrupt or resigning, and what happens to the player's assets. */
class Bankruptcy {
    final GameEngine engine;
    final GameData gameData;
    final Rules rules;
    final Payments payments;
    final Bonds bonds;

    Bankruptcy(GameEngine engine) {
        this.engine = engine;
        this.gameData = engine.gameData;
        this.rules = engine.rules;
        this.payments = engine.payments;
        this.bonds = engine.bonds;
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

    List<GameEvent> resign(GameState state, PlayerState player, Dice dice) {
        var events = new ArrayList<GameEvent>();
        events.add(new GameEvent.PlayerResigned(player.getPlayerId()));
        events.addAll(bankrupt(state, player, null, dice));
        return List.copyOf(events);
    }

    List<GameEvent> bankrupt(GameState state, PlayerState player, @Nullable String creditorUid, Dice dice) {
        var wasCurrent = player.getPlayerId().equals(state.getCurrentPlayer());
        state.getPendingDecisions().removeIf(pending -> pending.player().equals(player.getPlayerId()));
        var events = new ArrayList<GameEvent>();

        // Liquidate everything the bank will buy or lend on, and use every remaining loan before paying the creditor.
        if (!rules.loansStopped(state) && player.getPosition() != LOAN_REPAYMENT_SQUARE) {
            var loans = rules.loansAvailable(state, player);
            for (int i = 0; i < loans; i++) {
                player.setLoans(player.getLoans() + 1);
                events.add(new LoanTaken(player.getPlayerId(), player.getLoans()));
                events.add(payments.fromBank(player, LOAN_AMOUNT, MoneyReason.LOAN));
            }
        }
        if (player.isCar()) {
            player.setCar(false);
            events.add(new CarSold(player.getPlayerId()));
            events.add(payments.fromBank(player, CAR_SELL_BACK_PRICE, MoneyReason.CAR_SALE));
        }
        for (var share : new Ownership(gameData, state).sharesOf(player.getPlayerId())) {
            events.addAll(engine.sellBackShare(state, player, share.id()));
        }
        for (var property : new Ownership(gameData, state).propertiesOf(player.getPlayerId())) {
            if (property.isMortgaged()) {
                continue;
            }
            var deed = gameData.titleDeed(property.getSquare());
            var mortgage = rules.mortgageValue(state, deed, property);
            var buyBack = rules.propertyBuyBack(state, deed, property);
            if (buyBack != null && (mortgage == null || buyBack >= mortgage)) {
                events.addAll(engine.sellBackProperty(state, player, property.getSquare()));
            } else if (mortgage != null) {
                property.setMortgaged(true);
                events.add(new PropertyMortgaged(player.getPlayerId(), property.getSquare()));
                events.add(payments.fromBank(player, mortgage, MoneyReason.MORTGAGE));
            }
        }

        if (player.getCash() > 0) {
            var creditor = creditorUid == null ? null : state.player(creditorUid).orElseThrow();
            events.add(payments.transfer(player, creditor, player.getCash(), MoneyReason.BANKRUPTCY));
        }
        player.setLoans(0);
        player.setOut(true);
        events.add(new PlayerBankrupt(player.getPlayerId(), creditorUid));
        var returnedProperties = new ArrayList<Integer>();
        for (var property : new Ownership(gameData, state).propertiesOf(player.getPlayerId())) {
            returnedProperties.add(property.getSquare());
            property.setOwner(null);
            property.setMortgaged(false);
            property.setBuilt(false);
        }
        var shares = state.getShares().stream().filter(share -> player.getPlayerId().equals(share.getOwner())).toList();
        shares.forEach(share -> share.setOwner(null));
        var returnedBonds = bonds.returnOwnedBy(state, player.getPlayerId());
        var heldTips = List.copyOf(player.getHeldStockTips());
        state.getStockTipDeck().addAll(heldTips);
        player.getHeldStockTips().clear();
        if (!heldTips.isEmpty()) events.add(new GameEvent.HeldStockTipsReturned(player.getPlayerId(), heldTips));
        if (!returnedProperties.isEmpty() || !shares.isEmpty() || !returnedBonds.isEmpty()) {
            events.add(new AssetsReturned(player.getPlayerId(), returnedProperties,
                    shares.stream().map(ShareState::getId).toList(), returnedBonds));
        }
        if (wasCurrent && state.getPlayers().stream().anyMatch(p -> !p.isOut())) {
            events.addAll(engine.passTurn(state, dice));
        }
        return List.copyOf(events);
    }
}
