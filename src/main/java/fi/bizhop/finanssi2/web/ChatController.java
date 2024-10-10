package fi.bizhop.finanssi2.web;

import fi.bizhop.finanssi2.db.ChatRepository;
import fi.bizhop.finanssi2.model.db.ChatMessage;
import fi.bizhop.finanssi2.service.MessagingService;
import fi.bizhop.finanssi2.web.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatController {
    final ChatRepository chatRepository;

    final MessagingService messagingService;

    @RequestMapping(value = "/api/chat", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody List<ChatMessage> getMessages() {
        var messages = new ArrayList<>(chatRepository.findAll(PageRequest.of(0, 20, Sort.by("timestamp").descending())).getContent());
        Collections.reverse(messages);
        return messages;
    }

    @RequestMapping(value = "/api/chat", method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
    void postMessage(@RequestBody ChatMessageInput message, @RequestAttribute("user") User user) {
        var newMessage = new ChatMessage(user.email(), message.message(), System.currentTimeMillis());
        chatRepository.save(newMessage);

        messagingService.sendChatMessage("/topic/chat", newMessage);
    }

    public record ChatMessageInput(String message) {}
}
