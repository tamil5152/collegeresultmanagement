package com.college.result.controller;

import com.college.result.common.ApiResponse;
import com.college.result.common.PageResponse;
import com.college.result.common.Paging;
import com.college.result.dto.AssignmentDtos.*;
import com.college.result.security.AuthUser;
import com.college.result.service.EnrollmentAssignmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin - assignments and enrollments")
public class EnrollmentAssignmentController {

    private final EnrollmentAssignmentService service;

    public EnrollmentAssignmentController(EnrollmentAssignmentService service) {
        this.service = service;
    }

    @GetMapping("/assignments")
    public ApiResponse<PageResponse<AssignmentResponse>> assignments(
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") long semesterId,
            @RequestParam(defaultValue = "0") long facultyId, @RequestParam(defaultValue = "0") long departmentId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(service.listAssignments(search, semesterId, facultyId, departmentId,
                Paging.of(page, size)));
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssignmentResponse> createAssignment(@AuthenticationPrincipal AuthUser actor,
                                                            @Valid @RequestBody AssignmentRequest r) {
        return ApiResponse.ok("Faculty assigned", service.createAssignment(actor, r));
    }

    @PutMapping("/assignments/{id}")
    public ApiResponse<AssignmentResponse> changeFaculty(@AuthenticationPrincipal AuthUser actor,
                                                         @PathVariable long id,
                                                         @Valid @RequestBody AssignmentUpdateRequest r) {
        return ApiResponse.ok("Assignment updated", service.changeFaculty(actor, id, r));
    }

    @DeleteMapping("/assignments/{id}")
    public ApiResponse<Void> deleteAssignment(@AuthenticationPrincipal AuthUser actor, @PathVariable long id) {
        service.deleteAssignment(actor, id);
        return ApiResponse.ok("Assignment removed", null);
    }

    @GetMapping("/enrollments")
    public ApiResponse<PageResponse<EnrollmentResponse>> enrollments(
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") long semesterId,
            @RequestParam(defaultValue = "0") long departmentId, @RequestParam(defaultValue = "0") long subjectId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(service.listEnrollments(search, semesterId, departmentId, subjectId,
                Paging.of(page, size)));
    }

    @PostMapping("/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EnrollmentResponse> enroll(@AuthenticationPrincipal AuthUser actor,
                                                  @Valid @RequestBody EnrollmentRequest r) {
        return ApiResponse.ok("Student enrolled", service.enroll(actor, r));
    }

    @PostMapping("/enrollments/bulk")
    public ApiResponse<BulkEnrollResult> bulkEnroll(@AuthenticationPrincipal AuthUser actor,
                                                    @Valid @RequestBody BulkEnrollRequest r) {
        return ApiResponse.ok("Enrollment completed", service.bulkEnroll(actor, r));
    }

    @DeleteMapping("/enrollments/{id}")
    public ApiResponse<Void> unenroll(@AuthenticationPrincipal AuthUser actor, @PathVariable long id) {
        service.unenroll(actor, id);
        return ApiResponse.ok("Enrollment removed", null);
    }
}
