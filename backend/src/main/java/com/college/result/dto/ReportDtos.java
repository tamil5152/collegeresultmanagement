package com.college.result.dto;

import java.math.BigDecimal;

public final class ReportDtos {

    private ReportDtos() {}

    public record DepartmentSummaryRow(long departmentId, String departmentCode, String departmentName, long students,
                                       long passed, long failed, BigDecimal passPercentage, BigDecimal averageSgpa) {}

    public record SubjectAnalysisRow(String subjectCode, String subjectName, long appeared, long passed, long failed,
                                     BigDecimal passPercentage, BigDecimal averageTotal, BigDecimal highestTotal,
                                     BigDecimal maxTotal) {}

    public record GradeDistributionRow(String grade, long count) {}

    public record MarksStatusRow(String departmentCode, String departmentName, long draft, long submitted,
                                 long approved, long rejected, long published) {}

    public record AdminDashboard(long students, long faculty, long departments, long subjects, long openSessions,
                                 long marksAwaitingReview, long publishedResults, String activeGradingRule) {}
}
