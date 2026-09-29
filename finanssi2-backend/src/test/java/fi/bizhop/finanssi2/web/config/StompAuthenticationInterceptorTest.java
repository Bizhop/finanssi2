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
    void testSubscribeRequiresAuthenticatedSession() {
        var anonymous = frame(StompHeaderAccessor.create(StompCommand.SUBSCRIBE));
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(anonymous, channel));

        var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(new FirebaseAuthenticationToken(mock(FirebaseToken.class)));
        var authenticated = frame(accessor);
        assertSame(authenticated, interceptor.preSend(authenticated, channel));
    }
}
