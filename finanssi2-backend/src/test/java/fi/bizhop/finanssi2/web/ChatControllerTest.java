package fi.bizhop.finanssi2.web;

import fi.bizhop.finanssi2.db.ChatRepository;
import fi.bizhop.finanssi2.db.ChatMessage;
import fi.bizhop.finanssi2.service.MessagingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
}
