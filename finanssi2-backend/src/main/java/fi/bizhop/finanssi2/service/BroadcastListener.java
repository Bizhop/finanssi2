package fi.bizhop.finanssi2.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends published {@link Broadcast}s only after the surrounding transaction has committed, so clients never learn of a change that is
 * rolled back. Outside a transaction the message is sent immediately.
 */
@Component
@RequiredArgsConstructor
class BroadcastListener {
    final MessagingService messagingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void send(Broadcast broadcast) {
        messagingService.send(broadcast.topic(), broadcast.payload());
    }
}
