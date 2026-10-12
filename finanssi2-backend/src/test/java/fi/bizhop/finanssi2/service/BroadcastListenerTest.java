package fi.bizhop.finanssi2.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BroadcastListenerTest {
    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean MessagingService messagingService() { return mock(MessagingService.class); }
        @Bean BroadcastListener broadcastListener(MessagingService messagingService) { return new BroadcastListener(messagingService); }
        @Bean PlatformTransactionManager transactionManager() {
            return new DataSourceTransactionManager(new DriverManagerDataSource("jdbc:h2:mem:broadcast"));
        }
    }

    final Broadcast broadcast = new Broadcast("/topic/games/1", "payload");

    @Test
    void sendsOnlyAfterCommitAndNeverOnRollback() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var messaging = context.getBean(MessagingService.class);
            var publisher = (ApplicationEventPublisher) context;
            var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

            transaction.executeWithoutResult(status -> {
                publisher.publishEvent(broadcast);
                verify(messaging, never()).send("/topic/games/1", "payload");
            });
            verify(messaging).send("/topic/games/1", "payload");

            org.mockito.Mockito.clearInvocations(messaging);
            transaction.executeWithoutResult(status -> {
                publisher.publishEvent(broadcast);
                status.setRollbackOnly();
            });
            verify(messaging, never()).send("/topic/games/1", "payload");
        }
    }

    @Test
    void sendsImmediatelyWithoutATransaction() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            context.publishEvent(broadcast);

            verify(context.getBean(MessagingService.class)).send("/topic/games/1", "payload");
        }
    }
}
