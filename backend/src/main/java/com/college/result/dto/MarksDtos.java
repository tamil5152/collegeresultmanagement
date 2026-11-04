package com.college.result.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class MarksDtos {

    private MarksDtos() {}

    // ---- faculty: assignments
    public record SessionBrief(long id, String name, String status) {}

    public record FacultyAssignment(long assignmentId, long subjectId, String subjectCode, String subjectName,
                                    long semesterId, String semesterLabel, long enrolledStudents,
                                    List<SessionBrief> sessions) {}

    public record EnrolledStudentResponse(long studentId, String rollNumber, String fullName) {}

    // ---- faculty: marks entry
    public record MarkEntry(
            @NotNull(message = "Student is required") Long studentId,
            @DecimalMin(value = "0", message = "Marks cannot be negative")
            @Digits(integer = 4, fraction = 2, message = "At most 2 decimal places") BigDecimal internalMarks,
            @DecimalMin(value = "0", message = "Marks cannot be negative")
            @Digits(integer = 4, fraction = 2, message = "At most 2 decimal places") BigDecimal externalMarks) {}

    public record SaveMarksRequest(
            @NotEmpty(message = "At least one entry is required") @Size(max = 500) @Valid List<MarkEntry> entries) {}

    public record MarkSheetRow(long studentId, String rollNumber, String studentName, Long markId,
                               BigDecimal internalMarks, BigDecimal externalMarks, String status,
                               String reviewRemarks, boolean editable) {}

    public record MarkSheet(long assignmentId, long sessionId, String sessionName, String sessionStatus,
                            long subjectId, String subjectCode, String subjectName, BigDecimal internalMax,
                            BigDecimal externalMax, BigDecimal internalPass, BigDecimal externalPass,
                            BigDecimal totalPass, boolean sessionOpen, List<MarkSheetRow> rows) {}

    public record SubmitResult(int submitted) {}

    public record SubmissionSummary(long assignmentId, long sessionId, String sessionName, String sessionStatus,
                                    String subjectCode, String subjectName, long enrolled, long notEntered,
                                    long draft, long submitted, long approved, long rejected, long published) {}

    public record FacultyDashboard(long assignedSubjects, long openSessions, long draftOrRejected, long submitted,
                                   long approved, long published) {}

    // ---- admin: review and correction
    public record MarkReviewRow(long id, long studentId, String rollNumber, String studentName,
                                String departmentCode, long subjectId, String subjectCode, String subjectName,
                                long sessionId, String sessionName, BigDecimal internalMarks,
                                BigDecimal externalMarks, BigDecimal internalMax, BigDecimal externalMax,
                                String status, String facultyName, Instant submittedAt, Instant reviewedAt,
                                String reviewRemarks) {}

    public record ReviewRequest(
            @NotEmpty(message = "Select at least one mark") @Size(max = 500) List<Long> markIds,
            @NotBlank(message = "Action is required") String action,
            @Size(max = 500) String remarks) {}

    public record ReviewResult(int reviewed, String action) {}

    public record CorrectionRequest(
            @NotNull(message = "Internal marks are required") @DecimalMin("0") @Digits(integer = 4, fraction = 2)
            BigDecimal internalMarks,
            @NotNull(message = "External marks are required") @DecimalMin("0") @Digits(integer = 4, fraction = 2)
            BigDecimal externalMarks,
            @NotBlank(message = "A reason is required") @Size(min = 10, max = 500,
                    message = "Reason must be 10-500 characters") String reason) {}
}
