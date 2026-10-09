package fi.bizhop.finanssi2.security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
@Profile("!test & !auth-test")
public class FirebaseConfig {
    @Value("${finanssi2.firebase.credentials}")
    private String credentialsPath;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        FirebaseOptions options;
        try (var serviceAccount = new FileInputStream(credentialsPath)) {
            options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
        }
        return FirebaseApp.initializeApp(options);
    }
}
