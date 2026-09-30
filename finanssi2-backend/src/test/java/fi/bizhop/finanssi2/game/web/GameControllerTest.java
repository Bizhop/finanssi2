package fi.bizhop.finanssi2.game.web;

import fi.bizhop.finanssi2.db.ChatRepository;
import fi.bizhop.finanssi2.game.db.Game;
import fi.bizhop.finanssi2.game.db.GameLogEntry;
import fi.bizhop.finanssi2.game.db.GameLogRepository;
import fi.bizhop.finanssi2.game.db.GameRepository;
import fi.bizhop.finanssi2.game.db.GameStatus;
import fi.bizhop.finanssi2.game.engine.GameEvent;
import fi.bizhop.finanssi2.game.engine.PlayerState;
import fi.bizhop.finanssi2.service.MessagingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

import static fi.bizhop.finanssi2.web.SecurityConfig.TEST_USER_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class GameControllerTest {
    static final String GAME_ID = "66f9a1b2c3d4e5f607182931";

    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate restTemplate;
    @MockitoBean
    MessagingService messagingService;
    @MockitoBean
    ChatRepository chatRepository;
    @MockitoBean
    GameRepository gameRepository;
    @MockitoBean
    GameLogRepository gameLogRepository;

    String url(String path) {
        return String.format("http://localhost:%d/api/games%s", port, path);
    }

    ResponseEntity<JsonNode> post(String path, String uid) {
        var headers = new HttpHeaders();
        headers.add(TEST_USER_HEADER, uid);
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(headers), JsonNode.class);
    }

    Game lobby(String... uids) {
        var game = new Game();
        game.setId(GAME_ID);
        game.setVersion(1L);
        game.setCreator(uids[0]);
        for (int i = 0; i < uids.length; i++) {
            game.getState().getPlayers().add(new PlayerState(uids[i], "Player " + uids[i], null, i, 0, 0));
        }
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        return game;
    }

    void saveSucceeds() {
        when(gameRepository.save(any())).thenAnswer(invocation -> {
            var game = invocation.<Game>getArgument(0);
            game.setVersion(game.getVersion() + 1);
            return game;
        });
        when(gameLogRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void testStartReturnsGameWithoutDeckOrder() {
        lobby("a", "b");
        saveSucceeds();

        var response = post("/" + GAME_ID + "/start", "a");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var game = response.getBody();
        assertEquals("RUNNING", game.get("status").asString());
        assertEquals(2, game.get("state").get("turnOrder").size());
        assertEquals(75_000, game.get("state").get("players").get(0).get("cash").asInt());
        assertFalse(game.get("state").has("financeNewsDeck"));
    }

    @Test
    void testRuleViolationIsConflict() {
        lobby("a");

        var response = post("/" + GAME_ID + "/start", "a");

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().get("detail").asString().contains("players"));
    }

    @Test
    void testNonCreatorStartingIsForbidden() {
        lobby("a", "b");
        assertEquals(HttpStatus.FORBIDDEN, post("/" + GAME_ID + "/start", "b").getStatusCode());
    }

    @Test
    void testMissingGameIsNotFound() {
        when(gameRepository.findById("missing")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, post("/missing/join", "a").getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, restTemplate.getForEntity(url("/missing"), String.class).getStatusCode());
    }

    @Test
    void testConcurrentChangeIsConflict() {
        lobby("a");
        when(gameRepository.save(any())).thenThrow(new OptimisticLockingFailureException("version 1 changed"));

        assertEquals(HttpStatus.CONFLICT, post("/" + GAME_ID + "/join", "b").getStatusCode());
    }

    @Test
    void testEventsCarryTheirType() {
        lobby("a");
        var entry = new GameLogEntry("e1", GAME_ID, 1, 1000L, "PlayerJoined", new GameEvent.PlayerJoined("a", "Player a", 0));
        when(gameLogRepository.findByGameIdAndSeqGreaterThanOrderBySeq(GAME_ID, 0)).thenReturn(List.of(entry));

        var response = restTemplate.getForEntity(url("/" + GAME_ID + "/events"), JsonNode.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var event = response.getBody().get(0).get("event");
        assertEquals("PlayerJoined", event.get("type").asString());
        assertEquals("a", event.get("player").asString());
    }

    @Test
    void testInvalidEventsParameter() {
        for (var query : List.of("?after=-1", "?after=abc")) {
            assertEquals(HttpStatus.BAD_REQUEST, restTemplate.getForEntity(url("/" + GAME_ID + "/events" + query), String.class)
                    .getStatusCode(), query);
        }
    }

    @Test
    void testList() {
        var game = lobby("a");
        game.setStatus(GameStatus.LOBBY);
        when(gameRepository.findByStatusOrPlayer(any(), any(), any())).thenReturn(List.of(game));
        var headers = new HttpHeaders();
        headers.add(TEST_USER_HEADER, "b");

        var response = restTemplate.exchange(url(""), HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(GAME_ID, response.getBody().get(0).get("id").asString());
    }
}
