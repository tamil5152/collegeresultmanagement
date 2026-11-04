package com.college.result.controller;

import com.college.result.common.ApiException;
import com.college.result.common.ApiResponse;
import com.college.result.common.PageResponse;
import com.college.result.common.Paging;
import com.college.result.dto.AuditDtos.AuditLogResponse;
import com.college.result.dto.MarksDtos.CorrectionRequest;
import com.college.result.dto.MarksDtos.MarkReviewRow;
import com.college.result.dto.MarksDtos.ReviewRequest;
import com.college.result.dto.MarksDtos.ReviewResult;
import com.college.result.dto.ReportDtos.*;
import com.college.result.dto.ResultDtos.AdminResultRow;
import com.college.result.dto.ResultDtos.PublicationRequest;
import com.college.result.dto.ResultDtos.PublicationStatus;
import com.college.result.dto.ResultDtos.PublishResult;
import com.college.result.repository.AuditRepository;
import com.college.result.repository.ReportRepository;
import com.college.result.repository.ResultRepository;
import com.college.result.security.AuthUser;
import com.college.result.service.AdminMarksService;
import com.college.result.service.ResultPublicationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Admin: marks review, corrections, result publication, reports, dashboard and audit log. */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin - marks, results and reports")
public class AdminOperationsController {

    private final AdminMarksService marksService;
    private final ResultPublicationService publication;
    private final ResultRepository results;
    private final ReportRepository reports;
    private final AuditRepository audit;

    public AdminOperationsController(AdminMarksService marksService, ResultPublicationService publication,
                                     ResultRepository results, ReportRepository reports, AuditRepository audit) {
        this.marksService = marksService;
        this.publication = publication;
        this.results = results;
        this.reports = reports;
        this.audit = audit;
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboard> dashboard() {
        return ApiResponse.ok(reports.dashboard());
    }

    // ---- marks review
    @GetMapping("/marks")
    public ApiResponse<PageResponse<MarkReviewRow>> marks(
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") long sessionId,
            @RequestParam(defaultValue = "0") long departmentId, @RequestParam(defaultValue = "0") long subjectId,
            @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(marksService.list(search, sessionId, departmentId, subjectId, status,
                Paging.of(page, size)));
    }

    @PostMapping("/marks/review")
    public ApiResponse<ReviewResult> review(@AuthenticationPrincipal AuthUser admin,
                                            @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok("Marks reviewed", marksService.review(admin, request));
    }

    @PostMapping("/marks/{id}/correct")
    public ApiResponse<Void> correct(@AuthenticationPrincipal AuthUser admin, @PathVariable long id,
                                     @Valid @RequestBody CorrectionRequest request) {
        marksService.correct(admin, id, request);
        return ApiResponse.ok("Correction recorded", null);
    }

    // ---- results publication
    @GetMapping("/results/publication-status")
    public ApiResponse<PublicationStatus> publicationStatus(@RequestParam long sessionId,
                                                            @RequestParam long departmentId) {
        return ApiResponse.ok(publication.status(sessionId, departmentId));
    }

    @PostMapping("/results/publish")
    public ApiResponse<PublishResult> publish(@AuthenticationPrincipal AuthUser admin,
                                              @Valid @RequestBody PublicationRequest request) {
        PublishResult result = publication.publish(admin, request);
        return ApiResponse.ok(result.published() + " result(s) published", result);
    }

    @GetMapping("/results")
    public ApiResponse<PageResponse<AdminResultRow>> publishedResults(
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") long sessionId,
            @RequestParam(defaultValue = "0") long departmentId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Paging p = Paging.of(page, size);
        return ApiResponse.ok(PageResponse.of(results.searchAdmin(search, sessionId, departmentId, p), p,
                results.countAdmin(search, sessionId, departmentId)));
    }

    // ---- reports
    @GetMapping("/reports/department-summary")
    public ApiResponse<List<DepartmentSummaryRow>> departmentSummary(@RequestParam long sessionId) {
        return ApiResponse.ok(reports.departmentSummary(requireId(sessionId)));
    }

    @GetMapping("/reports/subject-analysis")
    public ApiResponse<List<SubjectAnalysisRow>> subjectAnalysis(@RequestParam long sessionId,
                                                                 @RequestParam(defaultValue = "0") long departmentId) {
        return ApiResponse.ok(reports.subjectAnalysis(requireId(sessionId), departmentId));
    }

    @GetMapping("/reports/grade-distribution")
    public ApiResponse<List<GradeDistributionRow>> gradeDistribution(
            @RequestParam long sessionId, @RequestParam(defaultValue = "0") long departmentId) {
        return ApiResponse.ok(reports.gradeDistribution(requireId(sessionId), departmentId));
    }

    @GetMapping("/reports/marks-status")
    public ApiResponse<List<MarksStatusRow>> marksStatus(@RequestParam long sessionId) {
        return ApiResponse.ok(reports.marksStatus(requireId(sessionId)));
    }

    // ---- audit log
    @GetMapping("/audit-logs")
    public ApiResponse<PageResponse<AuditLogResponse>> auditLogs(
            @RequestParam(defaultValue = "") String action, @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Paging p = Paging.of(page, size);
        return ApiResponse.ok(PageResponse.of(audit.search(action, search, p), p, audit.count(action, search)));
    }

    private static long requireId(long id) {
        if (id <= 0) {
            throw ApiException.badRequest("sessionId is required");
        }
        return id;
    }
}
