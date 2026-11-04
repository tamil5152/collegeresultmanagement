package com.college.result.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Request / response records for departments, academic years, semesters, subjects, sessions and grading rules. */
public final class MasterDtos {

    private MasterDtos() {}

    public record ActiveRequest(@NotNull(message = "active is required") Boolean active) {}

    /** Drop-down entry; meta carries the semester number for semester / subject options (otherwise null). */
    public record Option(long id, String label, Integer meta) {}

    // ---- departments
    public record DepartmentRequest(
            @NotBlank(message = "Code is required") @Size(max = 20) String code,
            @NotBlank(message = "Name is required") @Size(max = 150) String name) {}

    public record DepartmentResponse(long id, String code, String name, boolean active) {}

    // ---- academic years
    public record AcademicYearRequest(
            @NotBlank(message = "Label is required") @Size(max = 20) String label,
            @NotNull(message = "Start date is required") LocalDate startDate,
            @NotNull(message = "End date is required") LocalDate endDate) {}

    public record AcademicYearResponse(long id, String label, LocalDate startDate, LocalDate endDate, boolean active) {}

    // ---- semesters
    public record SemesterRequest(
            @NotNull(message = "Academic year is required") Long academicYearId,
            @NotNull(message = "Semester number is required") @Min(1) @Max(12) Integer semesterNumber,
            @NotBlank(message = "Name is required") @Size(max = 100) String name,
            @NotNull(message = "Start date is required") LocalDate startDate,
            @NotNull(message = "End date is required") LocalDate endDate) {}

    public record SemesterResponse(long id, long academicYearId, String academicYearLabel, int semesterNumber,
                                   String name, LocalDate startDate, LocalDate endDate) {}

    // ---- subjects
    public record SubjectRequest(
            @NotNull(message = "Department is required") Long departmentId,
            @NotBlank(message = "Code is required") @Size(max = 20) String code,
            @NotBlank(message = "Name is required") @Size(max = 150) String name,
            @NotNull(message = "Semester number is required") @Min(1) @Max(12) Integer semesterNumber,
            @NotNull(message = "Credits are required") @DecimalMin(value = "0.01") @DecimalMax("99.99")
            @Digits(integer = 2, fraction = 2) BigDecimal credits,
            @NotNull(message = "Internal maximum is required") @DecimalMin("0") @DecimalMax("9999.99")
            @Digits(integer = 4, fraction = 2) BigDecimal internalMax,
            @NotNull(message = "External maximum is required") @DecimalMin("0") @DecimalMax("9999.99")
            @Digits(integer = 4, fraction = 2) BigDecimal externalMax,
            @NotNull(message = "Internal pass mark is required") @DecimalMin("0") @Digits(integer = 4, fraction = 2)
            BigDecimal internalPass,
            @NotNull(message = "External pass mark is required") @DecimalMin("0") @Digits(integer = 4, fraction = 2)
            BigDecimal externalPass,
            @NotNull(message = "Total pass mark is required") @DecimalMin("0") @Digits(integer = 4, fraction = 2)
            BigDecimal totalPass) {}

    public record SubjectResponse(long id, long departmentId, String departmentCode, String code, String name,
                                  int semesterNumber, BigDecimal credits, BigDecimal internalMax,
                                  BigDecimal externalMax, BigDecimal internalPass, BigDecimal externalPass,
                                  BigDecimal totalPass, boolean active) {}

    // ---- examination sessions
    public record ExamSessionRequest(
            @NotBlank(message = "Name is required") @Size(max = 150) String name,
            @NotNull(message = "Semester is required") Long semesterId,
            @NotNull(message = "Start date is required") LocalDate startDate,
            @NotNull(message = "End date is required") LocalDate endDate,
            @NotNull(message = "Status is required") String status) {}

    public record ExamSessionResponse(long id, String name, long semesterId, String semesterName,
                                      String academicYearLabel, int semesterNumber, LocalDate startDate,
                                      LocalDate endDate, String status) {}

    // ---- grading rules
    public record BandRequest(
            @NotBlank(message = "Grade is required") @Size(max = 5) String grade,
            @NotNull(message = "Minimum percentage is required") @DecimalMin("0") @DecimalMax("100")
            @Digits(integer = 3, fraction = 2) BigDecimal minPercentage,
            @NotNull(message = "Grade point is required") @DecimalMin("0") @DecimalMax("99.99")
            @Digits(integer = 2, fraction = 2) BigDecimal gradePoint,
            @NotNull(message = "failGrade is required") Boolean failGrade) {}

    public record GradingRuleRequest(
            @NotBlank(message = "Name is required") @Size(max = 100) String name,
            @Size(max = 500) String description,
            @NotNull Boolean samplePlaceholder,
            @NotNull Boolean includeFailedInGpa,
            @NotEmpty(message = "At least one grade band is required") @Valid List<BandRequest> bands) {}

    public record BandResponse(long id, String grade, BigDecimal minPercentage, BigDecimal gradePoint,
                               boolean failGrade) {}

    public record GradingRuleResponse(long id, String name, String description, boolean samplePlaceholder,
                                      boolean includeFailedInGpa, boolean active, boolean usedByResults,
                                      List<BandResponse> bands) {}
}
