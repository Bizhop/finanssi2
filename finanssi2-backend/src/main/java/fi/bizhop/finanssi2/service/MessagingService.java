package fi.bizhop.finanssi2.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessagingService {
    final SimpMessagingTemplate simpMessagingTemplate;
    final ObjectMapper objectMapper;

    /** Sends the payload as JSON; a failure is logged, since the change it reports is already saved */
    public void send(String topic, Object payload) {
        try {
            var json = objectMapper.writeValueAsString(payload);
            simpMessagingTemplate.convertAndSend(topic, json);
            log.debug("Sent {} characters to {}", json.length(), topic);
        } catch (Exception e) {
            log.warn("Failed to send to {}", topic, e);
        }
    }
}
