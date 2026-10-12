package fi.bizhop.finanssi2.security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Only Firebase Auth is used, so firebase-admin's Firestore and Cloud Storage dependencies are excluded. This keeps the token
 * verification path honest: it must run on what is left (a missing class would be a NoClassDefFoundError, not a FirebaseAuthException).
 */
class FirebaseAuthSmokeTest {
    static final String PROJECT = "smoke-test-project";
    FirebaseApp app;
    java.security.KeyPair keys;

    @BeforeEach
    void initialize() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        var pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(keys.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
        var account = """
                {"type":"service_account","project_id":"%s","private_key_id":"key-1","private_key":"%s",
                 "client_email":"smoke@%s.iam.gserviceaccount.com","client_id":"1","token_uri":"https://oauth2.googleapis.com/token"}
                """.formatted(PROJECT, pem.replace("\n", "\\n"), PROJECT);
        app = FirebaseApp.initializeApp(FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(account.getBytes(StandardCharsets.UTF_8))))
                .setProjectId(PROJECT).setConnectTimeout(2_000).setReadTimeout(2_000).build(), "smoke-" + System.nanoTime());
    }

    @AfterEach
    void cleanUp() {
        app.delete();
    }

    private String jwt(String header, String payload) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var signingInput = encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        var signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keys.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + encoder.encodeToString(signature.sign());
    }

    @Test
    void theAuthApiCanBeIntrospected() {
        // Spring introspects the bean's class, which resolves every type in its method signatures
        for (var type : new Class<?>[] {FirebaseAuth.class, com.google.firebase.auth.AbstractFirebaseAuth.class, FirebaseApp.class,
                com.google.firebase.auth.FirebaseToken.class, com.google.firebase.auth.UserRecord.class}) {
            type.getDeclaredMethods();
            type.getDeclaredFields();
        }
    }

    @Test
    void aGarbageTokenIsRejectedAsInvalid() {
        assertThrows(FirebaseAuthException.class, () -> FirebaseAuth.getInstance(app).verifyIdToken("not-a-token"));
    }

    @Test
    void aWellFormedTokenSignedByAnUnknownKeyIsRejectedAfterTheKeyLookup() throws Exception {
        long now = System.currentTimeMillis() / 1000;
        var token = jwt("{\"alg\":\"RS256\",\"kid\":\"unknown\",\"typ\":\"JWT\"}",
                "{\"aud\":\"%s\",\"iss\":\"https://securetoken.google.com/%s\",\"sub\":\"uid-1\",\"auth_time\":%d,\"iat\":%d,\"exp\":%d}"
                        .formatted(PROJECT, PROJECT, now, now, now + 3600));

        // Google's key fetch fails or does not know the key; either way this is a FirebaseAuthException through the whole verifier
        assertThrows(FirebaseAuthException.class, () -> FirebaseAuth.getInstance(app).verifyIdToken(token));
    }
}
