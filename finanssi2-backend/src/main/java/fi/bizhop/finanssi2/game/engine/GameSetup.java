package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.Card;
import fi.bizhop.finanssi2.game.data.Deck;
import fi.bizhop.finanssi2.game.data.GameData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

import static fi.bizhop.finanssi2.game.data.GameConstants.MAX_PLAYERS;
import static fi.bizhop.finanssi2.game.data.GameConstants.MIN_PLAYERS;
import static fi.bizhop.finanssi2.game.data.GameConstants.START_SQUARE;
import static fi.bizhop.finanssi2.game.data.GameConstants.STARTING_CASH;

/** Starting a game: cash and pieces, starting order and shuffled decks */
public class GameSetup {
    final GameData gameData;

    public GameSetup(GameData gameData) {
        this.gameData = gameData;
    }

    public List<GameEvent> start(GameState state, Dice dice, RandomGenerator random) {
        var players = state.getPlayers();
        if (players.size() < MIN_PLAYERS || players.size() > MAX_PLAYERS) {
            throw new RuleViolation("A game needs " + MIN_PLAYERS + "–" + MAX_PLAYERS + " players");
        }
        var events = new ArrayList<GameEvent>();

        // Everyone rolls both dice and the highest total starts; tied players roll again
        var contenders = players.stream().map(PlayerState::getUid).toList();
        for (int round = 1; contenders.size() > 1; round++) {
            var highest = 0;
            var tied = new ArrayList<String>();
            for (var uid : contenders) {
                var roll = List.of(dice.roll(), dice.roll());
                events.add(new GameEvent.StartingRoll(uid, round, roll));
                var total = roll.get(0) + roll.get(1);
                if (total > highest) {
                    highest = total;
                    tied.clear();
                }
                if (total == highest) {
                    tied.add(uid);
                }
            }
            contenders = tied;
        }

        // Play proceeds clockwise, which is join order, from the starter
        var uids = players.stream().map(PlayerState::getUid).toList();
        var starter = uids.indexOf(contenders.getFirst());
        var turnOrder = new ArrayList<String>();
        for (int i = 0; i < uids.size(); i++) {
            turnOrder.add(uids.get((starter + i) % uids.size()));
        }
        state.setTurnOrder(turnOrder);
        state.setCurrentPlayer(turnOrder.getFirst());
        state.setPhase(TurnPhase.BEFORE_ROLL);

        // No interest or Stock Tip for starting on square 1 (R12)
        for (var player : players) {
            player.setCash(STARTING_CASH);
            player.setPosition(START_SQUARE);
        }

        var financeNews = new ArrayList<>(gameData.cards(Deck.FINANCE_NEWS).stream().map(Card::id).toList());
        Collections.shuffle(financeNews, random);
        state.setFinanceNewsDeck(financeNews);

        events.add(new GameEvent.GameStarted(List.copyOf(turnOrder), STARTING_CASH));
        events.add(new GameEvent.TurnStarted(turnOrder.getFirst()));
        return events;
    }
}
