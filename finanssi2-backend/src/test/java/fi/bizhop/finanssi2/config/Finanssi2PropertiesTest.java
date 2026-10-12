package fi.bizhop.finanssi2.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Finanssi2PropertiesTest {
    @Configuration
    @EnableConfigurationProperties(Finanssi2Properties.class)
    static class Config {}

    final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void bindsTheDocumentedKeys() {
        runner.withPropertyValues(
                "finanssi2.endpoints.whitelist=/api/health/live,/ws",
                "finanssi2.web.allowed-origin=https://example.test",
                "finanssi2.debug.allowed-emails=a@example.com, b@example.com",
                "finanssi2.firebase.credentials=key.json").run(context -> {
            var properties = context.getBean(Finanssi2Properties.class);
            assertEquals(List.of("/api/health/live", "/ws"), properties.endpoints().whitelist());
            assertEquals("https://example.test", properties.web().allowedOrigin());
            assertEquals(Set.of("a@example.com", "b@example.com"), properties.debug().allowedEmails());
            assertEquals(Path.of("key.json"), properties.firebase().credentials());
        });
    }

    @Test
    void emptyDebugEmailsDisableDebugMode() {
        runner.withPropertyValues("finanssi2.debug.allowed-emails=").run(context ->
                assertTrue(context.getBean(Finanssi2Properties.class).debug().allowedEmails().isEmpty()));
        runner.run(context -> assertTrue(context.getBean(Finanssi2Properties.class).debug().allowedEmails().isEmpty()));
    }
}
