package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenSecretTest {
    @Test
    void acceptsExplicitSyntheticKeyAndReturnsDefensiveCopies() {
        byte[] key = new byte[32];
        TokenSecret secret = new TokenSecret(Optional.of(Base64.getEncoder().encodeToString(key)));
        byte[] copy = secret.signingKey();
        copy[0] = 1;
        assertArrayEquals(key, secret.signingKey());
    }

    @Test
    void rejectsMissingConfigurationWithoutDefault() {
        assertSafeRejection(Optional.empty());
    }

    @Test
    void rejectsEmptyConfiguration() {
        assertSafeRejection(Optional.of(""));
    }

    @Test
    void rejectsInvalidBase64WithoutEchoingItsValue() {
        assertSafeRejection(Optional.of("synthetic-invalid-secret!"));
    }

    @Test
    void rejectsInsufficientKeyMaterial() {
        assertSafeRejection(Optional.of(Base64.getEncoder().encodeToString(new byte[31])));
    }

    private void assertSafeRejection(Optional<String> configuredSecret) {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new TokenSecret(configuredSecret));
        assertEquals("AUTH_TOKEN_SECRET must be Base64 of at least 32 random bytes", failure.getMessage());
        assertNull(failure.getCause());
    }
}