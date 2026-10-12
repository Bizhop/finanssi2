package fi.bizhop.finanssi2.service;

import fi.bizhop.finanssi2.db.ChatRepository;
import fi.bizhop.finanssi2.db.ChatMessage;
import fi.bizhop.finanssi2.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import fi.bizhop.finanssi2.game.db.GameRepository;
import fi.bizhop.finanssi2.game.service.DebugAccess;
import fi.bizhop.finanssi2.game.service.GameNotFoundException;
import fi.bizhop.finanssi2.game.service.NotAllowedException;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.util.List;
import fi.bizhop.finanssi2.game.db.GameMode;
import org.jspecify.annotations.Nullable;

@Service
@RequiredArgsConstructor
public class ChatService {
    static final String CHAT_TOPIC = "/topic/chat";

    final ChatRepository chatRepository;

    final ApplicationEventPublisher publisher;
    final GameRepository gameRepository;
    final DebugAccess debugAccess;

    /**
     * Returns up to {@code size} messages, newest first: the newest ones when {@code before} is null, otherwise
     * the ones older than the message with that id.
     */
    public List<ChatMessage> getMessages(String before, int size) {
        return before == null
                ? chatRepository.findAllByOrderByIdDesc(Limit.of(size))
                : chatRepository.findByIdLessThanOrderByIdDesc(before, Limit.of(size));
    }

    /** Saves the message and broadcasts it to the chat topic */
    public ChatMessage postMessage(User author, String message) {
        validateMessage(message);
        var newMessage = new ChatMessage(null, author.userId(), message, System.currentTimeMillis());
        // Broadcast the saved message so clients get its id
        var savedMessage = chatRepository.save(newMessage, null, UUID.fromString(author.userId()));

        publisher.publishEvent(new Broadcast(CHAT_TOPIC, savedMessage));
        return savedMessage;
    }

    public List<ChatMessage> getGameMessages(String gameId, String before, int size, User user) {
        var game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        debugAccess.requireRead(game, user);
        return chatRepository.findGameMessages(UUID.fromString(gameId), before, Limit.of(size));
    }

    @Transactional
    public ChatMessage postGameMessage(String gameId, User author, String message) {
        validateMessage(message);
        var game = gameRepository.findByIdForUpdate(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        debugAccess.requireRead(game, author);
        if (game.getMode() == GameMode.NORMAL
                && game.getState().player(author.userId()).isEmpty()) {
            throw new NotAllowedException("Join the game before sending messages");
        }
        var saved = chatRepository.save(new ChatMessage(null, author.userId(), message,
                System.currentTimeMillis()), UUID.fromString(gameId), UUID.fromString(author.userId()));
        var topic = "/topic/games/" + gameId + "/chat";
        publisher.publishEvent(new Broadcast(topic, saved));
        return saved;
    }

    public static void validateMessage(@Nullable String message) {
        if (message == null || message.isBlank() || message.length() > 100) {
            throw new IllegalArgumentException("message must contain 1–100 nonblank characters");
        }
    }
}
