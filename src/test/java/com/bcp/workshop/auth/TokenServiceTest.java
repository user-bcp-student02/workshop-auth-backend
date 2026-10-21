package com.bcp.workshop.auth;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenServiceTest {
    private static final Instant EMISSION = Instant.parse("2026-10-21T00:00:00Z");
    private static final UserRecord USER = new UserRecord(1L, "student", "Student", "STUDENT");

    @Test
    void issuesRequiredClaimsFromStoredIdentityWithFixedClock() throws Exception {
        TokenService service = serviceAt(EMISSION);
        JWTClaimsSet claims = SignedJWT.parse(service.issue(USER, "angular-workshop").token()).getJWTClaimsSet();
        assertEquals("student", claims.getSubject());
        assertEquals("angular-workshop", claims.getStringClaim("client"));
        assertEquals(EMISSION, claims.getIssueTime().toInstant());
        assertEquals(EMISSION.plusSeconds(3600), claims.getExpirationTime().toInstant());
    }

    @Test
    void usesEachRequestClientWithoutNormalization() throws Exception {
        TokenService service = serviceAt(EMISSION);
        for (String client : new String[]{" Angular-Workshop ", "other-client"}) {
            JWTClaimsSet claims = SignedJWT.parse(service.issue(USER, client).token()).getJWTClaimsSet();
            assertEquals(client, claims.getStringClaim("client"));
        }
    }

    @Test
    void returnsDurationCoherentWithEmissionAndExpiry() throws Exception {
        TokenService.IssuedToken issued = serviceAt(EMISSION).issue(USER, "test-client");
        JWTClaimsSet claims = SignedJWT.parse(issued.token()).getJWTClaimsSet();
        long duration = claims.getExpirationTime().toInstant().getEpochSecond()
                - claims.getIssueTime().toInstant().getEpochSecond();
        assertEquals(3600, issued.expiresIn());
        assertEquals(issued.expiresIn(), duration);
        assertEquals(issued.expiresIn(), claims.getExpirationTime().toInstant().getEpochSecond() - EMISSION.getEpochSecond());
    }

    private TokenService serviceAt(Instant instant) {
        TokenSecret secret = new TokenSecret(Optional.of(Base64.getEncoder().encodeToString(new byte[32])));
        return new TokenService(secret, Clock.fixed(instant, ZoneOffset.UTC));
    }
}