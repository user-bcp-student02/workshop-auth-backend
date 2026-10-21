package com.bcp.workshop.auth;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jose.util.JSONObjectUtils;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        long observedNow = EMISSION.plusSeconds(2).getEpochSecond();
        assertTrue(Math.abs(issued.expiresIn() - (claims.getExpirationTime().toInstant().getEpochSecond() - observedNow)) <= 2);
    }

    @Test
    void payloadContainsOnlyPublicClaimsAndNoKeyMaterial() throws Exception {
        TokenService.IssuedToken issued = serviceAt(EMISSION).issue(USER, "test-client");
        SignedJWT token = SignedJWT.parse(issued.token());
        assertEquals(Set.of("sub", "client", "role", "iat", "exp"), token.getJWTClaimsSet().getClaims().keySet());
        assertFalse(token.getPayload().toString().contains(Base64.getEncoder().encodeToString(new byte[32])));
        assertFalse(token.getPayload().toString().contains(USER.displayName()));
        assertEquals("IssuedToken[redacted]", issued.toString());
    }

    @Test
    void acceptsAuthenticTokenBeforeExpiry() throws Exception {
        String token = serviceAt(EMISSION).issue(USER, "client").token();
        assertEquals("student", serviceAt(EMISSION.plusSeconds(3599)).verify(token).getSubject());
    }

    @Test
    void rejectsExactlyAtExpiryAndAfterwards() {
        String token = serviceAt(EMISSION).issue(USER, "client").token();
        for (long offset : new long[]{3600, 3601}) {
            assertThrows(IllegalArgumentException.class,
                    () -> serviceAt(EMISSION.plusSeconds(offset)).verify(token));
        }
    }

    @Test
    void rejectsChangedSubjectClientExpiryOrSignature() throws Exception {
        SignedJWT token = SignedJWT.parse(serviceAt(EMISSION).issue(USER, "client").token());
        Map<String, Object> original = JSONObjectUtils.parse(token.getPayload().toString());
        for (String field : new String[]{"sub", "client", "exp"}) {
            Map<String, Object> altered = new HashMap<>(original);
            altered.put(field, field.equals("exp") ? EMISSION.plusSeconds(7200).getEpochSecond() : "changed");
            String tampered = token.getHeader().toBase64URL() + "."
                    + Base64URL.encode(JSONObjectUtils.toJSONString(altered)) + "." + token.getSignature();
            assertThrows(IllegalArgumentException.class, () -> serviceAt(EMISSION).verify(tampered));
        }
        String signature = token.getSignature().toString();
        String tamperedSignature = token.getHeader().toBase64URL() + "." + token.getPayload().toBase64URL()
                + "." + (signature.startsWith("A") ? "B" : "A") + signature.substring(1);
        assertThrows(IllegalArgumentException.class, () -> serviceAt(EMISSION).verify(tamperedSignature));
    }

    @Test
    void rejectsUnrelatedKeyAndUnauthorizedAlgorithms() throws Exception {
        String token = serviceAt(EMISSION).issue(USER, "client").token();
        byte[] differentKey = new byte[32];
        differentKey[0] = 1;
        TokenService other = new TokenService(new TokenSecret(Optional.of(Base64.getEncoder().encodeToString(differentKey))),
                Clock.fixed(EMISSION, ZoneOffset.UTC));
        assertThrows(IllegalArgumentException.class, () -> other.verify(token));
        JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
        String unsecured = new PlainJWT(claims).serialize();
        assertThrows(IllegalArgumentException.class, () -> serviceAt(EMISSION).verify(unsecured));
        JWSObject wrongAlgorithm = new JWSObject(new JWSHeader(JWSAlgorithm.HS384), new Payload(claims.toJSONObject()));
        wrongAlgorithm.sign(new MACSigner(new byte[64]));
        assertThrows(IllegalArgumentException.class, () -> serviceAt(EMISSION).verify(wrongAlgorithm.serialize()));
    }

    @Test
    void rejectsMissingMandatoryClaimsEvenWithValidSignature() throws Exception {
        Map<String, Object> original = validPayload();
        for (String field : new String[]{"sub", "client", "iat", "exp"}) {
            Map<String, Object> incomplete = new HashMap<>(original);
            incomplete.remove(field);
            String token = signPayload(incomplete);
            assertThrows(IllegalArgumentException.class, () -> serviceAt(EMISSION).verify(token));
        }
    }

    @Test
    void rejectsInvalidClaimTypesAndIncoherentTimes() throws Exception {
        for (Map<String, Object> replacement : List.<Map<String, Object>>of(
                Map.of("sub", 12), Map.of("sub", " "), Map.of("client", 12), Map.of("client", "\t"),
                Map.of("iat", "not-a-number"), Map.of("exp", "not-a-number"),
                Map.of("iat", EMISSION.getEpochSecond() + 0.5), Map.of("exp", EMISSION.getEpochSecond() - 1),
                Map.of("exp", EMISSION.getEpochSecond() + 7200))) {
            Map<String, Object> invalid = new HashMap<>(validPayload());
            invalid.putAll(replacement);
            String token = signPayload(invalid);
            assertThrows(IllegalArgumentException.class, () -> serviceAt(EMISSION).verify(token));
        }
    }

    @Test
    void rejectsMalformedAndLegacyTokensWithoutDisclosure() {
        for (String token : new String[]{"", "not-a-token", Base64.getEncoder()
                .encodeToString("synthetic|role|expiry|synthetic".getBytes(java.nio.charset.StandardCharsets.UTF_8))}) {
            assertThrows(IllegalArgumentException.class, () -> serviceAt(EMISSION).verify(token));
        }
        assertThrows(IllegalArgumentException.class, () -> serviceAt(EMISSION).verify(null));
    }

    @Test
    void rejectsInvalidIdentityAndClientBeforeSigning() {
        TokenService service = serviceAt(EMISSION);
        assertThrows(IllegalArgumentException.class, () -> service.issue(null, "client"));
        assertThrows(IllegalArgumentException.class,
                () -> service.issue(new UserRecord(2L, null, "Name", "ROLE"), "client"));
        for (String client : new String[]{null, "", " \t "}) {
            assertThrows(IllegalArgumentException.class, () -> service.issue(USER, client));
        }
    }

    private Map<String, Object> validPayload() throws Exception {
        return JSONObjectUtils.parse(SignedJWT.parse(serviceAt(EMISSION).issue(USER, "client").token()).getPayload().toString());
    }

    private String signPayload(Map<String, Object> claims) throws Exception {
        JWSObject token = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(claims));
        token.sign(new MACSigner(new byte[32]));
        return token.serialize();
    }

    private TokenService serviceAt(Instant instant) {
        TokenSecret secret = new TokenSecret(Optional.of(Base64.getEncoder().encodeToString(new byte[32])));
        return new TokenService(secret, Clock.fixed(instant, ZoneOffset.UTC));
    }
}