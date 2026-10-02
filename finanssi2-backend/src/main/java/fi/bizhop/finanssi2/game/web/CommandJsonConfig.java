package fi.bizhop.finanssi2.game.web;

import fi.bizhop.finanssi2.game.engine.GameCommand;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.DeserializationFeature;

import java.util.List;

/** Command request bodies reject unknown fields, so a misspelled field is a 400 rather than a silently defaulted value */
@Configuration
public class CommandJsonConfig implements WebMvcConfigurer {
    static final List<Class<?>> STRICT_TYPES = List.of(GameCommand.class, DebugGameController.CommandRequest.class);

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConverters(converter -> {
            if (!(converter instanceof JacksonJsonHttpMessageConverter json)) return;
            var strict = json.getMapper().rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
            STRICT_TYPES.forEach(type -> json.registerMappersForType(type, mappers -> mappers.put(MediaType.APPLICATION_JSON, strict)));
        });
    }
}
