package fi.bizhop.finanssi2.game.engine;

import java.util.random.RandomGenerator;

public class RandomDice implements Dice {
    final RandomGenerator random;

    public RandomDice(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public int roll() {
        return random.nextInt(1, 7);
    }
}
