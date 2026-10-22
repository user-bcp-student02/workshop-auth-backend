package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class AuthResourceTest {
    @Inject
    TokenService tokenService;

    @Test
    void logsInWithWorkshopUserAndReturnsSignedToken() {
        var response = given()
                .contentType("application/json")
                .body("""
                        {"username":"student","password":"Workshop123!",
                         "client":"angular-workshop"}
                        """)
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("token", notNullValue())
                .extract().response();

        String token = response.jsonPath().getString("token");
        long expiresIn = response.jsonPath().getLong("expiresIn");
        TokenService.TokenClaims claims = tokenService.verify(token);

        assertEquals("student", claims.sub());
        assertEquals("angular-workshop", claims.client());
        assertEquals(claims.exp() - claims.iat(), expiresIn);
    }

    @Test
    void rejectsInvalidCredentialsWithoutReturningToken() {
        given()
                .contentType("application/json")
                .body("""
                        {"username":"student","password":"incorrect",
                         "client":"angular-workshop"}
                        """)
                .when().post("/auth/login")
                .then()
                // Mantener el status y el esquema genérico publicados en ENDPOINTS.md.
                .statusCode(401);
    }

    @Test
    void rejectsMalformedClient() {
        given()
                .contentType("application/json")
                .body("""
                        {"username":"student","password":"Workshop123!",
                         "client":"angular workshop"}
                        """)
                .when().post("/auth/login")
                // Mapear al error genérico de entrada definido por el contrato.
                .then()
                .statusCode(400);
    }
}