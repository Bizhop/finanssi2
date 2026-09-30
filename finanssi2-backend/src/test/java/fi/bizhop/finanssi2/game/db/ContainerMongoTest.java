package fi.bizhop.finanssi2.game.db;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/** Against a real MongoDB in Docker. Not part of the normal build: run with {@code ./gradlew containerTest}. */
@Tag("container")
@Testcontainers
class ContainerMongoTest extends GameMongoTests {
    @Container
    @ServiceConnection
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:7");
}
