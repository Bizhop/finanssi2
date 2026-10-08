package fi.bizhop.finanssi2.service;

import fi.bizhop.finanssi2.db.ChatRepository;
import fi.bizhop.finanssi2.db.ChatMessage;
import fi.bizhop.finanssi2.security.User;
import fi.bizhop.finanssi2.game.db.GameRepository;
import fi.bizhop.finanssi2.game.service.DebugAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {
    @Mock
    ChatRepository chatRepository;
    @Mock
    MessagingService messagingService;
    @Mock
    GameRepository gameRepository;
    @Mock
    DebugAccess debugAccess;
    @InjectMocks
    ChatService chatService;

    @Test
    void testGetNewestMessages() {
        var newest = List.of(new ChatMessage("0000000000000000011", "tester@example.com", "Tester Example", "Hello", 1000L, ""));
        when(chatRepository.findAllByOrderByIdDesc(Limit.of(20))).thenReturn(newest);

        assertEquals(newest, chatService.getMessages(null, 20));
    }

    @Test
    void testGetOlderMessages() {
        var older = List.of(new ChatMessage("0000000000000000009", "tester@example.com", null, "Older", 500L, ""));
        when(chatRepository.findByIdLessThanOrderByIdDesc("0000000000000000010", Limit.of(5))).thenReturn(older);

        assertEquals(older, chatService.getMessages("0000000000000000010", 5));
    }

    @Test
    void testPostMessageSavesAndBroadcastsSavedMessage() {
        var id = java.util.UUID.randomUUID().toString();
        var author = new User(id, "tester@example.com", "Tester Example", "https://example.com/photo.png");
        when(chatRepository.save(any(ChatMessage.class), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(java.util.UUID.fromString(id)))).thenAnswer(invocation ->
                invocation.<ChatMessage>getArgument(0).withId("0000000000000000012"));

        var saved = chatService.postMessage(author, "Hello");

        var toSave = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatRepository).save(toSave.capture(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(java.util.UUID.fromString(id)));
        assertNull(toSave.getValue().id());
        assertEquals(id, saved.userId());
        assertEquals("Tester Example", saved.name());
        assertEquals("Hello", saved.message());
        assertEquals("https://example.com/photo.png", saved.photoUrl());
        assertEquals("0000000000000000012", saved.id());
        // Clients need the id, so the broadcast message must be the saved one
        verify(messagingService).send("/topic/chat", saved);
    }
}
