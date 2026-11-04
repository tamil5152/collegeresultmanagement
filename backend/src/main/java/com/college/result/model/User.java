package com.college.result.model;

public record User(long id, String username, String email, String passwordHash, String fullName,
                   Role role, boolean active) {}
