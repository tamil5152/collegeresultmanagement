package com.college.result.controller;

import com.college.result.common.ApiResponse;
import com.college.result.dto.ResultDtos.ResultDetail;
import com.college.result.dto.ResultDtos.StudentDashboard;
import com.college.result.dto.ResultDtos.StudentResultSummary;
import com.college.result.dto.UserDtos.StudentResponse;
import com.college.result.security.AuthUser;
import com.college.result.service.MarksheetPdfService;
import com.college.result.service.StudentResultService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Student endpoints. No student id is ever accepted from the client: identity comes from the token. */
@RestController
@RequestMapping("/api/student")
@Tag(name = "Student")
public class StudentController {

    private final StudentResultService service;
    private final MarksheetPdfService pdf;

    public StudentController(StudentResultService service, MarksheetPdfService pdf) {
        this.service = service;
        this.pdf = pdf;
    }

    @GetMapping("/dashboard")
    public ApiResponse<StudentDashboard> dashboard(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.dashboard(user));
    }

    @GetMapping("/profile")
    public ApiResponse<StudentResponse> profile(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.profile(user));
    }

    @GetMapping("/results")
    public ApiResponse<List<StudentResultSummary>> results(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.list(user));
    }

    @GetMapping("/results/{resultId}")
    public ApiResponse<ResultDetail> result(@AuthenticationPrincipal AuthUser user, @PathVariable long resultId) {
        return ApiResponse.ok(service.detail(user, resultId));
    }

    @GetMapping("/results/{resultId}/marksheet")
    public ResponseEntity<byte[]> marksheet(@AuthenticationPrincipal AuthUser user, @PathVariable long resultId) {
        ResultDetail detail = service.detail(user, resultId);
        String filename = "marksheet-" + detail.rollNumber().replaceAll("[^A-Za-z0-9._-]", "_") + "-sem"
                + detail.semesterNumber() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf.render(detail));
    }
}
