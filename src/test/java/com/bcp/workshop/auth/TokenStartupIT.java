package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenStartupIT {
    @Test
    void missingSecretPreventsStartup() throws Exception {
        assertStartupRejected(null);
    }

    @Test
    void invalidBase64PreventsStartupWithoutDisclosure() throws Exception {
        assertStartupRejected("synthetic-invalid-key!");
    }

    @Test
    void shortKeyPreventsStartupWithoutDisclosure() throws Exception {
        assertStartupRejected(Base64.getEncoder().encodeToString(new byte[31]));
    }

    private void assertStartupRejected(String configuredSecret) throws Exception {
        Path directory = Files.createTempDirectory(Path.of("target"), "token-startup-");
        Path output = directory.resolve("startup.log");
        Path database = directory.resolve("startup.db");
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder builder = new ProcessBuilder(javaExecutable, "-Dquarkus.http.port=0",
                "-D%prod.quarkus.http.host=127.0.0.1", "-jar", "target/quarkus-app/quarkus-run.jar")
                .redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().put("AUTH_DB_URL", "jdbc:sqlite:" + database.toAbsolutePath());
        if (configuredSecret == null) {
            builder.environment().remove("AUTH_TOKEN_SECRET");
        } else {
            builder.environment().put("AUTH_TOKEN_SECRET", configuredSecret);
        }
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Invalid configuration must stop startup");
            assertNotEquals(0, process.exitValue());
            String log = Files.readString(output);
            assertTrue(log.contains("AUTH_TOKEN_SECRET must be Base64 of at least 32 random bytes"));
            assertFalse(log.contains("Listening on:"));
            if (configuredSecret != null) {
                assertFalse(log.contains(configuredSecret), "Startup errors must not disclose configuration values");
            }
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(output);
            Files.deleteIfExists(Path.of(database + "-wal"));
            Files.deleteIfExists(Path.of(database + "-shm"));
            Files.deleteIfExists(database);
            Files.deleteIfExists(directory);
        }
    }
}