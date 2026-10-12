package fi.bizhop.finanssi2.web.config;

import fi.bizhop.finanssi2.game.db.GameRepository;
import fi.bizhop.finanssi2.game.db.GameMode;
import fi.bizhop.finanssi2.game.service.DebugAccess;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import org.jspecify.annotations.Nullable;

/** Rechecks debug-game chat authorization for each recipient at delivery time. */
@Component
@RequiredArgsConstructor
public class PrivateChatDeliveryInterceptor implements ChannelInterceptor {
    final StompAuthenticationInterceptor authentication;
    final GameRepository gameRepository;
    final DebugAccess debugAccess;

    @Override
    public @Nullable Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        var headers = SimpMessageHeaderAccessor.wrap(message);
        var destination = headers.getDestination();
        if (destination == null || !destination.matches("/topic/games/[0-9a-fA-F-]{36}/chat")) return message;
        var sessionId = headers.getSessionId();
        var auth = sessionId == null ? null : authentication.sessions.get(sessionId);
        if (auth == null) return null;
        var gameId = destination.substring("/topic/games/".length(), destination.length() - "/chat".length());
        try {
            var access = gameRepository.findAccess(gameId).orElse(null);
            if (access != null && access.mode() == GameMode.DEBUG) debugAccess.requireOwner(access.mode(), access.creator(), auth.user());
            return access == null ? null : message;
        } catch (RuntimeException denied) {
            return null;
        }
    }
}
