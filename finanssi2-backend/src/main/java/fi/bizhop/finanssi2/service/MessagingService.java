package fi.bizhop.finanssi2.service;

import tools.jackson.databind.ObjectMapper;
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

    /** Sends the payload as JSON; a failure is logged, since the change it reports is already saved */
    public void send(String topic, Object payload) {
        try {
            var json = objectMapper.writeValueAsString(payload);
            simpMessagingTemplate.convertAndSend(topic, json);
            logger.log(Level.INFO, "Sent to {0}: {1}", new Object[] {topic, json});
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to send to " + topic, e);
        }
    }
}
