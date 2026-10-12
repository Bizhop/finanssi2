package fi.bizhop.finanssi2.security;

import com.google.auth.oauth2.GoogleCredentials;
import fi.bizhop.finanssi2.config.Finanssi2Properties;
import lombok.RequiredArgsConstructor;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Files;
import java.io.IOException;

@Configuration
@RequiredArgsConstructor
@Profile("!test & !auth-test")
public class FirebaseConfig {
    private final Finanssi2Properties properties;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        FirebaseOptions options;
        try (var serviceAccount = Files.newInputStream(properties.firebase().credentials())) {
            options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
        }
        return FirebaseApp.initializeApp(options);
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }
}
