package fi.bizhop.finanssi2.web;

import fi.bizhop.finanssi2.db.ChatRepository;
import fi.bizhop.finanssi2.service.MessagingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
public class HelloControllerTest {
    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate restTemplate;
    @MockitoBean
    MessagingService messagingService;
    @MockitoBean
    ChatRepository chatRepository;

    @Test
    public void testHello() {
        var response = restTemplate.getForObject(String.format("http://localhost:%d/api/hello", port), String.class);
        assertEquals("Hello, world!", response);
    }
}
