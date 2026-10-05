package fi.bizhop.finanssi2.game.web;

import fi.bizhop.finanssi2.db.ChatRepository;
import fi.bizhop.finanssi2.game.db.*;
import fi.bizhop.finanssi2.game.engine.ScriptedDice;
import fi.bizhop.finanssi2.game.service.DiceSource;
import fi.bizhop.finanssi2.service.MessagingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static fi.bizhop.finanssi2.web.SecurityConfig.TEST_USER_HEADER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "finanssi2.debug.allowed-emails=a@example.com,b@example.com")
@AutoConfigureTestRestTemplate
@ActiveProfiles({"test", "dev"})
class DebugGameControllerTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @MockitoBean GameRepository games;
    @MockitoBean GameLogRepository history;
    @MockitoBean ChatRepository chat;
    @MockitoBean MessagingService messaging;
    @MockitoBean DiceSource dice;

    final Map<String, Game> stored = new LinkedHashMap<>();

    @BeforeEach
    void repository() {
        when(games.saveWithEvents(any(), any())).thenAnswer(call -> {
            var game = call.<Game>getArgument(0);
            game.setVersion(game.getVersion() == null ? 0 : game.getVersion() + 1);
            stored.put(game.getId(), game);
            return game;
        });
        when(games.findById(anyString())).thenAnswer(call -> Optional.ofNullable(stored.get(call.<String>getArgument(0))));
        when(games.findByStatusOrPlayer(any(), anyString(), any())).thenAnswer(call -> List.copyOf(stored.values()));
        doAnswer(call -> { stored.remove(call.<Game>getArgument(0).getId()); return null; }).when(games).delete(any(Game.class));
        when(dice.forGame(any())).thenReturn(new ScriptedDice(6, 6, 1, 1));
    }

    ResponseEntity<JsonNode> request(HttpMethod method, String path, String user, String body) {
        var headers = new HttpHeaders();
        headers.add(TEST_USER_HEADER, user);
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("http://localhost:" + port + path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    String create(String user) {
        var result = request(HttpMethod.POST, "/api/debug/games", user, "{}");
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("DEBUG", result.getBody().get("mode").asString());
        assertEquals(2, result.getBody().get("state").get("players").size());
        return result.getBody().get("id").asString();
    }

    @Test
    void capabilitiesCreationAndPrivateLifecycle() {
        assertFalse(request(HttpMethod.GET, "/api/me/capabilities", "outsider", null).getBody().get("debugMode").asBoolean());
        assertTrue(request(HttpMethod.GET, "/api/me/capabilities", "a", null).getBody().get("debugMode").asBoolean());
        assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.POST, "/api/debug/games", "outsider", "{}").getStatusCode());
        assertEquals(HttpStatus.CONFLICT, request(HttpMethod.POST, "/api/debug/games", "a", "{\"playerCount\":1}").getStatusCode());
        var id = create("a");
        assertEquals(0, request(HttpMethod.GET, "/api/games", "b", null).getBody().size());
        for (var path : List.of("/api/games/" + id, "/api/games/" + id + "/events")) {
            assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.GET, path, "b", null).getStatusCode());
        }
        for (var suffix : List.of("join", "leave", "start")) {
            assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.POST, "/api/games/" + id + "/" + suffix, "b", null).getStatusCode());
        }
        assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.DELETE, "/api/debug/games/" + id, "b", null).getStatusCode());
        verify(messaging, never()).send(eq("/topic/games"), any());
        var deleted = request(HttpMethod.DELETE, "/api/debug/games/" + id, "a", null);
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());
        assertNull(deleted.getBody());
        assertEquals(HttpStatus.NOT_FOUND, request(HttpMethod.GET, "/api/games/" + id, "a", null).getStatusCode());
        verify(games).delete(any(Game.class));
    }

    @Test
    void viewsCommandsConflictsAndCardRoutes() {
        var id = create("a");
        assertEquals(HttpStatus.OK, request(HttpMethod.POST, "/api/games/" + id + "/start", "a", null).getStatusCode());
        var view = request(HttpMethod.GET, "/api/games/" + id, "a", null).getBody();
        assertEquals("a", view.get("actingPlayer").asString());
        assertTrue(view.get("allowedCommands").toString().contains("Roll"));
        var version = stored.get(id).getVersion();
        var endpoint = "/api/debug/games/" + id + "/commands";
        assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.POST, "/api/games/" + id + "/commands", "a", "{\"type\":\"Roll\"}").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.POST, endpoint, "b",
                "{\"actor\":\"a\",\"expectedVersion\":" + version + ",\"command\":{\"type\":\"Roll\"}}").getStatusCode());
        assertEquals(HttpStatus.CONFLICT, request(HttpMethod.POST, endpoint, "a",
                "{\"actor\":\"wrong\",\"expectedVersion\":" + version + ",\"command\":{\"type\":\"Roll\"}}").getStatusCode());
        assertEquals(HttpStatus.CONFLICT, request(HttpMethod.POST, endpoint, "a",
                "{\"actor\":\"a\",\"expectedVersion\":-1,\"command\":{\"type\":\"Roll\"}}").getStatusCode());
        assertEquals(HttpStatus.CONFLICT, request(HttpMethod.POST, endpoint, "a",
                "{\"actor\":\"a\",\"expectedVersion\":" + version + ",\"command\":{\"type\":\"Roll\"},\"dice\":[7]}").getStatusCode());
        for (var body : List.of("\"command\":{\"type\":\"BuyProperty\",\"sqare\":11}", "\"command\":{\"type\":\"Roll\"},\"dise\":[6]")) {
            assertEquals(HttpStatus.BAD_REQUEST, request(HttpMethod.POST, endpoint, "a",
                    "{\"actor\":\"a\",\"expectedVersion\":" + version + "," + body + "}").getStatusCode(), body);
        }
        assertEquals(version, stored.get(id).getVersion());
        assertEquals(HttpStatus.OK, request(HttpMethod.PUT, "/api/debug/games/" + id + "/next-card", "a",
                "{\"deck\":\"FINANCE_NEWS\",\"card\":\"FL-05\",\"expectedVersion\":" + version + "}").getStatusCode());
        version = stored.get(id).getVersion();
        assertEquals(HttpStatus.OK, request(HttpMethod.POST, endpoint, "a",
                "{\"actor\":\"a\",\"expectedVersion\":" + version + ",\"command\":{\"type\":\"Roll\"},\"dice\":[1]}").getStatusCode());
        doThrow(new OptimisticLockingFailureException("other tab")).when(games).saveWithEvents(any(), any());
        version = stored.get(id).getVersion();
        assertEquals(HttpStatus.CONFLICT, request(HttpMethod.POST, endpoint, "a",
                "{\"actor\":\"a\",\"expectedVersion\":" + version + ",\"command\":{\"type\":\"EndTurn\"}}").getStatusCode());
    }

    @Test
    void normalCommandsRejectActingIdentityAndDiceFields() {
        var result = request(HttpMethod.POST, "/api/games", "a", null);
        var id = result.getBody().get("id").asString();
        for (var extra : List.of("\"actor\":\"b\"", "\"dice\":[6]", "\"expectedVersion\":0")) {
            assertEquals(HttpStatus.BAD_REQUEST, request(HttpMethod.POST, "/api/games/" + id + "/commands", "a",
                    "{\"type\":\"Roll\"," + extra + "}").getStatusCode());
        }
        assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.PUT, "/api/debug/games/" + id + "/next-card", "a",
                "{\"deck\":\"FINANCE_NEWS\",\"card\":\"FL-05\",\"expectedVersion\":0}").getStatusCode());
    }
}
