package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.engine.GameEvent.DiceRolled;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import static fi.bizhop.finanssi2.game.data.GameConstants.BROKERAGE_FEE_STEP;
import static fi.bizhop.finanssi2.game.data.GameConstants.BROKERAGE_FEE_TO_BANK;
import static fi.bizhop.finanssi2.game.data.GameConstants.MAX_BROKERAGE_FEE;

/** Calling a shareholders meeting to take over a business group. */
class ShareholdersMeetings {
    final GameEngine engine;
    final GameData gameData;
    final Payments payments;

    ShareholdersMeetings(GameEngine engine) {
        this.engine = engine;
        this.gameData = engine.gameData;
        this.payments = engine.payments;
    }

    boolean ownershipOwnsGroupAsset(GameState state, String playerId, String group) {
        var groupData = gameData.group(group);
        return groupData.properties().stream().anyMatch(square -> playerId.equals(state.property(square).getOwner()))
                || gameData.sharesOf(group).stream().anyMatch(share -> playerId.equals(state.share(share.id()).getOwner()));
    }

    boolean groupBoughtFromBank(GameState state, String group) {
        return gameData.group(group).properties().stream().allMatch(square -> state.property(square).getOwner() != null)
                && gameData.sharesOf(group).stream().allMatch(share -> state.share(share.id()).getOwner() != null);
    }

    boolean groupHasOtherOwner(GameState state, String playerId, String group) {
        return groupDataHasActiveOwner(state, group, playerId);
    }

    boolean groupDataHasActiveOwner(GameState state, String group, String except) {
        var groupData = gameData.group(group);
        return groupData.properties().stream().map(square -> state.property(square).getOwner())
                .filter(owner -> owner != null && !owner.equals(except)).anyMatch(owner -> state.player(owner).filter(p -> !p.isOut()).isPresent())
                || gameData.sharesOf(group).stream().map(share -> state.share(share.id()).getOwner())
                .filter(owner -> owner != null && !owner.equals(except)).anyMatch(owner -> state.player(owner).filter(p -> !p.isOut()).isPresent());
    }

    int shareholdersMeetingTakeoverSum(GameState state, String group, String caller) {
        var total = 0;
        for (var square : gameData.group(group).properties()) {
            var property = state.property(square);
            if (property.getOwner() != null && !caller.equals(property.getOwner())) {
                var deed = gameData.titleDeed(square);
                total += deed.value(property.isBuilt());
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
        var success = brokerageFee == MAX_BROKERAGE_FEE;
        if (!success) {
            diceResult.add(dice.roll());
            diceResult.add(dice.roll());
            success = diceResult.stream().mapToInt(Integer::intValue).sum() <= brokerageFee / BROKERAGE_FEE_STEP;
        }
        var takeoverSum = shareholdersMeetingTakeoverSum(state, group, caller.getPlayerId());
        var events = new ArrayList<GameEvent>();
        if (!diceResult.isEmpty()) events.add(new GameEvent.DiceRolled(caller.getPlayerId(), diceResult));
        events.add(new GameEvent.ShareholdersMeetingResolved(caller.getPlayerId(), group, brokerageFee, takeoverSum,
                diceResult, success));
        if (!success) {
            events.add(payments.toBank(caller, brokerageFee, MoneyReason.SHAREHOLDERS_MEETING));
            return List.copyOf(events);
        }

        var sellerIds = new LinkedHashSet<String>();
        for (var square : gameData.group(group).properties()) {
            var property = state.property(square);
            if (property.getOwner() != null && !caller.getPlayerId().equals(property.getOwner())) sellerIds.add(property.getOwner());
        }
        for (var share : gameData.sharesOf(group)) {
            var owner = state.share(share.id()).getOwner();
            if (owner != null && !caller.getPlayerId().equals(owner)) sellerIds.add(owner);
        }
        var baseBankFee = Math.min(BROKERAGE_FEE_TO_BANK, brokerageFee);
        var shareOfFee = (brokerageFee - baseBankFee) / (double) sellerIds.size();
        var brokeragePayouts = sellerIds.stream().collect(Collectors.toMap(playerId -> playerId,
                playerId -> (int) (Math.floor((shareOfFee + 250) / 500) * 500), (a, b) -> a, LinkedHashMap::new));
        for (var square : gameData.group(group).properties()) {
            var property = state.property(square);
            if (property.getOwner() == null || caller.getPlayerId().equals(property.getOwner())) continue;
            var seller = state.player(property.getOwner()).orElseThrow();
            var deed = gameData.titleDeed(square);
            var price = deed.value(property.isBuilt());
            events.add(payments.transfer(caller, seller, price, MoneyReason.SHAREHOLDERS_MEETING));
            var previousOwner = property.getOwner();
            property.setOwner(caller.getPlayerId());
            events.add(new GameEvent.AssetTransferred(previousOwner, caller.getPlayerId(), "P:" + square));
        }
        for (var share : gameData.sharesOf(group)) {
            var shareState = state.share(share.id());
            if (shareState.getOwner() == null || caller.getPlayerId().equals(shareState.getOwner())) continue;
            var seller = state.player(shareState.getOwner()).orElseThrow();
            events.add(payments.transfer(caller, seller, share.value(), MoneyReason.SHAREHOLDERS_MEETING));
            var previousOwner = shareState.getOwner();
            shareState.setOwner(caller.getPlayerId());
            events.add(new GameEvent.AssetTransferred(previousOwner, caller.getPlayerId(), "S:" + share.id()));
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
}
