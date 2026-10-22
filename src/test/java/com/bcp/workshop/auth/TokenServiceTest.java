package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenServiceTest {
    private static final String SECRET =
            "test-only-secret-for-token-service-tests-at-least-32-bytes";

    @Test
    void issuesRequiredClaimsAndMatchingLifetime() {
        Instant now = Instant.parse("2026-10-22T12:00:00Z");
        TokenService service = serviceAt(now);

        TokenService.IssuedToken issued =
                service.issue("student", "angular-workshop");
        TokenService.TokenClaims claims = service.verify(issued.token());

        assertEquals("student", claims.sub());
        assertEquals("angular-workshop", claims.client());
        assertEquals(now.getEpochSecond(), claims.iat());
        assertEquals(claims.exp() - claims.iat(), issued.expiresIn());
        assertEquals(TokenService.TOKEN_TTL_SECONDS, issued.expiresIn());
    }

    @Test
    void rejectsExpiredToken() {
        Instant issuedAt = Instant.parse("2026-10-22T12:00:00Z");
        TokenService issuer = serviceAt(issuedAt);
        String token = issuer.issue("student", "angular-workshop").token();

        TokenService verifier = serviceAt(
                issuedAt.plusSeconds(TokenService.TOKEN_TTL_SECONDS));

        assertThrows(TokenService.InvalidTokenException.class,
                () -> verifier.verify(token));
    }

    @Test
    void rejectsInvalidClient() {
        TokenService service = serviceAt(Instant.parse("2026-10-22T12:00:00Z"));

        assertThrows(IllegalArgumentException.class,
                () -> service.issue("student", "angular workshop"));
    }

    @Test
    void rejectsTokenWithModifiedSignature() {
        TokenService service = serviceAt(Instant.parse("2026-10-22T12:00:00Z"));
        String token = service.issue("student", "angular-workshop").token();
        String modified = token.substring(0, token.length() - 1)
                + (token.endsWith("a") ? "b" : "a");

        assertThrows(TokenService.InvalidTokenException.class,
                () -> service.verify(modified));
    }

    private TokenService serviceAt(Instant instant) {
        return new TokenService(
                new ObjectMapper(),
                SECRET,
                Clock.fixed(instant, ZoneOffset.UTC));
    }
}