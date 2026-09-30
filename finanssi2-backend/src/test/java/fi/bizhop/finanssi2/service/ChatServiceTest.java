package fi.bizhop.finanssi2.service;

import fi.bizhop.finanssi2.db.ChatRepository;
import fi.bizhop.finanssi2.db.ChatMessage;
import fi.bizhop.finanssi2.security.User;
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
    @InjectMocks
    ChatService chatService;

    @Test
    void testGetNewestMessages() {
        var newest = List.of(new ChatMessage("66f9a1b2c3d4e5f607182931", "tester@example.com", "Tester Example", "Hello", 1000L, ""));
        when(chatRepository.findAllByOrderByIdDesc(Limit.of(20))).thenReturn(newest);

        assertEquals(newest, chatService.getMessages(null, 20));
    }

    @Test
    void testGetOlderMessages() {
        var older = List.of(new ChatMessage("66f9a1b2c3d4e5f607182920", "tester@example.com", null, "Older", 500L, ""));
        when(chatRepository.findByIdLessThanOrderByIdDesc("66f9a1b2c3d4e5f607182930", Limit.of(5))).thenReturn(older);

        assertEquals(older, chatService.getMessages("66f9a1b2c3d4e5f607182930", 5));
    }

    @Test
    void testPostMessageSavesAndBroadcastsSavedMessage() {
        var author = new User("uid", "tester@example.com", "Tester Example", "https://example.com/photo.png");
        when(chatRepository.save(any())).thenAnswer(invocation ->
                invocation.<ChatMessage>getArgument(0).withId("66f9a1b2c3d4e5f607182940"));

        var saved = chatService.postMessage(author, "Hello");

        var toSave = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatRepository).save(toSave.capture());
        assertNull(toSave.getValue().getId());
        assertEquals("tester@example.com", saved.getUsername());
        assertEquals("Tester Example", saved.getName());
        assertEquals("Hello", saved.getMessage());
        assertEquals("https://example.com/photo.png", saved.getPhotoUrl());
        assertEquals("66f9a1b2c3d4e5f607182940", saved.getId());
        // Clients need the id, so the broadcast message must be the saved one
        verify(messagingService).send("/topic/chat", saved);
    }
}
