package com.college.result.dto;

import com.college.result.common.Patterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class UserDtos {

    private UserDtos() {}

    // ---- students
    public record StudentCreateRequest(
            @NotBlank @Pattern(regexp = Patterns.USERNAME, message = Patterns.USERNAME_MESSAGE) String username,
            @NotBlank(message = "Password is required")
            @Pattern(regexp = Patterns.PASSWORD, message = Patterns.PASSWORD_MESSAGE) String password,
            @NotBlank(message = "Email is required") @Email @Size(max = 150) String email,
            @NotBlank(message = "Full name is required") @Size(max = 150) String fullName,
            @NotNull(message = "Department is required") Long departmentId,
            @NotBlank(message = "Roll number is required") @Size(max = 30) String rollNumber,
            @NotNull(message = "Batch year is required") @Min(1990) @Max(2200) Integer batchYear,
            @NotNull(message = "Current semester is required") @Min(1) @Max(12) Integer currentSemester,
            @Size(max = 20) String phone,
            LocalDate dateOfBirth) {}

    /** Username is immutable; password is optional and, when present, resets the student's password. */
    public record StudentUpdateRequest(
            @Pattern(regexp = Patterns.PASSWORD, message = Patterns.PASSWORD_MESSAGE) String password,
            @NotBlank(message = "Email is required") @Email @Size(max = 150) String email,
            @NotBlank(message = "Full name is required") @Size(max = 150) String fullName,
            @NotNull(message = "Department is required") Long departmentId,
            @NotBlank(message = "Roll number is required") @Size(max = 30) String rollNumber,
            @NotNull(message = "Batch year is required") @Min(1990) @Max(2200) Integer batchYear,
            @NotNull(message = "Current semester is required") @Min(1) @Max(12) Integer currentSemester,
            @Size(max = 20) String phone,
            LocalDate dateOfBirth) {}

    public record StudentResponse(long id, long userId, String username, String email, String fullName,
                                  long departmentId, String departmentCode, String departmentName, String rollNumber,
                                  int batchYear, int currentSemester, String phone, LocalDate dateOfBirth,
                                  boolean active) {}

    // ---- faculty
    public record FacultyCreateRequest(
            @NotBlank @Pattern(regexp = Patterns.USERNAME, message = Patterns.USERNAME_MESSAGE) String username,
            @NotBlank(message = "Password is required")
            @Pattern(regexp = Patterns.PASSWORD, message = Patterns.PASSWORD_MESSAGE) String password,
            @NotBlank(message = "Email is required") @Email @Size(max = 150) String email,
            @NotBlank(message = "Full name is required") @Size(max = 150) String fullName,
            @NotNull(message = "Department is required") Long departmentId,
            @NotBlank(message = "Employee code is required") @Size(max = 30) String employeeCode,
            @Size(max = 100) String designation,
            @Size(max = 20) String phone) {}

    public record FacultyUpdateRequest(
            @Pattern(regexp = Patterns.PASSWORD, message = Patterns.PASSWORD_MESSAGE) String password,
            @NotBlank(message = "Email is required") @Email @Size(max = 150) String email,
            @NotBlank(message = "Full name is required") @Size(max = 150) String fullName,
            @NotNull(message = "Department is required") Long departmentId,
            @NotBlank(message = "Employee code is required") @Size(max = 30) String employeeCode,
            @Size(max = 100) String designation,
            @Size(max = 20) String phone) {}

    public record FacultyResponse(long id, long userId, String username, String email, String fullName,
                                  long departmentId, String departmentCode, String employeeCode, String designation,
                                  String phone, boolean active) {}
}
