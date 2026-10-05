package fi.bizhop.finanssi2.game.db;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import fi.bizhop.finanssi2.game.engine.GameEvent;
import fi.bizhop.finanssi2.game.engine.GameState;
import fi.bizhop.finanssi2.game.engine.PendingDecision;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Arrays;

@Configuration
public class PersistenceJsonConfig {
    abstract static class GameStateMixin {
        @JsonIgnore(false) abstract List<String> getFinanceNewsDeck();
        @JsonIgnore(false) abstract List<String> getStockTipDeck();
    }

    abstract static class PendingDecisionMixin {
        @JsonIgnore(false) abstract List<PendingDecision.Bid> bids();
    }

    @Bean
    JsonMapper persistenceJsonMapper() {
        return JsonMapper.builder()
                .addMixIn(GameState.class, GameStateMixin.class)
                .addMixIn(PendingDecision.BondAuction.class, PendingDecisionMixin.class)
                .addMixIn(PendingDecision.AssetAuction.class, PendingDecisionMixin.class)
                .build();
    }

    @Bean("persistenceEventMapper")
    ObjectMapper persistenceEventMapper() {
        var mapper = new ObjectMapper();
        var subtypes = Arrays.stream(GameEvent.class.getPermittedSubclasses())
                .map(type -> new NamedType(type, type.getSimpleName())).toArray(NamedType[]::new);
        mapper.registerSubtypes(subtypes);
        return mapper;
    }
}
