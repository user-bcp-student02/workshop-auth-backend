package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;

import java.util.Map;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.nullValue;

@QuarkusIntegrationTest
@QuarkusTestResource(IsolatedDatabaseResource.class)
class AuthTransportIT {
    @Test
    void malformedJsonHasAnEmptyBadRequestResponse() {
        assertEmptyError(given().contentType("application/json").body("{")
                .when().post("/auth/login").then(), 400);
    }

    @Test
    void incompatibleUsernameTypeHasAnEmptyBadRequestResponse() {
        assertEmptyError(given().contentType("application/json").body(Map.of("username", Map.of()))
                .when().post("/auth/login").then(), 400);
    }

    @Test
    void getLoginIsNotAllowed() {
        assertEmptyError(given().when().get("/auth/login").then(), 405);
    }

    @Test
    void incompatibleAcceptIsNotAcceptable() {
        assertEmptyError(given().contentType("application/json").accept("text/plain")
                .body(Map.of("username", "student", "password", "Workshop123!"))
                .when().post("/auth/login").then(), 406);
    }

    @Test
    void plainTextLoginIsUnsupported() {
        assertEmptyError(given().contentType("text/plain").body("synthetic-request")
                .when().post("/auth/login").then(), 415);
    }

    @Test
    void healthIsPublicWithoutAuthorization() {
        assertHealth(given().accept("application/json").when().get("/q/health").then());
    }

    @Test
    void healthDoesNotValidateAnInvalidBearerToken() {
        assertHealth(given().accept("application/json")
                .header("Authorization", "Bearer test-only-not-a-token")
                .when().get("/q/health").then());
    }

    @Test
    void healthStillReturnsJsonForPlainTextAccept() {
        assertHealth(given().accept("text/plain").when().get("/q/health").then());
    }

    @Test
    void healthPreservesTheObservedPostWithoutBody() {
        assertHealth(given().when().post("/q/health").then());
    }

        @Test
        void packagedLoginReturnsSignedIdentityAndClient() throws Exception {
        String encoded = given().contentType("application/json")
            .body(Map.of("username", "student", "password", "Workshop123!", "client", " Prod-Client ",
                "sub", "forged-user"))
            .when().post("/auth/login").then().statusCode(200)
            .body("keySet()", containsInAnyOrder("token", "expiresIn", "user", "debug"))
            .body("expiresIn", is(3600)).body("user.username", is("student"))
            .body("debug", is("authenticated-with-legacy-flow"))
            .extract().path("token");
        SignedJWT token = SignedJWT.parse(encoded);
        assertTrue(JWSAlgorithm.HS256.equals(token.getHeader().getAlgorithm()));
        assertTrue(token.verify(new MACVerifier(Base64.getDecoder().decode(System.getenv("AUTH_TOKEN_SECRET")))));
        assertEquals("student", token.getJWTClaimsSet().getSubject());
        assertEquals(" Prod-Client ", token.getJWTClaimsSet().getStringClaim("client"));
        assertEquals(3600, (token.getJWTClaimsSet().getExpirationTime().getTime()
            - token.getJWTClaimsSet().getIssueTime().getTime()) / 1000);
        }

        @Test
        void packagedLoginPreservesClientAndCredentialErrorPriority() {
        given().contentType("application/json")
            .body(Map.of("username", "student", "password", "Workshop123!"))
            .when().post("/auth/login").then().statusCode(400)
            .body("keySet()", containsInAnyOrder("message"))
            .body("message", is("Client is required"));
        given().contentType("application/json")
            .body(Map.of("username", "student", "password", "wrong"))
            .when().post("/auth/login").then().statusCode(401)
            .body("keySet()", containsInAnyOrder("message"))
            .body("message", is("Invalid user student or password"));
        }

    private void assertEmptyError(ValidatableResponse response, int expectedStatus) {
        response.statusCode(expectedStatus).body(emptyString()).header("Content-Type", nullValue());
    }

    private void assertHealth(ValidatableResponse response) {
        response.statusCode(200)
                .contentType("application/json")
                .body("keySet()", containsInAnyOrder("status", "checks"))
                .body("status", is("UP"))
                .body("checks", empty());
    }
}