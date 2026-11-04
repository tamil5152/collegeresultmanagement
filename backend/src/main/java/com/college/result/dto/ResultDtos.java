package com.college.result.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ResultDtos {

    private ResultDtos() {}

    public record PublicationRequest(
            @NotNull(message = "Examination session is required") Long sessionId,
            @NotNull(message = "Department is required") Long departmentId) {}

    public record PublicationStatus(long sessionId, long departmentId, String sessionName, String departmentName,
                                    long studentsToPublish, long studentsAlreadyPublished, long readyStudents,
                                    long studentsWithMissingMarks, long studentsWithUnapprovedMarks,
                                    String activeGradingRule, boolean canPublish, String message,
                                    List<String> blockedStudents) {}

    public record PublishResult(int published) {}

    public record SubjectResult(String subjectCode, String subjectName, BigDecimal credits, BigDecimal internalMarks,
                                BigDecimal externalMarks, BigDecimal totalMarks, BigDecimal maxMarks,
                                BigDecimal percentage, String grade, BigDecimal gradePoint, boolean passed) {}

    /** One published semester result in a student's list. cgpa is the cumulative value up to this result. */
    public record StudentResultSummary(long resultId, long sessionId, String sessionName, String academicYear,
                                       int semesterNumber, BigDecimal sgpa, BigDecimal cgpa, BigDecimal totalCredits,
                                       BigDecimal earnedCredits, String resultStatus, int revision,
                                       Instant publishedAt) {}

    public record ResultDetail(long resultId, long studentId, String rollNumber, String studentName,
                               String departmentName, int batchYear, String sessionName, String academicYear,
                               int semesterNumber, String semesterName, List<SubjectResult> subjects,
                               BigDecimal totalCredits, BigDecimal earnedCredits, BigDecimal sgpa, BigDecimal cgpa,
                               String resultStatus, int revision, String gradingRule, boolean gradingRuleIsSample,
                               Instant publishedAt) {}

    public record StudentDashboard(String fullName, String rollNumber, String departmentName, int currentSemester,
                                   int publishedResults, BigDecimal latestSgpa, BigDecimal cgpa,
                                   BigDecimal earnedCredits, String latestSessionName) {}

    public record AdminResultRow(long resultId, long studentId, String rollNumber, String studentName,
                                 String departmentCode, long sessionId, String sessionName, BigDecimal sgpa,
                                 String resultStatus, int revision, Instant publishedAt) {}
}
