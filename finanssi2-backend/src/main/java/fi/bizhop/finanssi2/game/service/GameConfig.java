package fi.bizhop.finanssi2.game.service;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.engine.GameEngine;
import fi.bizhop.finanssi2.game.engine.GameSetup;
import fi.bizhop.finanssi2.game.engine.RandomDice;
import fi.bizhop.finanssi2.game.engine.Rules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/** The engine is plain Java; this makes its parts available as beans */
@Configuration
public class GameConfig {
    /** Rolls dice and shuffles the decks */
    @Bean
    public RandomGenerator gameRandom() {
        return new SecureRandom();
    }

    @Bean
    @Profile("!dev")
    public DiceSource diceSource(RandomGenerator gameRandom) {
        var dice = new RandomDice(gameRandom);
        return gameId -> dice;
    }

    @Bean
    public Rules rules(GameData gameData) {
        return new Rules(gameData);
    }

    @Bean
    public GameEngine gameEngine(GameData gameData, Rules rules) {
        return new GameEngine(gameData, rules);
    }

    @Bean
    public GameSetup gameSetup(GameData gameData) {
        return new GameSetup(gameData);
    }
}
