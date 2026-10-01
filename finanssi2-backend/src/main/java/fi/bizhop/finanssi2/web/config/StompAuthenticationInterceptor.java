package fi.bizhop.finanssi2.web.config;

import fi.bizhop.finanssi2.security.FirebaseAuthenticationToken;
import fi.bizhop.finanssi2.security.FirebaseTokenVerifier;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Authenticates websocket clients: CONNECT must carry an {@code Authorization: Bearer <Firebase ID token>} header, and other frames
 * are accepted only from authenticated sessions. A rejected CONNECT gets an ERROR frame and the connection is closed.
 */
@Component
@RequiredArgsConstructor
public class StompAuthenticationInterceptor implements ChannelInterceptor {
    final FirebaseTokenVerifier tokenVerifier;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        return switch (accessor.getCommand()) {
            case CONNECT, STOMP -> {
                var token = tokenVerifier.verifyAuthorizationHeader(accessor.getFirstNativeHeader("Authorization"))
                        .orElseThrow(() -> new MessageDeliveryException("Missing, invalid or expired Firebase ID token"));
                // Remembered for the rest of the session, so later frames carry it too
                accessor.setUser(new FirebaseAuthenticationToken(token));
                yield message;
            }
            case SUBSCRIBE, SEND -> {
                if (accessor.getUser() == null) {
                    throw new MessageDeliveryException("Not authenticated");
                }
                yield message;
            }
            case DISCONNECT, UNSUBSCRIBE, ACK, NACK, BEGIN, COMMIT, ABORT, CONNECTED, RECEIPT, MESSAGE, ERROR -> message;
        };
    }
}
