package fi.bizhop.finanssi2.game.db;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("container")
@Testcontainers
class ContainerPostgresTest extends GamePostgresTests {
    @Container
    @ServiceConnection
    // Same minor version as the production Aurora cluster
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.11");
}
