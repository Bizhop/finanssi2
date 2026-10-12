package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.data.Square;
import fi.bizhop.finanssi2.game.engine.GameEvent.BankEntranceRoll;
import fi.bizhop.finanssi2.game.engine.GameEvent.PieceMoved;

import java.util.ArrayList;
import java.util.List;

import static fi.bizhop.finanssi2.game.data.GameConstants.BANK_FIRST_SQUARE;
import static fi.bizhop.finanssi2.game.data.GameConstants.INDUSTRIAL_SQUARES;
import static fi.bizhop.finanssi2.game.data.GameConstants.SQUARE_COUNT;
import static fi.bizhop.finanssi2.game.engine.GameEngine.rollDice;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_01;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_02;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_03;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_04;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_05;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_06;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_07;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_08;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_09;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_10;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_11;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_12;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_13;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_14;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_15;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_16;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_17;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_18;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_19;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_20;
import static fi.bizhop.finanssi2.game.engine.FinanceNewsCard.FL_21;

/** Finance News cards: drawing, the lasting card and the effects that touch every player. */
class FinanceNews {
    final GameEngine engine;
    final GameData gameData;
    final Rules rules;
    final Payments payments;

    FinanceNews(GameEngine engine) {
        this.engine = engine;
        this.gameData = engine.gameData;
        this.rules = engine.rules;
        this.payments = engine.payments;
    }

    /** Draw a Finance News card, replacing the previous active card and preserving the shuffled deck order. */
    List<GameEvent> financeNews(GameState state, PlayerState player, Square square, Dice dice) {
        if (state.getFinanceNewsDeck().isEmpty()) return List.of();
        var deck = state.getFinanceNewsDeck();
        var id = deck.removeFirst();
        var card = FinanceNewsCard.of(id);
        deck.addLast(id);
        var replaced = state.getActiveFinanceNews();
        state.setActiveFinanceNews(switch (card) {
            case FL_01, FL_05, FL_06, FL_08, FL_09, FL_10, FL_11, FL_12, FL_14, FL_15, FL_16, FL_17, FL_20, FL_21 -> id;
            default -> null;
        });
        var events = new ArrayList<GameEvent>();
        events.add(new GameEvent.FinanceNewsDrawn(player.getPlayerId(), id, replaced));
        if (card == FL_02 || card == FL_03) events.addAll(engine.grandBondDraw(state, player, dice));
        if (card == FL_01) events.addAll(engine.chargePlayers(state, player, p -> p.isCar() ? 5_000 : 0, MoneyReason.FINANCE_NEWS));
        if (card == FL_07) events.addAll(engine.chargePlayers(state, player, p -> roundUp500(p.getCash(), 2), MoneyReason.FINANCE_NEWS));
        if (card == FL_13) events.addAll(engine.chargePlayers(state, player, p -> roundUp500(
                Math.min(p.getCash(), 100_000) / 10 + Math.max(0, p.getCash() - 100_000) / 4, 1), MoneyReason.FINANCE_NEWS));
        if (card == FL_04) state.enqueueDecision(new PendingDecision.NewsDirection(player.getPlayerId(), id));
        if (card == FL_10) {
            for (var playerId : List.copyOf(state.getTurnOrder())) {
                var standing = state.player(playerId).orElseThrow();
                if (!standing.isOut() && standing.getPosition() == BANK_FIRST_SQUARE) events.addAll(engine.moveTo(state, standing, 1, dice));
            }
        }
        if (card == FL_19) events.addAll(energyTax(state, player, dice));
        if (card == FL_18) {
            for (var shareholder : state.getTurnOrder()) {
                var recipient = state.player(shareholder).orElseThrow();
                if (recipient.isOut()) continue;
                var dividendEvents = engine.bankDividend(state, recipient, gameData.square(46), dice);
                events.addAll(dividendEvents);
            }
        }
        return List.copyOf(events);
    }

    List<GameEvent> chooseNewsDirection(GameState state, PlayerState drawer, boolean forward, Dice dice) {
        var decision = (PendingDecision.NewsDirection) state.popDecision();
        var events = new ArrayList<GameEvent>();
        var from = drawer.getPosition();
        var to = from;
        var direction = forward ? 1 : -1;
        for (int i = 0; i < 3; i++) {
            to = (to - 1 + direction + SQUARE_COUNT) % SQUARE_COUNT + 1;
            if (gameData.square(to).mandatoryStop()) break;
        }
        drawer.setPosition(to);
        events.add(new PieceMoved(drawer.getPlayerId(), from, to));
        events.addAll(engine.land(state, drawer, dice, false));
        for (var other : state.playersFrom(drawer.getPlayerId()).stream().skip(1).toList()) {
            if (!other.isOut() && other.getMissedTurns() == 0) events.addAll(moveOneStep(state, other, true, dice));
        }
        return List.copyOf(events);
    }

    List<GameEvent> moveOneStep(GameState state, PlayerState player, boolean forward, Dice dice) {
        var from = player.getPosition();
        var to = forward ? from % SQUARE_COUNT + 1 : (from + SQUARE_COUNT - 2) % SQUARE_COUNT + 1;
        player.setPosition(to);
        var events = new ArrayList<GameEvent>();
        events.add(new PieceMoved(player.getPlayerId(), from, to));
        events.addAll(engine.land(state, player, dice, false));
        return List.copyOf(events);
    }

    List<GameEvent> energyTax(GameState state, PlayerState drawer, Dice dice) {
        var events = new ArrayList<GameEvent>();
        for (var target : state.playersFrom(drawer.getPlayerId())) {
            if (target.isOut()) continue;
            var built = state.getProperties().stream().filter(p -> target.getPlayerId().equals(p.getOwner()) && p.isBuilt()).toList();
            if (built.isEmpty()) continue;
            var passedBankEntrance = target.getPosition() < BANK_FIRST_SQUARE;
            var amount = built.stream().mapToInt(p -> INDUSTRIAL_SQUARES.contains(p.getSquare()) ? 30_000 : 20_000).sum();
            events.addAll(engine.moveTo(state, target, 1, dice));
            if (passedBankEntrance) {
                var roll = rollDice(dice, 2);
                events.add(new BankEntranceRoll(target.getPlayerId(), roll));
                events.add(payments.fromBank(target, rules.bankEntranceReward(state, roll), MoneyReason.BANK_ENTRANCE_REWARD));
            }
            events.addAll(payments.charge(state, target, null, List.of(new Charge(amount, MoneyReason.FINANCE_NEWS))));
        }
        return List.copyOf(events);
    }

    static int roundUp500(int amount, int divisor) {
        var value = (amount + divisor - 1) / divisor;
        return ((value + 499) / 500) * 500;
    }
}
