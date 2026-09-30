package fi.bizhop.finanssi2.service;

import fi.bizhop.finanssi2.db.ChatRepository;
import fi.bizhop.finanssi2.db.ChatMessage;
import fi.bizhop.finanssi2.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {
    static final String CHAT_TOPIC = "/topic/chat";

    final ChatRepository chatRepository;

    final MessagingService messagingService;

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
        var newMessage = new ChatMessage(null, author.email(), author.name(), message, System.currentTimeMillis(), author.photoUrl());
        // Broadcast the saved message so clients get its id
        var savedMessage = chatRepository.save(newMessage);

        messagingService.send(CHAT_TOPIC, savedMessage);
        return savedMessage;
    }
}
