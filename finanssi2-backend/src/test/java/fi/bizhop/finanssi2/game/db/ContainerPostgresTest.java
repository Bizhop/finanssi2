package fi.bizhop.finanssi2.game.db;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

@Tag("container")
@Testcontainers
class ContainerPostgresTest extends GamePostgresTests {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");
}
