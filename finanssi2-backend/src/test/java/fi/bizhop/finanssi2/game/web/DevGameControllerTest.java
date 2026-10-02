package fi.bizhop.finanssi2.game.web;

import fi.bizhop.finanssi2.db.ChatRepository;
import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.db.Game;
import fi.bizhop.finanssi2.game.db.GameLogRepository;
import fi.bizhop.finanssi2.game.db.GameRepository;
import fi.bizhop.finanssi2.game.db.GameStatus;
import fi.bizhop.finanssi2.game.engine.GameSetup;
import fi.bizhop.finanssi2.game.engine.PlayerState;
import fi.bizhop.finanssi2.game.engine.ScriptedDice;
import fi.bizhop.finanssi2.service.MessagingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.Random;

import static fi.bizhop.finanssi2.web.SecurityConfig.TEST_USER_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles({"test", "dev"})
class DevGameControllerTest {
    static final String GAME_ID = "66f9a1b2c3d4e5f607182931";

    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate restTemplate;
    @Autowired
    GameData gameData;
    @MockitoBean
    MessagingService messagingService;
    @MockitoBean
    ChatRepository chatRepository;
    @MockitoBean
    GameRepository gameRepository;
    @MockitoBean
    GameLogRepository gameLogRepository;

    ResponseEntity<JsonNode> post(String path, String json) {
        var headers = new HttpHeaders();
        headers.add(TEST_USER_HEADER, "a");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(String.format("http://localhost:%d/api/games/%s%s", port, GAME_ID, path), HttpMethod.POST,
                new HttpEntity<>(json, headers), JsonNode.class);
    }

    @Test
    void devProfileDoesNotExposeOldDiceRoute() {
        assertEquals(HttpStatus.NOT_FOUND, post("/dev/dice", "[3]").getStatusCode());
    }
}
