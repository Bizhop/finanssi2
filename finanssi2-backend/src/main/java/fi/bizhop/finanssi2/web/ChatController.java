package fi.bizhop.finanssi2.web;

import fi.bizhop.finanssi2.db.ChatMessage;
import fi.bizhop.finanssi2.service.ChatService;
import fi.bizhop.finanssi2.web.model.ChatMessageInput;
import fi.bizhop.finanssi2.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatController {
    static final int MAX_PAGE_SIZE = 100;

    final ChatService chatService;

    /**
     * Returns a page of messages, newest first. Without {@code before} returns the newest messages; to load older
     * ones, pass the id of the oldest message received so far. A page shorter than {@code size} is the last one.
     */
    @RequestMapping(value = "/api/chat", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody List<ChatMessage> getMessages(
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "20") int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (before != null && !before.matches("[0-9]{1,19}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "before must be a message id");
        }
        if (before != null) {
            try { Long.parseLong(before); }
            catch (NumberFormatException invalid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "before must be a message id");
            }
        }
        return chatService.getMessages(before, size);
    }

    @RequestMapping(value = "/api/chat", method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
    void postMessage(@RequestBody ChatMessageInput message, @RequestAttribute("user") User user) {
        chatService.postMessage(user, message.message());
    }
}
