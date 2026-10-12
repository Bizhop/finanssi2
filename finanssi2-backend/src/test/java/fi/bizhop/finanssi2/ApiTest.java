package fi.bizhop.finanssi2;

import fi.bizhop.finanssi2.db.ApplicationUserRepository;
import fi.bizhop.finanssi2.db.ChatRepository;
import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.db.GameLogRepository;
import fi.bizhop.finanssi2.game.db.GameRepository;
import fi.bizhop.finanssi2.game.service.DiceSource;
import fi.bizhop.finanssi2.service.MessagingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Base for tests that call the running API with persistence and messaging mocked. Every such test shares this one configuration (the
 * same mock beans and properties), so Spring builds a single application context for all of them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "finanssi2.debug.allowed-emails=a@example.com,b@example.com")
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
public abstract class ApiTest {
    @LocalServerPort
    protected int port;
    @Autowired
    protected TestRestTemplate restTemplate;
    @Autowired
    protected GameData gameData;
    @MockitoBean
    protected MessagingService messagingService;
    @MockitoBean
    protected ChatRepository chatRepository;
    @MockitoBean
    protected GameRepository gameRepository;
    @MockitoBean
    protected GameLogRepository gameLogRepository;
    @MockitoBean
    protected ApplicationUserRepository users;
    @MockitoBean
    protected DiceSource diceSource;
}
