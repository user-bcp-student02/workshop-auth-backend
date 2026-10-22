package com.bcp.workshop.auth;

/**
 * Compatibilidad temporal de compilación. La emisión Base64 heredada quedó
 * deshabilitada por #12 / H-03: AuthResource debe emitir mediante TokenService.
 */
@Deprecated(forRemoval = true)
public final class LegacyTokenUtil {
    private LegacyTokenUtil() {}

    public static String create(String username, String role) {
        throw new UnsupportedOperationException("Legacy token format is disabled");
    }
}