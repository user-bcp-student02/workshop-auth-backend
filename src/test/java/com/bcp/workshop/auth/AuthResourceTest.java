package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(IsolatedDatabaseResource.class)
class AuthResourceTest {
    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("keySet()", containsInAnyOrder("token", "expiresIn", "user", "debug"))
                .body("user.keySet()", containsInAnyOrder("username", "displayName", "role"))
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("token", instanceOf(String.class))
                .body("token", not(emptyString()))
                .body("expiresIn", instanceOf(Integer.class))
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"));
    }

    @Test
    void preservesStoredIdentityEscapesDisplayNameAndIgnoresUnknownFields() {
        given()
                .contentType("application/json")
                .body(Map.of("username", "character-user", "password", "Characterization123!",
                        "client", "angular-workshop", "sub", "forged-user", "role", "ADMIN",
                        "displayName", "forged-name"))
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.keySet()", containsInAnyOrder("username", "displayName", "role"))
                .body("user.username", is("character-user"))
                .body("user.displayName", is("&lt;Student &amp; Trainer&gt;"))
                .body("user.role", is("AUDITOR"));
    }

    @Test
    void rejectsNullAndEmptyRequests() {
        for (String requestJson : List.of("null", "")) {
            assertLoginError(requestJson, 400, "Request is null");
        }
    }

    @Test
    void validatesUsernameBeforePassword() {
        for (String requestJson : List.of("{}", "{\"username\":null}", "{\"username\":\"\"}",
                "{\"username\":\"   \"}", "{\"username\":\"\\t\"}")) {
            assertLoginError(requestJson, 400, "Username is required");
        }
    }

    @Test
    void validatesPasswordAfterUsername() {
        for (String requestJson : List.of("{\"username\":\"student\"}",
                "{\"username\":\"student\",\"password\":null}",
                "{\"username\":\"student\",\"password\":\"\"}",
                "{\"username\":\"student\",\"password\":\"   \"}",
                "{\"username\":\"student\",\"password\":\"\\t\"}")) {
            assertLoginError(requestJson, 400, "Password is required");
        }
    }

    @Test
    void rejectsInvalidCredentialsWithoutTrimmingOrChangingCase() {
        for (Map<String, String> credentials : List.of(
                Map.of("username", "missing-user", "password", "wrong"),
                Map.of("username", "student", "password", "wrong"),
                Map.of("username", " student ", "password", "Workshop123!"),
                Map.of("username", "STUDENT", "password", "Workshop123!"),
                Map.of("username", "student", "password", " Workshop123! "))) {
            given()
                    .contentType("application/json")
                    .body(credentials)
                    .when().post("/auth/login")
                    .then()
                    .statusCode(401)
                    .body("keySet()", containsInAnyOrder("message"))
                    .body("message", is("Invalid user " + credentials.get("username") + " or password"));
        }
    }

    @Test
    void returnsUnauthorizedWhenTheIsolatedCredentialQueryFails() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("AUTH_DB_URL"));
             var statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE legacy_users RENAME TO characterization_users_unavailable");
            try {
                assertLoginError("{\"username\":\"student\",\"password\":\"Workshop123!\"}",
                        401, "Invalid user student or password");
            } finally {
                statement.executeUpdate("ALTER TABLE characterization_users_unavailable RENAME TO legacy_users");
            }
        }
    }

    @Test
    void preservesCapturedExceptionWithMessage() {
        assertCapturedFailure(new IllegalStateException("test failure"), "test failure");
    }

    @Test
    void preservesCapturedExceptionWithoutMessage() {
        assertCapturedFailure(new IllegalStateException(), "Unknown error");
    }

    @Test
    void characterizesClientAsOptionalAndIgnoredBeforeTheApprovedChange() {
        for (String requestJson : List.of(
                "{\"username\":\"student\",\"password\":\"Workshop123!\"}",
                "{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":null}",
                "{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"\"}",
                "{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"   \"}",
                "{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"other-client\"}")) {
            given()
                    .contentType("application/json")
                    .body(requestJson)
                    .when().post("/auth/login")
                    .then()
                    .statusCode(200)
                    .body("keySet()", containsInAnyOrder("token", "expiresIn", "user", "debug"))
                    .body("user.username", is("student"))
                    .body("expiresIn", is(3600));
        }
    }

    @Test
    void characterizesLegacyTokenFieldsWithoutFixingTheEmbeddedSecret() {
        long beforeLogin = Instant.now().getEpochSecond();
        String token = given()
                .contentType("application/json")
                .body(Map.of("username", "student", "password", "Workshop123!"))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("token");
        long afterLogin = Instant.now().getEpochSecond();
        String[] fields = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8).split("\\|", -1);
        assertEquals(4, fields.length);
        assertEquals("student", fields[0]);
        assertEquals("STUDENT", fields[1]);
        long expiry = Long.parseLong(fields[2]);
        assertTrue(expiry >= beforeLogin + 3600 && expiry <= afterLogin + 3600);
    }

    private void assertLoginError(String requestJson, int expectedStatus, String expectedMessage) {
        given()
                .contentType("application/json")
                .body(requestJson)
                .when().post("/auth/login")
                .then()
                .statusCode(expectedStatus)
                .contentType("application/json")
                .body("keySet()", containsInAnyOrder("message"))
                .body("message", is(expectedMessage));
    }

    private void assertCapturedFailure(IllegalStateException failure, String expectedMessage) {
        AuthResource resource = new AuthResource();
        resource.database = new LegacyDatabase() {
            @Override
            public UserRecord findByCredentials(String username, String password) {
                throw failure;
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "synthetic-password";
        try (Response response = resource.login(request)) {
            assertEquals(500, response.getStatus());
            assertEquals(Map.of("message", expectedMessage,
                    "exception", "java.lang.IllegalStateException"), response.getEntity());
        }
    }
}
