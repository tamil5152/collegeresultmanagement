package com.college.result.dto;

import jakarta.validation.constraints.NotNull;

public final class AssignmentDtos {

    private AssignmentDtos() {}

    public record AssignmentRequest(
            @NotNull(message = "Subject is required") Long subjectId,
            @NotNull(message = "Faculty is required") Long facultyId,
            @NotNull(message = "Semester is required") Long semesterId) {}

    public record AssignmentUpdateRequest(@NotNull(message = "Faculty is required") Long facultyId) {}

    public record AssignmentResponse(long id, long subjectId, String subjectCode, String subjectName,
                                     int subjectSemesterNumber, long facultyId, String facultyName,
                                     String employeeCode, long semesterId, String semesterLabel,
                                     long enrolledStudents) {}

    public record EnrollmentRequest(
            @NotNull(message = "Student is required") Long studentId,
            @NotNull(message = "Subject is required") Long subjectId,
            @NotNull(message = "Semester is required") Long semesterId) {}

    public record BulkEnrollRequest(
            @NotNull(message = "Semester is required") Long semesterId,
            @NotNull(message = "Department is required") Long departmentId) {}

    public record BulkEnrollResult(int newEnrollments) {}

    public record EnrollmentResponse(long id, long studentId, String rollNumber, String studentName, long subjectId,
                                     String subjectCode, String subjectName, long semesterId, String semesterLabel) {}
}
