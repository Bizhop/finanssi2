package fi.bizhop.finanssi2.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HelloControllerTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate restTemplate;

    @Test
    public void testHello() {
        var url = String.format("http://localhost:%d/hello", port);
        var response = restTemplate.getForObject(url, String.class);
        assertEquals("Hello, world!", response);
    }
}
