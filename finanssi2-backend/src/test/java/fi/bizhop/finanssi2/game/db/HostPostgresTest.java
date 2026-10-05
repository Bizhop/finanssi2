package fi.bizhop.finanssi2.game.db;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Tag("database")
class HostPostgresTest extends GamePostgresTests {
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv().getOrDefault("DATABASE_URL",
                "jdbc:postgresql://host.docker.internal:5432/finanssi"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("DATABASE_USERNAME", "finanssi"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("DATABASE_PASSWORD", "finanssi"));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }
}
