package fi.bizhop.finanssi2.security;

import com.google.firebase.auth.FirebaseToken;
import fi.bizhop.finanssi2.db.ApplicationUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("auth-test")
class FirebaseAuthenticationFilterTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired ApplicationUserRepository users;
    @MockitoBean FirebaseTokenVerifier tokenVerifier;

    @BeforeEach
    void clearUsers() { users.deleteAll(); }

    @Test
    void missingTokensAreUnauthorizedAndUnverifiedTokensAreForbidden() {
        var missing = request(null);
        assertEquals(401, missing.getStatusCode().value());

        verifiedToken("pending-uid", "pending@example.com", false);
        var pending = request("pending-uid");
        assertEquals(403, pending.getStatusCode().value());
        assertTrue(pending.getBody().contains("EMAIL_VERIFICATION_REQUIRED"));
        assertEquals(0, users.count());
    }

    @Test
    void verifiedTokenUsesDatabaseIdentityAndDuplicateEmailCannotClaimAnotherAccount() {
        verifiedToken("firebase-uid-1", "person@example.com", true);
        var created = request("firebase-uid-1");
        assertEquals(200, created.getStatusCode().value());
        assertTrue(created.getBody().contains("\"email\":\"person@example.com\""));
        var id = users.findByFirebaseUid("firebase-uid-1").orElseThrow().getId().toString();
        assertTrue(created.getBody().contains("\"id\":\"" + id + "\""));
        assertNotEquals("firebase-uid-1", id);

        verifiedToken("firebase-uid-2", "PERSON@example.com", true);
        var collision = request("firebase-uid-2");
        assertEquals(409, collision.getStatusCode().value());
        assertTrue(collision.getBody().contains("ACCOUNT_LINKING_CONFLICT"));
        assertEquals(1, users.count());
    }

    @Test
    void profileEditAndAvatarWritesRequireCurrentVersion() throws Exception {
        verifiedToken("profile-owner", "profile@example.com", true);
        assertEquals(200, request("profile-owner").getStatusCode().value());
        var profile = users.findByFirebaseUid("profile-owner").orElseThrow();
        var firstVersion = profile.getVersion();

        var changed = putJson("profile-owner", "/api/me/profile", "{\"displayName\":\"Updated Name\",\"version\":" + firstVersion + "}");
        assertEquals(200, changed.getStatusCode().value());
        assertEquals("Updated Name", users.findByFirebaseUid("profile-owner").orElseThrow().getDisplayName());
        var stale = putJson("profile-owner", "/api/me/profile", "{\"displayName\":\"Stale Name\",\"version\":" + firstVersion + "}");
        assertEquals(409, stale.getStatusCode().value());

        var current = users.findByFirebaseUid("profile-owner").orElseThrow();
        var png = new java.awt.image.BufferedImage(20, 10, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var imageBytes = new java.io.ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(png, "png", imageBytes));
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ByteArrayResource(imageBytes.toByteArray()) {
            @Override public String getFilename() { return "avatar.png"; }
        });
        var uploadHeaders = headers("profile-owner");
        uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        var uploaded = http.exchange("http://localhost:" + port + "/api/me/avatar?version=" + current.getVersion(),
                HttpMethod.PUT, new HttpEntity<>(body, uploadHeaders), String.class);
        assertEquals(200, uploaded.getStatusCode().value(), uploaded.getBody());
        var storedAvatar = users.findByFirebaseUid("profile-owner").orElseThrow();
        assertTrue(storedAvatar.getCustomAvatar().startsWith("data:image/jpeg;base64,"));
        var staleDelete = http.exchange("http://localhost:" + port + "/api/me/avatar?version=" + current.getVersion(),
                HttpMethod.DELETE, new HttpEntity<>(headers("profile-owner")), String.class);
        assertEquals(409, staleDelete.getStatusCode().value());
        var removed = http.exchange("http://localhost:" + port + "/api/me/avatar?version=" + storedAvatar.getVersion(),
                HttpMethod.DELETE, new HttpEntity<>(headers("profile-owner")), String.class);
        assertEquals(200, removed.getStatusCode().value());
        assertNull(users.findByFirebaseUid("profile-owner").orElseThrow().getCustomAvatar());
    }

    private void verifiedToken(String uid, String email, boolean verified) {
        var token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn(uid);
        when(token.getEmail()).thenReturn(email);
        when(token.getName()).thenReturn("Test User");
        when(token.isEmailVerified()).thenReturn(verified);
        when(tokenVerifier.verifyAuthorizationHeader("Bearer " + uid)).thenReturn(Optional.of(token));
    }

    private org.springframework.http.ResponseEntity<String> request(String token) {
        var headers = token == null ? new HttpHeaders() : headers(token);
        return http.exchange("http://localhost:" + port + "/api/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private HttpHeaders headers(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private org.springframework.http.ResponseEntity<String> putJson(String token, String path, String json) {
        var headers = headers(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("http://localhost:" + port + path, HttpMethod.PUT, new HttpEntity<>(json, headers), String.class);
    }
}
