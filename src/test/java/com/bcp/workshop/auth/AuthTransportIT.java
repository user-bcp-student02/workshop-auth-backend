package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

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