package com.bcp.workshop.auth;

import java.time.Clock;

public class TokenService {
    public TokenService(TokenSecret secret, Clock clock) {
    }

    public IssuedToken issue(UserRecord user, String client) {
        throw new UnsupportedOperationException("Token emission is not implemented");
    }

    public record IssuedToken(String token, int expiresIn) {
        @Override
        public String toString() {
            return "IssuedToken[redacted]";
        }
    }
}