package fi.bizhop.finanssi2;

import fi.bizhop.finanssi2.db.ChatRepository;
import fi.bizhop.finanssi2.service.MessagingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class Finanssi2ApplicationTests {
	@MockitoBean
	MessagingService messagingService;
	@MockitoBean
	ChatRepository chatRepository;

	@Test
	void contextLoads() {
	}

}
