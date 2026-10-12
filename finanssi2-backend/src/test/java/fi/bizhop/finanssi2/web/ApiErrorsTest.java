package fi.bizhop.finanssi2.web;

import fi.bizhop.finanssi2.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.UUID;

import static fi.bizhop.finanssi2.web.SecurityConfig.TEST_USER_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Every controller family answers failures with a ProblemDetail whose {@code detail} the frontend can show */
class ApiErrorsTest extends ApiTest {
    private JsonNode send(HttpMethod method, String path, String uid, String body) {
        var headers = new HttpHeaders();
        if (uid != null) headers.add(TEST_USER_HEADER, uid);
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        var response = restTemplate.exchange("http://localhost:" + port + path, method, new HttpEntity<>(body, headers), JsonNode.class);
        var problem = response.getBody();
        assertTrue(problem != null && problem.has("detail"), response.getStatusCode() + " " + problem);
        assertEquals(response.getStatusCode().value(), problem.get("status").asInt());
        assertFalse(problem.get("detail").asString().isBlank());
        return problem;
    }

    @Test
    void chatReasonsReachTheBody() {
        var problem = send(HttpMethod.GET, "/api/chat?size=0", "a", null);
        assertEquals(400, problem.get("status").asInt());
        assertTrue(problem.get("detail").asString().contains("size"));
    }

    @Test
    void gameErrorsKeepTheirStatusAndMessage() {
        when(gameRepository.findById("missing")).thenReturn(Optional.empty());
        assertEquals(404, send(HttpMethod.GET, "/api/games/missing", "a", null).get("status").asInt());
        assertEquals(400, send(HttpMethod.GET, "/api/games/missing/events?after=-1", "a", null).get("status").asInt());
        assertEquals("Invalid request body or unexpected fields", send(HttpMethod.PUT, "/api/games/missing/settings", "a", "{").get("detail").asString());
    }

    @Test
    void publicProfileReasonsReachTheBody() {
        var problem = send(HttpMethod.GET, "/api/users?ids=not-a-uuid", "a", null);
        assertEquals("Invalid user id", problem.get("detail").asString());
        assertEquals(404, send(HttpMethod.GET, "/api/users/" + UUID.randomUUID() + "/avatar?v=1", "a", null).get("status").asInt());
    }

    @Test
    void profileEndpointsReportAMissingProfile() {
        var uid = UUID.randomUUID().toString();
        when(users.findById(UUID.fromString(uid))).thenReturn(Optional.empty());

        assertEquals("Account profile not found", send(HttpMethod.GET, "/api/me", uid, null).get("detail").asString());
        assertEquals("Account profile not found", send(HttpMethod.DELETE, "/api/me/avatar?version=1", uid, null).get("detail").asString());
        assertEquals("displayName must contain 1–50 characters",
                send(HttpMethod.PUT, "/api/me/profile", uid, "{\"displayName\":\"\",\"version\":1}").get("detail").asString());
    }

    @Test
    void springsOwnFailuresUseTheSameFormat() {
        assertEquals(404, send(HttpMethod.GET, "/api/no-such-route", "a", null).get("status").asInt());
        assertEquals(405, send(HttpMethod.DELETE, "/api/games/x/start", "a", null).get("status").asInt());
    }

    @Test
    void anUnparsableIdentityIsABadRequestNotAServerError() {
        assertEquals(HttpStatus.BAD_REQUEST.value(), send(HttpMethod.GET, "/api/me", "not-a-uuid", null).get("status").asInt());
    }
}
