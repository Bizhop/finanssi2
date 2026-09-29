package fi.bizhop.finanssi2.web.config;

import com.google.firebase.auth.FirebaseToken;
import fi.bizhop.finanssi2.db.ChatRepository;
import fi.bizhop.finanssi2.security.FirebaseTokenVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Connects real STOMP clients to the embedded server's /ws endpoint */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketAuthenticationTest {
    @LocalServerPort
    int port;
    @Autowired
    SimpMessagingTemplate messagingTemplate;
    @MockitoBean
    FirebaseTokenVerifier tokenVerifier;
    @MockitoBean
    ChatRepository chatRepository;

    final WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

    WebSocketAuthenticationTest() {
        stompClient.setMessageConverter(new StringMessageConverter());
    }

    @AfterEach
    void stopClient() {
        stompClient.stop();
    }

    /** "message" headers of ERROR frames the server sent */
    final LinkedBlockingQueue<String> errorFrames = new LinkedBlockingQueue<>();

    StompSession connect(String authorizationHeader) throws Exception {
        var connectHeaders = new StompHeaders();
        if (authorizationHeader != null) {
            connectHeaders.add("Authorization", authorizationHeader);
        }
        var handler = new StompSessionHandlerAdapter() {
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errorFrames.add(String.valueOf(headers.getFirst("message")));
            }
        };
        return stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders, handler)
                .get(5, TimeUnit.SECONDS);
    }

    void assertRejected(String authorizationHeader) throws InterruptedException {
        assertThrows(ExecutionException.class, () -> connect(authorizationHeader));
        var error = errorFrames.poll(5, TimeUnit.SECONDS);
        assertTrue(error != null && error.contains("Missing, invalid or expired Firebase ID token"), "ERROR frame: " + error);
    }

    @Test
    void testConnectWithoutTokenIsRejected() throws InterruptedException {
        when(tokenVerifier.verifyAuthorizationHeader(any())).thenReturn(Optional.empty());

        assertRejected(null);
        assertRejected("Bearer invalid");
    }

    @Test
    void testAuthenticatedClientReceivesChatMessages() throws Exception {
        var firebaseToken = mock(FirebaseToken.class);
        // The session's user name, never null for real tokens
        when(firebaseToken.getUid()).thenReturn("uid-1");
        when(tokenVerifier.verifyAuthorizationHeader("Bearer valid")).thenReturn(Optional.of(firebaseToken));
        var session = connect("Bearer valid");

        var received = new LinkedBlockingQueue<String>();
        session.subscribe("/topic/chat", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((String) payload);
            }
        });

        // The subscription is registered asynchronously and the simple broker sends no receipts, so send until one arrives
        String message = null;
        for (int attempt = 0; attempt < 50 && message == null; attempt++) {
            messagingTemplate.convertAndSend("/topic/chat", "hello");
            message = received.poll(100, TimeUnit.MILLISECONDS);
        }
        assertEquals("hello", message);
    }
}
