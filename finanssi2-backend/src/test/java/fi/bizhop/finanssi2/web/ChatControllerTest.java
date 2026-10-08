package fi.bizhop.finanssi2.web;

import fi.bizhop.finanssi2.db.ChatMessage;
import fi.bizhop.finanssi2.db.ChatRepository;
import fi.bizhop.finanssi2.game.db.Game;
import fi.bizhop.finanssi2.game.db.GameRepository;
import fi.bizhop.finanssi2.game.engine.PlayerState;
import fi.bizhop.finanssi2.security.User;
import fi.bizhop.finanssi2.service.MessagingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
public class ChatControllerTest {
    static final String OLDEST_LOADED_ID = "0000000000000000010";

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

    String url(String query) {
        return String.format("http://localhost:%d/api/chat%s", port, query);
    }

    @Test
    public void testNewestPageWithDefaultSize() {
        var newest = new ChatMessage("0000000000000000011", "tester@example.com", "Tester Example", "Hello", 1000L, "");
        when(chatRepository.findAllByOrderByIdDesc(Limit.of(20))).thenReturn(List.of(newest));

        var response = restTemplate.getForEntity(url(""), ChatMessage[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(newest), List.of(response.getBody()));
    }

    @Test
    public void testOlderPage() {
        var older = new ChatMessage("0000000000000000009", "tester@example.com", null, "Older", 500L, "");
        when(chatRepository.findByIdLessThanOrderByIdDesc(OLDEST_LOADED_ID, Limit.of(5))).thenReturn(List.of(older));

        var response = restTemplate.getForEntity(url("?before=" + OLDEST_LOADED_ID + "&size=5"), ChatMessage[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(older), List.of(response.getBody()));
    }

    @Test
    public void testInvalidParameters() {
        for (var query : List.of("?size=0", "?size=101", "?size=abc", "?before=not-an-id")) {
            var response = restTemplate.getForEntity(url(query), String.class);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), query);
        }
        verifyNoInteractions(chatRepository);
    }

    @Test
    public void testPageSizeLimits() {
        when(chatRepository.findAllByOrderByIdDesc(any())).thenReturn(List.of());

        for (var size : List.of(1, 100)) {
            var response = restTemplate.getForEntity(url("?size=" + size), String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "size=" + size);
        }
    }

    @Test
    void gameChatAllowsRoomReadsButOnlySeatsCanPost() {
        var player = new User(java.util.UUID.randomUUID().toString(), "player@example.com", "Player", null);
        var id = "66f9a1b2-c3d4-5e6f-8718-2931a2b3c4d5";
        var game = new Game();
        game.setId(id);
        game.getState().getPlayers().add(new PlayerState(player.userId(), player.name(), null, 0));
        when(gameRepository.findById(id)).thenReturn(java.util.Optional.of(game));
        when(gameRepository.findByIdForUpdate(id)).thenReturn(java.util.Optional.of(game));
        var saved = new ChatMessage("0000000000000000012", player.userId(), player.name(), "Hello game", 1000L, null);
        when(chatRepository.save(any(ChatMessage.class), eq(java.util.UUID.fromString(id)), eq(java.util.UUID.fromString(player.userId())))).thenReturn(saved);
        when(chatRepository.findGameMessages(eq(java.util.UUID.fromString(id)), eq(null), any()))
                .thenReturn(List.of(saved));

        var playerHeaders = headersFor(player.userId());
        var posted = restTemplate.postForEntity(gameChatUrl(id), new HttpEntity<>("{\"message\":\"Hello game\"}", playerHeaders), String.class);
        assertEquals(HttpStatus.OK, posted.getStatusCode(), posted.getBody());
        assertTrue(posted.getBody().contains("\"message\":\"Hello game\""));
        assertTrue(posted.getBody().contains("\"userId\":\"" + player.userId() + "\""), posted.getBody());

        var viewerHeaders = headersFor("viewer");
        var history = restTemplate.exchange(gameChatUrl(id), org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(viewerHeaders), String.class);
        assertEquals(HttpStatus.OK, history.getStatusCode());
        assertTrue(history.getBody().contains("\"message\":\"Hello game\""));
        var denied = restTemplate.postForEntity(gameChatUrl(id),
                new HttpEntity<>("{\"message\":\"Viewer message\"}", viewerHeaders), String.class);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        var invalid = restTemplate.postForEntity(gameChatUrl(id),
                new HttpEntity<>("{\"message\":\"   \"}", playerHeaders), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
    }

    private String gameChatUrl(String id) {
        return String.format("http://localhost:%d/api/games/%s/chat", port, id);
    }

    private HttpHeaders headersFor(String uid) {
        var headers = new HttpHeaders();
        headers.set("X-Test-User", uid);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }
}
