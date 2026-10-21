package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
@QuarkusTestResource(IsolatedDatabaseResource.class)
class AuthResourceTest {
    @Inject
    TokenService tokens;

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
    void requiresClientAfterSuccessfulAuthentication() {
        for (String requestJson : List.of(
                "{\"username\":\"student\",\"password\":\"Workshop123!\"}",
                "{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":null}",
                "{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"\"}",
                "{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\" \\t \"}")) {
            assertLoginError(requestJson, 400, "Client is required");
        }
    }

    @Test
    void preservesCredentialErrorsBeforeClientValidation() {
        assertLoginError("{}", 400, "Username is required");
        assertLoginError("{\"username\":\"student\"}", 400, "Password is required");
        for (String clientField : List.of("", ",\"client\":null", ",\"client\":\"\"", ",\"client\":\"   \"")) {
            assertLoginError("{\"username\":\"student\",\"password\":\"wrong\"" + clientField + "}",
                    401, "Invalid user student or password");
        }
    }

    @Test
    void acceptsValidatedClientsWithoutNormalizationOrAllowlist() throws Exception {
        for (String client : List.of(" Angular-Workshop ", "other-client")) {
            String token = given()
                    .contentType("application/json")
                    .body(Map.of("username", "student", "password", "Workshop123!", "client", client))
                    .when().post("/auth/login")
                    .then()
                    .statusCode(200)
                    .body("keySet()", containsInAnyOrder("token", "expiresIn", "user", "debug"))
                    .body("user.username", is("student"))
                        .body("expiresIn", is(3600))
                        .extract().path("token");
                    assertEquals(client, tokens.verify(token).getStringClaim("client"));
        }
    }

    @Test
                void returnsSignedClaimsFromStoredIdentityAndDeclaredExpiry() throws Exception {
        long beforeLogin = Instant.now().getEpochSecond();
        String token = given()
                .contentType("application/json")
                .body(Map.of("username", "student", "password", "Workshop123!", "client", "angular-workshop",
                    "sub", "forged-user", "role", "ADMIN"))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("token");
        long afterLogin = Instant.now().getEpochSecond();
        JWTClaimsSet claims = tokens.verify(token);
        assertEquals("student", claims.getSubject());
        assertEquals("STUDENT", claims.getStringClaim("role"));
        assertEquals("angular-workshop", claims.getStringClaim("client"));
        long issuedAt = claims.getIssueTime().toInstant().getEpochSecond();
        long expiry = claims.getExpirationTime().toInstant().getEpochSecond();
        assertTrue(issuedAt >= beforeLogin && issuedAt <= afterLogin);
        assertEquals(3600, expiry - issuedAt);
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

    @Test
    void successfulLoginDoesNotLogSensitiveValues() throws Exception {
        String[] issued = {""};
        String log = captureDiagnostics(() -> issued[0] = given().contentType("application/json")
                .body(Map.of("username", "student", "password", "Workshop123!", "client", "test-client"))
                .when().post("/auth/login").then().statusCode(200).extract().path("token"));
        assertSafeDiagnostics(log, issued[0]);
    }

    @Test
    void authenticationAndJdbcFailuresUseSafeDiagnostics() throws Exception {
        String log = captureDiagnostics(() -> {
            assertLoginError("{\"username\":\"student\",\"password\":\"synthetic-password\"}",
                    401, "Invalid user student or password");
            assertCapturedFailure(new IllegalStateException("synthetic-password"), "synthetic-password");
            returnsUnauthorizedWhenTheIsolatedCredentialQueryFails();
        });
        assertSafeDiagnostics(log, "");
        assertTrue(log.contains("Credential lookup failed"), "Use a fixed diagnostic for JDBC errors");
        assertFalse(log.contains("org.sqlite.SQLiteException"), "Do not dump JDBC exceptions");
    }

    private void assertSafeDiagnostics(String log, String token) {
        String secret = System.getenv("AUTH_TOKEN_SECRET");
        for (String sensitive : List.of("Workshop123!", "synthetic-password", "Characterization123!", secret,
                new String(Base64.getDecoder().decode(secret), StandardCharsets.UTF_8))) {
            assertFalse(log.contains(sensitive), "Sensitive values must not appear in diagnostics");
            assertFalse(log.contains(Base64.getEncoder().encodeToString(sensitive.getBytes(StandardCharsets.UTF_8))),
                    "Reversible encodings must not appear in diagnostics");
        }
        if (!token.isEmpty()) {
            assertFalse(log.contains(token), "Tokens must not appear in diagnostics");
        }
    }

    private String captureDiagnostics(CheckedAction action) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream previousOut = System.out;
        PrintStream previousError = System.err;
        StringBuilder messages = new StringBuilder();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.append(new SimpleFormatter().format(record));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger logger = Logger.getLogger("");
        logger.addHandler(handler);
        try (PrintStream captured = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            System.setErr(captured);
            action.run();
        } finally {
            System.setOut(previousOut);
            System.setErr(previousError);
            logger.removeHandler(handler);
        }
        return messages + output.toString(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
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
