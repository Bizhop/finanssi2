package fi.bizhop.finanssi2;

import fi.bizhop.finanssi2.db.ChatRepository;
import fi.bizhop.finanssi2.service.MessagingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class Finanssi2ApplicationTests {
	@MockBean
	MessagingService messagingService;
	@MockBean
	ChatRepository chatRepository;

	@Test
	void contextLoads() {
	}

}
