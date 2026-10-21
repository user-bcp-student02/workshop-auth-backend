package com.bcp.workshop.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

@ApplicationScoped
public class TokenService {
    private static final int LIFETIME_SECONDS = 3600;
    private final byte[] signingKey;
    private final Clock clock;

    @Inject
    public TokenService(TokenSecret secret) {
        this(secret, Clock.systemUTC());
    }

    public TokenService(TokenSecret secret, Clock clock) {
        this.signingKey = secret.signingKey();
        this.clock = clock;
    }

    public IssuedToken issue(UserRecord user, String client) {
        if (user == null || !isValidClient(user.username()) || !isValidClient(client)) {
            throw new IllegalArgumentException("Invalid token identity or client");
        }
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(LIFETIME_SECONDS);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.username()).claim("client", client).claim("role", user.role())
                .issueTime(Date.from(issuedAt)).expirationTime(Date.from(expiresAt)).build();
        SignedJWT token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT).build(), claims);
        try {
            token.sign(new MACSigner(signingKey));
            return new IssuedToken(token.serialize(),
                    Math.toIntExact(expiresAt.getEpochSecond() - issuedAt.getEpochSecond()));
        } catch (JOSEException error) {
            throw new IllegalStateException("Unable to issue token");
        }
    }

    static boolean isValidClient(String client) {
        return client != null && !client.trim().isEmpty();
    }

    public JWTClaimsSet verify(String token) {
        try {
            SignedJWT signed = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(signed.getHeader().getAlgorithm())
                    || !signed.verify(new MACVerifier(signingKey))) {
                throw new IllegalArgumentException();
            }
            Map<String, Object> claims = JSONObjectUtils.parse(signed.getPayload().toString());
            if (!(claims.get("sub") instanceof String subject) || !isValidClient(subject)
                    || !(claims.get("client") instanceof String client) || !isValidClient(client)) {
                throw new IllegalArgumentException();
            }
            long issuedAt = numericDate(claims.get("iat"));
            long expiresAt = numericDate(claims.get("exp"));
            long now = clock.instant().getEpochSecond();
            if (Math.subtractExact(expiresAt, issuedAt) != LIFETIME_SECONDS
                    || issuedAt > now || now >= expiresAt) {
                throw new IllegalArgumentException();
            }
            return signed.getJWTClaimsSet();
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid token");
        }
    }

    private long numericDate(Object value) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException();
        }
        return new BigDecimal(value.toString()).longValueExact();
    }

    public record IssuedToken(String token, int expiresIn) {
        @Override
        public String toString() {
            return "IssuedToken[redacted]";
        }
    }
}