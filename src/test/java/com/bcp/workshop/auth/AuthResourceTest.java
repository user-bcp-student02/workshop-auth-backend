package com.bcp.workshop.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class AuthResourceTest {
    private static final String TEST_SECRET = "test-only-workshop-secret-with-32-bytes-minimum";

    @Test
    void logsInWithSignedWorkshopToken() {
        String token = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("token", notNullValue())
                .extract().path("token");

        var claims = JWT.require(Algorithm.HMAC256(TEST_SECRET))
                .withIssuer("workshop-auth-backend")
                .build()
                .verify(token);
        assertEquals("student", claims.getSubject());
        assertEquals("STUDENT", claims.getClaim("role").asString());
    }
}
