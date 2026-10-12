package fi.bizhop.finanssi2.web;

import fi.bizhop.finanssi2.db.ChatMessage;
import fi.bizhop.finanssi2.service.ChatService;
import fi.bizhop.finanssi2.web.model.ChatMessageInput;
import fi.bizhop.finanssi2.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@RestController
@RequiredArgsConstructor
public class ChatController {
    static final int MAX_PAGE_SIZE = 100;

    final ChatService chatService;

    /**
     * Returns a page of messages, newest first. Without {@code before} returns the newest messages; to load older
     * ones, pass the id of the oldest message received so far. A page shorter than {@code size} is the last one.
     */
    @GetMapping(value = "/api/chat", produces = "application/json")
    List<ChatMessage> getMessages(
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "20") int size) {
        validatePage(before, size);
        return chatService.getMessages(before, size);
    }

    @PostMapping(value = "/api/chat", consumes = "application/json", produces = "application/json")
    void postMessage(@RequestBody ChatMessageInput message, @RequestAttribute("user") User user) {
        chatService.postMessage(user, validMessage(message));
    }

    @GetMapping(value = "/api/games/{id}/chat", produces = "application/json")
    List<ChatMessage> getGameMessages(@PathVariable String id, @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "20") int size, @RequestAttribute("user") User user) {
        validatePage(before, size);
        return chatService.getGameMessages(id, before, size, user);
    }

    @PostMapping(value = "/api/games/{id}/chat", consumes = "application/json", produces = "application/json")
    ChatMessage postGameMessage(@PathVariable String id, @RequestBody(required = false) ChatMessageInput input,
            @RequestAttribute("user") User user) {
        return chatService.postGameMessage(id, user, validMessage(input));
    }

    private static String validMessage(ChatMessageInput input) {
        var message = input == null ? null : input.message();
        try {
            ChatService.validateMessage(message);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
        return Objects.requireNonNull(message); // validated as present above
    }

    private static void validatePage(String before, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (before != null && !before.matches("[0-9]{1,19}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "before must be a message id");
        }
        if (before != null) try { Long.parseLong(before); }
        catch (NumberFormatException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "before must be a message id");
        }
    }
}
