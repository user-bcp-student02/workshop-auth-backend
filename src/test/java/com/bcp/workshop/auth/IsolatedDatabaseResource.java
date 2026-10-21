package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Map;

public class IsolatedDatabaseResource implements QuarkusTestResourceLifecycleManager {
    private Path databasePath;

    @Override
    public Map<String, String> start() {
        String databaseUrl = System.getenv("AUTH_DB_URL");
        String prefix = "jdbc:sqlite:";
        if (databaseUrl == null || !databaseUrl.startsWith(prefix)) {
            throw new IllegalStateException("Set AUTH_DB_URL to a fresh characterization database under target");
        }
        Path candidate = Path.of(databaseUrl.substring(prefix.length())).toAbsolutePath().normalize();
        Path target = Path.of("target").toAbsolutePath().normalize();
        String filename = candidate.getFileName().toString();
        if (!candidate.getParent().equals(target) || !filename.startsWith("characterization-")
                || !filename.endsWith(".db") || Files.exists(candidate)) {
            throw new IllegalStateException("Characterization requires a new target/characterization-*.db file");
        }
        try {
            Files.createDirectories(target);
            Files.createFile(candidate);
            databasePath = candidate;
            try (var connection = DriverManager.getConnection(databaseUrl);
                 var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
                try (var insert = connection.prepareStatement(
                        "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
                    insert.setString(1, "character-user");
                    insert.setString(2, "Characterization123!");
                    insert.setString(3, "<Student & Trainer>");
                    insert.setString(4, "AUDITOR");
                    insert.executeUpdate();
                }
            }
            return Map.of("quarkus.http.test-port", "0", "quarkus.http.host", "127.0.0.1",
                    "%prod.quarkus.http.host", "127.0.0.1");
        } catch (Exception error) {
            throw new IllegalStateException("Unable to initialize the isolated characterization database");
        }
    }

    @Override
    public void stop() {
        if (databasePath != null) {
            try {
                Files.deleteIfExists(Path.of(databasePath + "-wal"));
                Files.deleteIfExists(Path.of(databasePath + "-shm"));
                Files.deleteIfExists(databasePath);
            } catch (Exception error) {
                throw new IllegalStateException("Unable to remove the isolated characterization database");
            }
        }
    }
}