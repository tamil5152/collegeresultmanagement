package com.college.result.controller;

import com.college.result.common.ApiResponse;
import com.college.result.common.PageResponse;
import com.college.result.common.Paging;
import com.college.result.dto.MasterDtos.ActiveRequest;
import com.college.result.dto.UserDtos.*;
import com.college.result.security.AuthUser;
import com.college.result.service.UserManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin - students and faculty")
public class UserManagementController {

    private final UserManagementService service;

    public UserManagementController(UserManagementService service) {
        this.service = service;
    }

    /** active: -1 any (default), 1 active only, 0 inactive only. */
    @GetMapping("/students")
    public ApiResponse<PageResponse<StudentResponse>> students(
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") long departmentId,
            @RequestParam(defaultValue = "0") int semester, @RequestParam(defaultValue = "-1") int active,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(service.listStudents(search, departmentId, semester, active, Paging.of(page, size)));
    }

    @GetMapping("/students/{id}")
    public ApiResponse<StudentResponse> student(@PathVariable long id) {
        return ApiResponse.ok(service.getStudent(id));
    }

    @PostMapping("/students")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StudentResponse> createStudent(@AuthenticationPrincipal AuthUser actor,
                                                      @Valid @RequestBody StudentCreateRequest r) {
        return ApiResponse.ok("Student created", service.createStudent(actor, r));
    }

    @PutMapping("/students/{id}")
    public ApiResponse<StudentResponse> updateStudent(@AuthenticationPrincipal AuthUser actor, @PathVariable long id,
                                                      @Valid @RequestBody StudentUpdateRequest r) {
        return ApiResponse.ok("Student updated", service.updateStudent(actor, id, r));
    }

    @PatchMapping("/students/{id}/active")
    public ApiResponse<StudentResponse> studentActive(@AuthenticationPrincipal AuthUser actor, @PathVariable long id,
                                                      @Valid @RequestBody ActiveRequest r) {
        return ApiResponse.ok(service.setStudentActive(actor, id, r.active()));
    }

    @GetMapping("/faculty")
    public ApiResponse<PageResponse<FacultyResponse>> faculty(
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") long departmentId,
            @RequestParam(defaultValue = "-1") int active, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(service.listFaculty(search, departmentId, active, Paging.of(page, size)));
    }

    @GetMapping("/faculty/{id}")
    public ApiResponse<FacultyResponse> facultyMember(@PathVariable long id) {
        return ApiResponse.ok(service.getFaculty(id));
    }

    @PostMapping("/faculty")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FacultyResponse> createFaculty(@AuthenticationPrincipal AuthUser actor,
                                                      @Valid @RequestBody FacultyCreateRequest r) {
        return ApiResponse.ok("Faculty created", service.createFaculty(actor, r));
    }

    @PutMapping("/faculty/{id}")
    public ApiResponse<FacultyResponse> updateFaculty(@AuthenticationPrincipal AuthUser actor, @PathVariable long id,
                                                      @Valid @RequestBody FacultyUpdateRequest r) {
        return ApiResponse.ok("Faculty updated", service.updateFaculty(actor, id, r));
    }

    @PatchMapping("/faculty/{id}/active")
    public ApiResponse<FacultyResponse> facultyActive(@AuthenticationPrincipal AuthUser actor, @PathVariable long id,
                                                      @Valid @RequestBody ActiveRequest r) {
        return ApiResponse.ok(service.setFacultyActive(actor, id, r.active()));
    }
}
