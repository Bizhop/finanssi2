package fi.bizhop.finanssi2.game.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import fi.bizhop.finanssi2.game.engine.GameEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class PersistenceJsonConfig {
    @Bean("persistenceEventMapper")
    ObjectMapper persistenceEventMapper() {
        var mapper = new ObjectMapper();
        var subtypes = Arrays.stream(GameEvent.class.getPermittedSubclasses())
                .map(type -> new NamedType(type, type.getSimpleName())).toArray(NamedType[]::new);
        mapper.registerSubtypes(subtypes);
        return mapper;
    }
}
