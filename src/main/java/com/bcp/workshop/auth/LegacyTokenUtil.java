package com.bcp.workshop.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

public final class LegacyTokenUtil {
    private static final String ISSUER = "workshop-auth-backend";
    private static final long TOKEN_TTL_SECONDS = 3600;

    private LegacyTokenUtil() {}

    public static String create(String username, String role, String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("WORKSHOP_TOKEN_SECRET must contain at least 32 bytes");
        }
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(username)
                .withClaim("role", role)
                .withExpiresAt(Instant.now().plusSeconds(TOKEN_TTL_SECONDS))
                .sign(Algorithm.HMAC256(secret));
    }
}
