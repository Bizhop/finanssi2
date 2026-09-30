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
import java.util.Map;
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

    @JsonIgnoreProperties({"_source", "_notes"})
    record BoardFile(List<BusinessGroup> groups, List<Square> squares) {}

    @JsonIgnoreProperties({"_source", "_notes"})
    record TitleDeedFile(List<TitleDeed> titleDeeds) {}

    @JsonIgnoreProperties({"_source", "_notes"})
    record ShareFile(Map<String, Integer> groupShareCapital, List<Share> shares) {}

    @Bean
    public GameData gameData() throws IOException {
        var assets = readAssets();
        var gameData = new GameData(assets);
        logger.info(String.format("Loaded game data: %d squares, %d groups, %d title deeds, %d shares, %d Finance News, %d Stock Tips",
                assets.squares().size(), assets.groups().size(), assets.titleDeeds().size(), assets.shares().size(),
                assets.financeNews().size(), assets.stockTips().size()));
        return gameData;
    }

    static GameAssets readAssets() throws IOException {
        var board = read("gamedata/pelilauta.json", new TypeReference<BoardFile>() {});
        var titleDeeds = read("gamedata/hallintatodistukset.json", new TypeReference<TitleDeedFile>() {});
        var shares = read("gamedata/osakkeet.json", new TypeReference<ShareFile>() {});
        var financeNews = read("gamedata/finanssilehdet.json", new TypeReference<List<Card>>() {});
        var stockTips = read("gamedata/porssivihjeet.json", new TypeReference<List<Card>>() {});
        return new GameAssets(board.squares(), board.groups(), titleDeeds.titleDeeds(), shares.shares(), shares.groupShareCapital(),
                financeNews, stockTips);
    }

    static <T> T read(String path, TypeReference<T> type) throws IOException {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return MAPPER.readValue(input, type);
        }
    }
}
