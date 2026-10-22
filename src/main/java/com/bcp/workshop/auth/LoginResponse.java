package com.bcp.workshop.auth;

// Conservar el tipo y mapeo de user que establezca el contrato existente.
public record LoginResponse(
        String token,
        long expiresIn,
        Object user
) {}