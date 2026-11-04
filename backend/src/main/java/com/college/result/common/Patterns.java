package com.college.result.common;

public final class Patterns {

    private Patterns() {}

    /** At least 8 characters with a letter and a digit. */
    public static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d).{8,100}$";
    public static final String PASSWORD_MESSAGE = "Password must be 8-100 characters and contain a letter and a digit";
    public static final String USERNAME = "^[A-Za-z0-9._-]{3,50}$";
    public static final String USERNAME_MESSAGE = "Username must be 3-50 characters: letters, digits, '.', '_' or '-'";
}
