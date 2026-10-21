package com.bcp.workshop.auth;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Base64;
import java.util.Optional;

@Startup
@ApplicationScoped
public class TokenSecret {
    private static final int MINIMUM_KEY_BYTES = 32;
    private final byte[] signingKey;

    @Inject
    public TokenSecret(@ConfigProperty(name = "AUTH_TOKEN_SECRET") Optional<String> configuredSecret) {
        try {
            signingKey = Base64.getDecoder().decode(configuredSecret.orElseThrow(IllegalArgumentException::new));
            if (signingKey.length < MINIMUM_KEY_BYTES) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("AUTH_TOKEN_SECRET must be Base64 of at least 32 random bytes");
        }
    }

    byte[] signingKey() {
        return signingKey.clone();
    }
}