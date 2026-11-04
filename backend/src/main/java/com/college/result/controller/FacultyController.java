package com.college.result.controller;

import com.college.result.common.ApiResponse;
import com.college.result.dto.MarksDtos.*;
import com.college.result.security.AuthUser;
import com.college.result.service.FacultyMarksService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Faculty endpoints. Ownership of every assignment is verified in the service layer. */
@RestController
@RequestMapping("/api/faculty")
@Tag(name = "Faculty")
public class FacultyController {

    private final FacultyMarksService service;

    public FacultyController(FacultyMarksService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public ApiResponse<FacultyDashboard> dashboard(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.dashboard(user));
    }

    @GetMapping("/assignments")
    public ApiResponse<List<FacultyAssignment>> assignments(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.myAssignments(user));
    }

    @GetMapping("/assignments/{assignmentId}/students")
    public ApiResponse<List<EnrolledStudentResponse>> students(@AuthenticationPrincipal AuthUser user,
                                                               @PathVariable long assignmentId) {
        return ApiResponse.ok(service.students(user, assignmentId));
    }

    @GetMapping("/assignments/{assignmentId}/sessions/{sessionId}/marks")
    public ApiResponse<MarkSheet> sheet(@AuthenticationPrincipal AuthUser user, @PathVariable long assignmentId,
                                        @PathVariable long sessionId) {
        return ApiResponse.ok(service.sheet(user, assignmentId, sessionId));
    }

    @PutMapping("/assignments/{assignmentId}/sessions/{sessionId}/marks")
    public ApiResponse<MarkSheet> saveDraft(@AuthenticationPrincipal AuthUser user, @PathVariable long assignmentId,
                                            @PathVariable long sessionId, @Valid @RequestBody SaveMarksRequest r) {
        return ApiResponse.ok("Draft saved", service.saveDraft(user, assignmentId, sessionId, r));
    }

    @PostMapping("/assignments/{assignmentId}/sessions/{sessionId}/submit")
    public ApiResponse<SubmitResult> submit(@AuthenticationPrincipal AuthUser user, @PathVariable long assignmentId,
                                            @PathVariable long sessionId) {
        SubmitResult result = service.submit(user, assignmentId, sessionId);
        return ApiResponse.ok(result.submitted() + " mark(s) submitted for approval", result);
    }

    @GetMapping("/submissions")
    public ApiResponse<List<SubmissionSummary>> submissions(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.submissions(user));
    }
}
