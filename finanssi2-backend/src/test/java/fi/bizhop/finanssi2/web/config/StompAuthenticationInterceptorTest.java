package fi.bizhop.finanssi2.web.config;

import com.google.firebase.auth.FirebaseToken;
import fi.bizhop.finanssi2.security.FirebaseAuthenticationToken;
import fi.bizhop.finanssi2.security.FirebaseTokenVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthenticationInterceptorTest {
    @Mock
    FirebaseTokenVerifier tokenVerifier;
    @Mock
    fi.bizhop.finanssi2.game.db.GameRepository gameRepository;
    @Mock
    fi.bizhop.finanssi2.game.service.DebugAccess debugAccess;
    @Mock
    MessageChannel channel;
    @InjectMocks
    StompAuthenticationInterceptor interceptor;

    static Message<byte[]> frame(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void testConnectWithValidTokenAuthenticatesSession() {
        var firebaseToken = mock(FirebaseToken.class);
        when(firebaseToken.getUid()).thenReturn("uid-1");
        when(tokenVerifier.verifyAuthorizationHeader("Bearer valid")).thenReturn(Optional.of(firebaseToken));
        var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid");
        var message = frame(accessor);

        assertSame(message, interceptor.preSend(message, channel));

        var user = assertInstanceOf(FirebaseAuthenticationToken.class, accessor.getUser());
        assertEquals("uid-1", user.getName());
    }

    @Test
    void testConnectWithoutValidTokenIsRejected() {
        when(tokenVerifier.verifyAuthorizationHeader(null)).thenReturn(Optional.empty());
        var message = frame(StompHeaderAccessor.create(StompCommand.CONNECT));

        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void gameSubscriptionsCheckAccessAndRejectWildcardAndSends() {
        var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(new FirebaseAuthenticationToken(mock(FirebaseToken.class)));
        var id = "66f9a1b2c3d4e5f607182931";
        accessor.setDestination("/topic/games/" + id);
        when(gameRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(frame(accessor), channel));
        accessor.setDestination("/topic/games/**");
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(frame(accessor), channel));
        accessor.setDestination("/topic/**");
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(frame(accessor), channel));
        var send = StompHeaderAccessor.create(StompCommand.SEND);
        send.setUser(accessor.getUser());
        send.setDestination("/topic/games/" + id);
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(frame(send), channel));
    }

    @Test
    void testSubscribeRequiresAuthenticatedSession() {
        var anonymous = frame(StompHeaderAccessor.create(StompCommand.SUBSCRIBE));
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(anonymous, channel));

        var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(new FirebaseAuthenticationToken(mock(FirebaseToken.class)));
        accessor.setDestination("/topic/chat");
        var authenticated = frame(accessor);
        assertSame(authenticated, interceptor.preSend(authenticated, channel));
    }
}
