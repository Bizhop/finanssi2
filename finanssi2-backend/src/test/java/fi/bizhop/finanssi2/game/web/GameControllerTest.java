package fi.bizhop.finanssi2.game.web;

import fi.bizhop.finanssi2.db.ChatRepository;
import fi.bizhop.finanssi2.game.db.Game;
import fi.bizhop.finanssi2.game.db.GameLogEntry;
import fi.bizhop.finanssi2.game.db.GameLogRepository;
import fi.bizhop.finanssi2.game.db.GameRepository;
import fi.bizhop.finanssi2.game.db.GameStatus;
import fi.bizhop.finanssi2.game.engine.GameEvent;
import fi.bizhop.finanssi2.game.engine.GameSetup;
import fi.bizhop.finanssi2.game.engine.LoanLimit;
import fi.bizhop.finanssi2.game.engine.PlayerState;
import fi.bizhop.finanssi2.game.engine.ScriptedDice;
import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.service.DiceSource;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;
import java.util.Random;

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
    @MockitoBean
    DiceSource diceSource;
    @Autowired
    GameData gameData;

    String url(String path) {
        return String.format("http://localhost:%d/api/games%s", port, path);
    }

    static HttpHeaders user(String playerId) {
        var headers = new HttpHeaders();
        headers.add(TEST_USER_HEADER, playerId);
        return headers;
    }

    ResponseEntity<JsonNode> post(String path, String playerId) {
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(user(playerId)), JsonNode.class);
    }

    ResponseEntity<JsonNode> get(String path, String playerId) {
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(user(playerId)), JsonNode.class);
    }

    ResponseEntity<JsonNode> command(String json, String playerId) {
        var headers = user(playerId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url("/" + GAME_ID + "/commands"), HttpMethod.POST, new HttpEntity<>(json, headers),
                JsonNode.class);
    }

    /** A running game of a and b, a to roll */
    Game running() {
        var game = lobby("a", "b");
        game.setStatus(GameStatus.RUNNING);
        new GameSetup(gameData).start(game.getState(), new ScriptedDice(6, 6, 1, 1), new Random(1));
        return game;
    }

    Game lobby(String... uids) {
        var game = new Game();
        game.setId(GAME_ID);
        game.setVersion(1L);
        game.setCreator(uids[0]);
        for (int i = 0; i < uids.length; i++) {
            game.getState().getPlayers().add(new PlayerState(uids[i], "Player " + uids[i], null, i));
        }
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        return game;
    }

    void saveSucceeds() {
        when(gameRepository.saveWithEvents(any(), any())).thenAnswer(invocation -> {
            var game = invocation.<Game>getArgument(0);
            game.setVersion(game.getVersion() + 1);
            return game;
        });
    }

    @Test
    void testStartReturnsGameWithoutDeckOrder() {
        lobby("a", "b");
        saveSucceeds();
        when(diceSource.forGame(GAME_ID)).thenReturn(new ScriptedDice(6, 6, 1, 1));

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

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode(), String.valueOf(response.getBody()));
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
        assertEquals(HttpStatus.NOT_FOUND, get("/missing", "a").getStatusCode());
    }

    @Test
    void testConcurrentChangeIsConflict() {
        lobby("a");
        when(gameRepository.saveWithEvents(any(), any())).thenThrow(new OptimisticLockingFailureException("version 1 changed"));

        assertEquals(HttpStatus.CONFLICT, post("/" + GAME_ID + "/join", "b").getStatusCode());
    }

    @Test
    void testEventsCarryTheirType() {
        lobby("a");
        var entry = new GameLogEntry("e1", GAME_ID, 1, 1000L, "PlayerJoined", new GameEvent.PlayerJoined("a", "Player a", 0));
        when(gameLogRepository.findByGameIdAndSeqGreaterThanOrderBySeq(GAME_ID, 0)).thenReturn(List.of(entry));

        var response = get("/" + GAME_ID + "/events", "a");

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
    void testGetListsAllowedCommandsForTheUser() {
        running();

        var forA = get("/" + GAME_ID, "a").getBody();
        assertEquals(GAME_ID, forA.get("game").get("id").asString());
        assertEquals("[\"BuyCar\",\"EndGame\",\"Resign\",\"Roll\",\"TakeLoan\"]", forA.get("allowedCommands").toString());
        assertEquals("[\"Resign\"]", get("/" + GAME_ID, "b").getBody().get("allowedCommands").toString());
    }

    @Test
    void testRollCommand() {
        running();
        saveSucceeds();
        when(diceSource.forGame(GAME_ID)).thenReturn(new ScriptedDice(4));

        var response = command("{\"type\": \"Roll\"}", "a");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("DiceRolled", response.getBody().get(0).get("event").get("type").asString());
        assertEquals(4, response.getBody().get(0).get("event").get("dice").get(0).asInt());
    }

    @Test
    void testCommandStatusCodes() {
        running();
        assertEquals(HttpStatus.FORBIDDEN, command("{\"type\": \"Roll\"}", "b").getStatusCode());
        assertEquals(HttpStatus.CONFLICT, command("{\"type\": \"EndTurn\"}", "a").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, command("{\"type\": \"Cheat\"}", "a").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, command("{}", "a").getStatusCode());
        // Commands with parameters parse; the rules then reject them (not on square 11 or 17)
        assertEquals(HttpStatus.CONFLICT, command("{\"type\": \"BuyProperty\", \"square\": 3}", "a").getStatusCode());
        assertEquals(HttpStatus.CONFLICT, command("{\"type\": \"BuyShare\", \"share\": \"OS-KEMIA-1\"}", "a").getStatusCode());
        assertEquals(HttpStatus.CONFLICT, command("{\"type\": \"Build\", \"squares\": [3, 4]}", "a").getStatusCode());
    }

    @Test
    void testChangeSettings() {
        var game = lobby("a", "b");
        saveSucceeds();
        var headers = user("a");
        headers.setContentType(MediaType.APPLICATION_JSON);

        var response = restTemplate.exchange(url("/" + GAME_ID + "/settings"), HttpMethod.PUT,
                new HttpEntity<>("{\"loanLimit\": \"UNLIMITED\"}", headers), JsonNode.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UNLIMITED", response.getBody().get("state").get("settings").get("loanLimit").asString());
        assertEquals(LoanLimit.UNLIMITED, game.getState().getSettings().loanLimit());
        for (var json : List.of("{}", "{\"loanLimit\": \"SOME\"}")) {
            assertEquals(HttpStatus.BAD_REQUEST, restTemplate.exchange(url("/" + GAME_ID + "/settings"), HttpMethod.PUT,
                    new HttpEntity<>(json, headers), JsonNode.class).getStatusCode(), json);
        }
    }

    @Test
    void testList() {
        var game = lobby("a");
        game.setStatus(GameStatus.LOBBY);
        when(gameRepository.findByStatusOrPlayer(any(), any(), any())).thenReturn(List.of(game));
        var response = get("", "b");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(GAME_ID, response.getBody().get(0).get("id").asString());
    }
}
