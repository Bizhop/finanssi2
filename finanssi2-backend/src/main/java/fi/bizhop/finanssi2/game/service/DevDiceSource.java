package fi.bizhop.finanssi2.game.service;

import fi.bizhop.finanssi2.game.engine.Dice;
import fi.bizhop.finanssi2.game.engine.RandomDice;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.random.RandomGenerator;

/** Dev profile only: dice that return queued values for a game first, then random ones. For manual testing with the frontend. */
@Component
@Profile("dev")
public class DevDiceSource implements DiceSource {
    final Map<String, ConcurrentLinkedQueue<Integer>> queued = new ConcurrentHashMap<>();
    final RandomDice random;

    public DevDiceSource(RandomGenerator gameRandom) {
        random = new RandomDice(gameRandom);
    }

    public void queue(String gameId, List<Integer> values) {
        queued.computeIfAbsent(gameId, id -> new ConcurrentLinkedQueue<>()).addAll(values);
    }

    @Override
    public Dice forGame(String gameId) {
        return () -> {
            var queue = queued.get(gameId);
            var value = queue == null ? null : queue.poll();
            return value == null ? random.roll() : value;
        };
    }
}
