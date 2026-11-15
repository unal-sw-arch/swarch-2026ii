package com.kanasta.accounts;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

public record User(
        int id,
        String name,
        String email,
        @JsonIgnore String passwordHash,
        String phone,
        String address,
        String role,
        boolean active,
        LocalDateTime createdAt) {
}
