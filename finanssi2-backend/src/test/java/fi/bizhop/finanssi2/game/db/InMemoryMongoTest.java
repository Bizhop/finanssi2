package fi.bizhop.finanssi2.game.db;

import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.junit.jupiter.api.AfterAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Against mongo-java-server, an in-memory server speaking the MongoDB wire protocol */
class InMemoryMongoTest extends GameMongoTests {
    static final MongoServer server = new MongoServer(new MemoryBackend());

    @DynamicPropertySource
    static void mongoUri(DynamicPropertyRegistry registry) {
        var address = server.bind();
        registry.add("spring.mongodb.uri", () -> "mongodb://" + address.getHostString() + ":" + address.getPort() + "/finanssi");
    }

    @AfterAll
    static void stop() {
        server.shutdown();
    }
}
