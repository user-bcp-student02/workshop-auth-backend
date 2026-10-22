package com.bcp.workshop.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Emisión y verificación de JWT HS256 para #12 / H-03.
 * El secreto es obligatorio y no tiene valor por defecto.
 */
@ApplicationScoped
public class TokenService {
    public static final long TOKEN_TTL_SECONDS = 3_600L;
    public static final long CLOCK_SKEW_SECONDS = 1L;
    private static final int MIN_SECRET_BYTES = 32;
    private static final int MAX_SUBJECT_LENGTH = 256;
    private static final Pattern CLIENT_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    private static final String JWT_HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final ObjectMapper mapper;
    private final byte[] secret;
    private final Clock clock;

    @Inject
    public TokenService(
            ObjectMapper mapper,
            @ConfigProperty(name = "auth.token.secret") String secret) {
        this(mapper, secret, Clock.systemUTC());
    }

    TokenService(ObjectMapper mapper, String secret, Clock clock) {
        this.mapper = Objects.requireNonNull(mapper);
        this.clock = Objects.requireNonNull(clock);

        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("AUTH_TOKEN_SECRET must contain at least 32 bytes");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public IssuedToken issue(String authenticatedUsername, String requestClient) {
        validateSubject(authenticatedUsername);
        if (!isValidClient(requestClient)) {
            throw new IllegalArgumentException("Invalid client identifier");
        }

        long issuedAt = Instant.now(clock).getEpochSecond();
        long expiresAt = Math.addExact(issuedAt, TOKEN_TTL_SECONDS);

        var claims = mapper.createObjectNode();
        claims.put("sub", authenticatedUsername);
        claims.put("client", requestClient);
        claims.put("iat", issuedAt);
        claims.put("exp", expiresAt);

        String encodedHeader = ENCODER.encodeToString(JWT_HEADER.getBytes(StandardCharsets.UTF_8));
        String encodedClaims = ENCODER.encodeToString(toJson(claims));
        String signingInput = encodedHeader + "." + encodedClaims;

        return new IssuedToken(signingInput + "." + sign(signingInput),
                expiresAt - issuedAt);
    }

    public TokenClaims verify(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 3) {
                throw new InvalidTokenException();
            }

            JsonNode header = mapper.readTree(DECODER.decode(parts[0]));
            if (!"HS256".equals(header.path("alg").asText())
                    || !"JWT".equals(header.path("typ").asText())) {
                throw new InvalidTokenException();
            }

            String signingInput = parts[0] + "." + parts[1];
            byte[] providedSignature = DECODER.decode(parts[2]);
            byte[] expectedSignature = DECODER.decode(sign(signingInput));
            if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
                throw new InvalidTokenException();
            }

            JsonNode claims = mapper.readTree(DECODER.decode(parts[1]));
            String subject = claims.path("sub").asText(null);
            String client = claims.path("client").asText(null);
            long issuedAt = integralClaim(claims, "iat");
            long expiresAt = integralClaim(claims, "exp");
            long now = Instant.now(clock).getEpochSecond();

            validateSubject(subject);
            if (!isValidClient(client)
                    || issuedAt > now + CLOCK_SKEW_SECONDS
                    || expiresAt <= now
                    || expiresAt <= issuedAt
                    || expiresAt - issuedAt > TOKEN_TTL_SECONDS) {
                throw new InvalidTokenException();
            }

            return new TokenClaims(subject, client, issuedAt, expiresAt);
        } catch (InvalidTokenException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidTokenException();
        }
    }

    public static boolean isValidClient(String client) {
        return client != null && CLIENT_PATTERN.matcher(client).matches();
    }

    private void validateSubject(String subject) {
        if (subject == null || subject.isBlank() || subject.length() > MAX_SUBJECT_LENGTH) {
            throw new IllegalArgumentException("Invalid token subject");
        }
        for (int index = 0; index < subject.length(); index++) {
            if (Character.isISOControl(subject.charAt(index))) {
                throw new IllegalArgumentException("Invalid token subject");
            }
        }
    }

    private long integralClaim(JsonNode claims, String name) {
        JsonNode value = claims.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new InvalidTokenException();
        }
        return value.longValue();
    }

    private byte[] toJson(JsonNode value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize token claims");
        }
    }

    private String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return ENCODER.encodeToString(
                    mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Token signing is unavailable");
        }
    }

    public record IssuedToken(String token, long expiresIn) {}

    public record TokenClaims(String sub, String client, long iat, long exp) {}

    public static final class InvalidTokenException extends RuntimeException {
        public InvalidTokenException() {
            super("Invalid or expired token");
        }
    }
}