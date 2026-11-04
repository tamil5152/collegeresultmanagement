package com.college.result.controller;

import com.college.result.common.ApiResponse;
import com.college.result.common.PageResponse;
import com.college.result.common.Paging;
import com.college.result.dto.MasterDtos.*;
import com.college.result.repository.LookupRepository;
import com.college.result.security.AuthUser;
import com.college.result.service.GradingRuleService;
import com.college.result.service.MasterDataService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Admin CRUD for departments, academic years, semesters, subjects, examination sessions and grading rules. */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin - master data")
public class MasterDataController {

    private final MasterDataService service;
    private final GradingRuleService gradingRules;
    private final LookupRepository lookups;

    public MasterDataController(MasterDataService service, GradingRuleService gradingRules, LookupRepository lookups) {
        this.service = service;
        this.gradingRules = gradingRules;
        this.lookups = lookups;
    }

    // ---- departments
    @GetMapping("/departments")
    public ApiResponse<PageResponse<DepartmentResponse>> departments(
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(service.listDepartments(search, Paging.of(page, size)));
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DepartmentResponse> createDepartment(@Valid @RequestBody DepartmentRequest r) {
        return ApiResponse.ok("Department created", service.createDepartment(r));
    }

    @PutMapping("/departments/{id}")
    public ApiResponse<DepartmentResponse> updateDepartment(@PathVariable long id,
                                                            @Valid @RequestBody DepartmentRequest r) {
        return ApiResponse.ok("Department updated", service.updateDepartment(id, r));
    }

    @PatchMapping("/departments/{id}/active")
    public ApiResponse<DepartmentResponse> departmentActive(@PathVariable long id,
                                                            @Valid @RequestBody ActiveRequest r) {
        return ApiResponse.ok(service.setDepartmentActive(id, r.active()));
    }

    // ---- academic years
    @GetMapping("/academic-years")
    public ApiResponse<PageResponse<AcademicYearResponse>> academicYears(
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(service.listYears(search, Paging.of(page, size)));
    }

    @PostMapping("/academic-years")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AcademicYearResponse> createYear(@Valid @RequestBody AcademicYearRequest r) {
        return ApiResponse.ok("Academic year created", service.createYear(r));
    }

    @PutMapping("/academic-years/{id}")
    public ApiResponse<AcademicYearResponse> updateYear(@PathVariable long id,
                                                        @Valid @RequestBody AcademicYearRequest r) {
        return ApiResponse.ok("Academic year updated", service.updateYear(id, r));
    }

    @PatchMapping("/academic-years/{id}/active")
    public ApiResponse<AcademicYearResponse> yearActive(@PathVariable long id, @Valid @RequestBody ActiveRequest r) {
        return ApiResponse.ok(service.setYearActive(id, r.active()));
    }

    // ---- semesters
    @GetMapping("/semesters")
    public ApiResponse<PageResponse<SemesterResponse>> semesters(
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(service.listSemesters(search, Paging.of(page, size)));
    }

    @PostMapping("/semesters")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SemesterResponse> createSemester(@Valid @RequestBody SemesterRequest r) {
        return ApiResponse.ok("Semester created", service.createSemester(r));
    }

    @PutMapping("/semesters/{id}")
    public ApiResponse<SemesterResponse> updateSemester(@PathVariable long id, @Valid @RequestBody SemesterRequest r) {
        return ApiResponse.ok("Semester updated", service.updateSemester(id, r));
    }

    // ---- subjects
    @GetMapping("/subjects")
    public ApiResponse<PageResponse<SubjectResponse>> subjects(
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") long departmentId,
            @RequestParam(defaultValue = "0") int semesterNumber, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(service.listSubjects(search, departmentId, semesterNumber, Paging.of(page, size)));
    }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SubjectResponse> createSubject(@Valid @RequestBody SubjectRequest r) {
        return ApiResponse.ok("Subject created", service.createSubject(r));
    }

    @PutMapping("/subjects/{id}")
    public ApiResponse<SubjectResponse> updateSubject(@PathVariable long id, @Valid @RequestBody SubjectRequest r) {
        return ApiResponse.ok("Subject updated", service.updateSubject(id, r));
    }

    @PatchMapping("/subjects/{id}/active")
    public ApiResponse<SubjectResponse> subjectActive(@PathVariable long id, @Valid @RequestBody ActiveRequest r) {
        return ApiResponse.ok(service.setSubjectActive(id, r.active()));
    }

    // ---- examination sessions
    @GetMapping("/examination-sessions")
    public ApiResponse<PageResponse<ExamSessionResponse>> sessions(
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(service.listSessions(search, Paging.of(page, size)));
    }

    @PostMapping("/examination-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExamSessionResponse> createSession(@Valid @RequestBody ExamSessionRequest r) {
        return ApiResponse.ok("Examination session created", service.createSession(r));
    }

    @PutMapping("/examination-sessions/{id}")
    public ApiResponse<ExamSessionResponse> updateSession(@PathVariable long id,
                                                          @Valid @RequestBody ExamSessionRequest r) {
        return ApiResponse.ok("Examination session updated", service.updateSession(id, r));
    }

    // ---- grading rules
    @GetMapping("/grading-rules")
    public ApiResponse<List<GradingRuleResponse>> gradingRules() {
        return ApiResponse.ok(gradingRules.list());
    }

    @PostMapping("/grading-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GradingRuleResponse> createRule(@AuthenticationPrincipal AuthUser actor,
                                                       @Valid @RequestBody GradingRuleRequest r) {
        return ApiResponse.ok("Grading rule created", gradingRules.create(actor, r));
    }

    @PutMapping("/grading-rules/{id}")
    public ApiResponse<GradingRuleResponse> updateRule(@AuthenticationPrincipal AuthUser actor, @PathVariable long id,
                                                       @Valid @RequestBody GradingRuleRequest r) {
        return ApiResponse.ok("Grading rule updated", gradingRules.update(actor, id, r));
    }

    @PostMapping("/grading-rules/{id}/activate")
    public ApiResponse<GradingRuleResponse> activateRule(@AuthenticationPrincipal AuthUser actor,
                                                         @PathVariable long id) {
        return ApiResponse.ok("Grading rule activated", gradingRules.activate(actor, id));
    }

    // ---- drop-down options
    @GetMapping("/options/departments")
    public ApiResponse<List<Option>> optDepartments() { return ApiResponse.ok(lookups.departments()); }

    @GetMapping("/options/academic-years")
    public ApiResponse<List<Option>> optYears() { return ApiResponse.ok(lookups.academicYears()); }

    @GetMapping("/options/semesters")
    public ApiResponse<List<Option>> optSemesters() { return ApiResponse.ok(lookups.semesters()); }

    @GetMapping("/options/subjects")
    public ApiResponse<List<Option>> optSubjects(@RequestParam(defaultValue = "0") long departmentId,
                                                 @RequestParam(defaultValue = "0") int semesterNumber) {
        return ApiResponse.ok(lookups.subjects(departmentId, semesterNumber));
    }

    @GetMapping("/options/faculty")
    public ApiResponse<List<Option>> optFaculty(@RequestParam(defaultValue = "0") long departmentId) {
        return ApiResponse.ok(lookups.faculty(departmentId));
    }

    @GetMapping("/options/students")
    public ApiResponse<List<Option>> optStudents(@RequestParam(defaultValue = "0") long departmentId) {
        return ApiResponse.ok(lookups.students(departmentId));
    }

    @GetMapping("/options/examination-sessions")
    public ApiResponse<List<Option>> optSessions() { return ApiResponse.ok(lookups.examSessions()); }
}
