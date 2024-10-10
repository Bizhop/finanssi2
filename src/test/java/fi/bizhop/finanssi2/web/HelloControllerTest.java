package fi.bizhop.finanssi2.web;

import fi.bizhop.finanssi2.db.ChatRepository;
import fi.bizhop.finanssi2.service.MessagingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class HelloControllerTest {
    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate restTemplate;
    @MockBean
    MessagingService messagingService;
    @MockBean
    ChatRepository chatRepository;

    @Test
    public void testHello() {
        var response = restTemplate.getForObject(String.format("http://localhost:%d/api/hello", port), String.class);
        assertEquals("Hello, world!", response);
    }
}
