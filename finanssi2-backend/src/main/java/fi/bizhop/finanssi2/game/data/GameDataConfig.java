package fi.bizhop.finanssi2.game.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/** Loads the game assets from {@code gamedata/} on the classpath; startup fails if they are inconsistent */
@Configuration
public class GameDataConfig {
    static final Logger logger = Logger.getLogger(GameDataConfig.class.getName());

    // Strict, so a typo in a transcribed field name fails startup instead of silently becoming null. Flags left out of a file
    // (e.g. industrial on non-property squares) are false.
    static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    @JsonIgnoreProperties({"_source", "_unverified"})
    record BoardFile(List<BusinessGroup> groups, List<Square> squares) {}

    @Bean
    public GameData gameData() throws IOException {
        var board = read("gamedata/pelilauta.json", new TypeReference<BoardFile>() {});
        var financeNews = read("gamedata/finanssilehdet.json", new TypeReference<List<Card>>() {});
        var gameData = new GameData(board.squares(), board.groups(), financeNews);
        var mockCards = financeNews.stream().filter(Card::mock).count();
        logger.info(String.format("Loaded game data: %d squares, %d groups, %d Finance News cards (%d mock)",
                board.squares().size(), board.groups().size(), financeNews.size(), mockCards));
        return gameData;
    }

    static <T> T read(String path, TypeReference<T> type) throws IOException {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return MAPPER.readValue(input, type);
        }
    }
}
