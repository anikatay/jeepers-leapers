package com.neueda.leap.model;

import java.util.UUID;

public class User {

    private UUID userId;

    private String role; // ROLE_CUSTOMER, ROLE_ADMIN, ROLE_ANALYST

    private String status = "ACTIVE"; // ACTIVE, INACTIVE

    public User() {
    }

    public User(UUID userId, String role, String status) {
        this.userId = userId;
        this.role = role;
        this.status = status;
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}