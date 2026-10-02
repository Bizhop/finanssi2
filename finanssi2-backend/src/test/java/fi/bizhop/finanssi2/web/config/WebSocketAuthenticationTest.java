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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "finanssi2.debug.allowed-emails=a@example.com,b@example.com")
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

    @MockitoBean
    fi.bizhop.finanssi2.game.db.GameRepository gameRepository;

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

    static StompFrameHandler receiver(LinkedBlockingQueue<String> received) {
        return new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { received.add((String) payload); }
        };
    }

    void verifiedToken(String uid) {
        var token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn(uid);
        when(token.getEmail()).thenReturn(uid + "@example.com");
        when(token.getClaims()).thenReturn(java.util.Map.of("email_verified", true));
        when(tokenVerifier.verifyAuthorizationHeader("Bearer " + uid)).thenReturn(Optional.of(token));
    }

    @Test
    void privateGameSubscriptionsRequireVerifiedOwnerAndCannotSendUpdates() throws Exception {
        var id = "66f9a1b2c3d4e5f607182931";
        var game = new fi.bizhop.finanssi2.game.db.Game(fi.bizhop.finanssi2.game.db.GameMode.DEBUG);
        game.setCreator("a");
        when(gameRepository.findById(id)).thenReturn(Optional.of(game));
        verifiedToken("b");
        var nonowner = connect("Bearer b");
        var received = new LinkedBlockingQueue<String>();
        nonowner.subscribe("/topic/games/" + id, receiver(received));
        var error = errorFrames.poll(5, TimeUnit.SECONDS);
        assertTrue(error != null && error.contains("Game subscription denied"), "ERROR frame: " + error);
        assertTrue(received.isEmpty());

        verifiedToken("a");
        var owner = connect("Bearer a");
        owner.subscribe("/topic/games/" + id, receiver(received));
        String message = null;
        for (int attempt = 0; attempt < 50 && message == null; attempt++) {
            messagingTemplate.convertAndSend("/topic/games/" + id, "private");
            message = received.poll(100, TimeUnit.MILLISECONDS);
        }
        assertEquals("private", message);
        owner.send("/topic/games/" + id, "forged update");
        error = errorFrames.poll(5, TimeUnit.SECONDS);
        assertTrue(error != null && error.contains("Game updates can only be sent through REST"), "ERROR frame: " + error);
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
