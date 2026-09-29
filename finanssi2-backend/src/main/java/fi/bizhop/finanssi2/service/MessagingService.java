package fi.bizhop.finanssi2.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.bizhop.finanssi2.model.db.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.logging.Level;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class MessagingService {
    final Logger logger = Logger.getLogger(MessagingService.class.getName());
    final SimpMessagingTemplate simpMessagingTemplate;
    final ObjectMapper objectMapper = new ObjectMapper();

    public void sendChatMessage(String topic, ChatMessage message) {
        try {
            var messageJson = objectMapper.writeValueAsString(message);
            simpMessagingTemplate.convertAndSend(topic, messageJson);
            logger.log(Level.INFO, "Sent chat message: {0}", messageJson);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to send chat message", e);
        }
    }
}
