package fi.bizhop.finanssi2.web.config;

import com.google.firebase.auth.FirebaseToken;
import fi.bizhop.finanssi2.game.db.Game;
import fi.bizhop.finanssi2.game.db.GameMode;
import fi.bizhop.finanssi2.game.db.GameRepository;
import fi.bizhop.finanssi2.game.service.DebugAccess;
import fi.bizhop.finanssi2.game.service.NotAllowedException;
import fi.bizhop.finanssi2.security.FirebaseAuthenticationToken;
import fi.bizhop.finanssi2.security.FirebaseTokenVerifier;
import fi.bizhop.finanssi2.security.User;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrivateChatDeliveryInterceptorTest {
    @Test
    void dropsPrivateChatWhenOwnerAccessHasBeenRevoked() {
        var gameRepository = mock(GameRepository.class);
        var debugAccess = mock(DebugAccess.class);
        var tokenVerifier = mock(FirebaseTokenVerifier.class);
        var authentication = new StompAuthenticationInterceptor(tokenVerifier, gameRepository, debugAccess);
        var firebaseToken = mock(FirebaseToken.class);
        when(firebaseToken.getUid()).thenReturn("owner");
        var auth = new FirebaseAuthenticationToken(firebaseToken);
        var user = new User("owner", null, null, null, false);
        authentication.sessions.put("session-1", auth);

        var id = "66f9a1b2-c3d4-5e6f-8718-2931a2b3c4d5";
        var game = new Game(GameMode.DEBUG);
        game.setCreator("owner");
        when(gameRepository.findById(id)).thenReturn(Optional.of(game));
        doThrow(new NotAllowedException("Debug access is not enabled for this account"))
                .when(debugAccess).requireOwner(game, user);

        var accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setDestination("/topic/games/" + id + "/chat");
        accessor.setSessionId("session-1");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        var interceptor = new PrivateChatDeliveryInterceptor(authentication, gameRepository, debugAccess);
        assertNull(interceptor.preSend(message, mock(MessageChannel.class)));
    }
}
