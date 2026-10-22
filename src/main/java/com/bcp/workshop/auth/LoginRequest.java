package com.bcp.workshop.auth;

public class LoginRequest {
    public String username;
    public String password;
    public String client;

    public boolean hasValidClient() {
        return TokenService.isValidClient(client);
    }
}