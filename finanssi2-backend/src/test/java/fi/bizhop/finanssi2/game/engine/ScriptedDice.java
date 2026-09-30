package fi.bizhop.finanssi2.game.engine;

import java.util.ArrayDeque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/** Dice that return the given values in order and fail the test when they run out */
public class ScriptedDice implements Dice {
    final ArrayDeque<Integer> values;

    public ScriptedDice(Integer... values) {
        this.values = new ArrayDeque<>(List.of(values));
    }

    @Override
    public int roll() {
        if (values.isEmpty()) {
            return fail("Scripted dice ran out");
        }
        return values.poll();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
