package fi.bizhop.finanssi2.game.service;

import fi.bizhop.finanssi2.game.engine.Dice;
import fi.bizhop.finanssi2.game.engine.RandomDice;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

@Configuration
public class GameRandomConfig {
    /** Shuffles the decks */
    @Bean
    public RandomGenerator gameRandom() {
        return new SecureRandom();
    }

    @Bean
    public Dice dice(RandomGenerator gameRandom) {
        return new RandomDice(gameRandom);
    }
}
