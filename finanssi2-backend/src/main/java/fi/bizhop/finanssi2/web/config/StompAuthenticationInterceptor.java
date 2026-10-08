package fi.bizhop.finanssi2.web.config;

import fi.bizhop.finanssi2.game.db.GameRepository;
import fi.bizhop.finanssi2.game.service.DebugAccess;
import fi.bizhop.finanssi2.security.FirebaseAuthenticationToken;
import fi.bizhop.finanssi2.security.FirebaseTokenVerifier;
import fi.bizhop.finanssi2.security.AuthenticatedUserService;
import org.jspecify.annotations.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.ConcurrentHashMap;


/**
 * Authenticates websocket clients: CONNECT must carry an {@code Authorization: Bearer <Firebase ID token>} header, and other frames
 * are accepted only from authenticated sessions. Clients may only subscribe to known topics and never send; all changes go through
 * REST. A rejected frame gets an ERROR frame and the connection is closed.
 */
@Component
public class StompAuthenticationInterceptor implements ChannelInterceptor {
    final FirebaseTokenVerifier tokenVerifier;
    final AuthenticatedUserService authenticatedUserService;
    final GameRepository gameRepository;
    final DebugAccess debugAccess;
    java.util.Map<String, FirebaseAuthenticationToken> sessions = new ConcurrentHashMap<>();

    @Autowired
    StompAuthenticationInterceptor(FirebaseTokenVerifier tokenVerifier, AuthenticatedUserService authenticatedUserService,
                                   GameRepository gameRepository, DebugAccess debugAccess) {
        this.tokenVerifier = tokenVerifier;
        this.authenticatedUserService = authenticatedUserService;
        this.gameRepository = gameRepository;
        this.debugAccess = debugAccess;
    }

    StompAuthenticationInterceptor(FirebaseTokenVerifier tokenVerifier, GameRepository gameRepository, DebugAccess debugAccess) {
        this(tokenVerifier, null, gameRepository, debugAccess);
    }

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
                final FirebaseAuthenticationToken auth;
                try {
                    auth = authenticatedUserService == null
                            ? new FirebaseAuthenticationToken(token)
                            : new FirebaseAuthenticationToken(token, authenticatedUserService.resolve(token));
                } catch (RuntimeException e) {
                    throw new MessageDeliveryException("Verified email and valid account required: " + e.getClass().getSimpleName());
                }
                accessor.setUser(auth);
                if (accessor.getSessionId() != null) sessions.put(accessor.getSessionId(), auth);
                yield message;
            }
            case SEND -> {
                // No @MessageMapping handlers: the simple broker would relay a client SEND unchecked to every subscriber
                throw new MessageDeliveryException(accessor.getUser() == null ? "Not authenticated"
                        : "Messages can only be sent through REST");
            }
            case SUBSCRIBE -> {
                if (accessor.getUser() == null) {
                    throw new MessageDeliveryException("Not authenticated");
                }
                var destination = accessor.getDestination();
                if (destination != null && destination.startsWith("/topic/games/")) {
                    var suffix = destination.substring("/topic/games/".length());
                    var chat = suffix.endsWith("/chat");
                    var id = chat ? suffix.substring(0, suffix.length() - "/chat".length()) : suffix;
                    // Literal game ids only; broker wildcard subscriptions and extra path segments are denied.
                    if (!id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                            || !(accessor.getUser() instanceof FirebaseAuthenticationToken auth)) {
                        throw new MessageDeliveryException("Invalid game subscription");
                    }
                    try {
                        var game = gameRepository.findById(id).orElseThrow();
                        debugAccess.requireRead(game, auth.user());
                        if (chat && game.getMode() == fi.bizhop.finanssi2.game.db.GameMode.DEBUG) {
                            debugAccess.requireOwner(game, auth.user());
                        }
                    } catch (RuntimeException e) {
                        throw new MessageDeliveryException(message, "Game subscription denied", e);
                    }
                } else if (!"/topic/games".equals(destination) && !"/topic/chat".equals(destination)) {
                    throw new MessageDeliveryException("Unknown topic");
                }
                yield message;
            }
            case DISCONNECT -> {
                if (accessor.getSessionId() != null) sessions.remove(accessor.getSessionId());
                yield message;
            }
            case UNSUBSCRIBE, ACK, NACK, BEGIN, COMMIT, ABORT, CONNECTED, RECEIPT, MESSAGE, ERROR -> message;
        };
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        sessions.remove(event.getSessionId());
    }
}
