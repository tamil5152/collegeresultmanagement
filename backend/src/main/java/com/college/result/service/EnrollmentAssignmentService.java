package com.college.result.service;

import com.college.result.common.ApiException;
import com.college.result.common.PageResponse;
import com.college.result.common.Paging;
import com.college.result.dto.AssignmentDtos.*;
import com.college.result.dto.MasterDtos.SemesterResponse;
import com.college.result.dto.UserDtos.FacultyResponse;
import com.college.result.dto.UserDtos.StudentResponse;
import com.college.result.model.Assignment;
import com.college.result.model.Subject;
import com.college.result.repository.AssignmentRepository;
import com.college.result.repository.EnrollmentRepository;
import com.college.result.repository.FacultyRepository;
import com.college.result.repository.SemesterRepository;
import com.college.result.repository.StudentRepository;
import com.college.result.repository.SubjectRepository;
import com.college.result.security.AuthUser;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Faculty-to-subject assignments and student enrollments. */
@Service
public class EnrollmentAssignmentService {

    private final AssignmentRepository assignments;
    private final EnrollmentRepository enrollments;
    private final SubjectRepository subjects;
    private final FacultyRepository faculty;
    private final StudentRepository students;
    private final SemesterRepository semesters;
    private final AuditService audit;

    public EnrollmentAssignmentService(AssignmentRepository assignments, EnrollmentRepository enrollments,
                                       SubjectRepository subjects, FacultyRepository faculty,
                                       StudentRepository students, SemesterRepository semesters, AuditService audit) {
        this.assignments = assignments;
        this.enrollments = enrollments;
        this.subjects = subjects;
        this.faculty = faculty;
        this.students = students;
        this.semesters = semesters;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ assignments
    public PageResponse<AssignmentResponse> listAssignments(String search, long semesterId, long facultyId,
                                                            long departmentId, Paging p) {
        return PageResponse.of(assignments.search(search, semesterId, facultyId, departmentId, p), p,
                assignments.count(search, semesterId, facultyId, departmentId));
    }

    @Transactional
    public AssignmentResponse createAssignment(AuthUser actor, AssignmentRequest r) {
        Subject subject = subjects.findById(r.subjectId())
                .orElseThrow(() -> ApiException.badRequest("Subject not found"));
        SemesterResponse semester = semesters.findById(r.semesterId())
                .orElseThrow(() -> ApiException.badRequest("Semester not found"));
        requireActiveFaculty(r.facultyId());
        if (!subject.active()) {
            throw ApiException.badRequest("Subject is inactive");
        }
        if (subject.semesterNumber() != semester.semesterNumber()) {
            throw ApiException.badRequest("Subject " + subject.code() + " belongs to semester "
                    + subject.semesterNumber() + " but the selected semester is number " + semester.semesterNumber());
        }
        long id = assignments.insert(r.subjectId(), r.facultyId(), r.semesterId());
        audit.log(actor, "ASSIGNMENT_CREATED", "SUBJECT_ASSIGNMENT", id,
                Map.of("subjectId", r.subjectId(), "facultyId", r.facultyId(), "semesterId", r.semesterId()));
        return assignments.findResponse(id).orElseThrow();
    }

    @Transactional
    public AssignmentResponse changeFaculty(AuthUser actor, long id, AssignmentUpdateRequest r) {
        assignments.findById(id).orElseThrow(() -> ApiException.notFound("Assignment not found"));
        requireActiveFaculty(r.facultyId());
        assignments.updateFaculty(id, r.facultyId());
        audit.log(actor, "ASSIGNMENT_UPDATED", "SUBJECT_ASSIGNMENT", id, Map.of("facultyId", r.facultyId()));
        return assignments.findResponse(id).orElseThrow();
    }

    @Transactional
    public void deleteAssignment(AuthUser actor, long id) {
        Assignment a = assignments.findById(id).orElseThrow(() -> ApiException.notFound("Assignment not found"));
        if (assignments.hasMarks(a.subjectId(), a.semesterId())) {
            throw ApiException.conflict("Marks already exist for this subject and semester; the assignment cannot "
                    + "be removed (reassign it to another faculty member instead)");
        }
        assignments.delete(id);
        audit.log(actor, "ASSIGNMENT_DELETED", "SUBJECT_ASSIGNMENT", id, null);
    }

    private void requireActiveFaculty(long facultyId) {
        FacultyResponse f = faculty.findById(facultyId)
                .orElseThrow(() -> ApiException.badRequest("Faculty member not found"));
        if (!f.active()) {
            throw ApiException.badRequest("Faculty member is inactive");
        }
    }

    // ------------------------------------------------------------------ enrollments
    public PageResponse<EnrollmentResponse> listEnrollments(String search, long semesterId, long departmentId,
                                                            long subjectId, Paging p) {
        return PageResponse.of(enrollments.search(search, semesterId, departmentId, subjectId, p), p,
                enrollments.count(search, semesterId, departmentId, subjectId));
    }

    @Transactional
    public BulkEnrollResult bulkEnroll(AuthUser actor, BulkEnrollRequest r) {
        SemesterResponse semester = semesters.findById(r.semesterId())
                .orElseThrow(() -> ApiException.badRequest("Semester not found"));
        int added = enrollments.bulkEnroll(r.semesterId(), r.departmentId(), semester.semesterNumber());
        audit.log(actor, "ENROLLMENT_BULK", "SEMESTER", r.semesterId(),
                Map.of("departmentId", r.departmentId(), "newEnrollments", added));
        return new BulkEnrollResult(added);
    }

    @Transactional
    public EnrollmentResponse enroll(AuthUser actor, EnrollmentRequest r) {
        StudentResponse student = students.findById(r.studentId())
                .orElseThrow(() -> ApiException.badRequest("Student not found"));
        Subject subject = subjects.findById(r.subjectId())
                .orElseThrow(() -> ApiException.badRequest("Subject not found"));
        SemesterResponse semester = semesters.findById(r.semesterId())
                .orElseThrow(() -> ApiException.badRequest("Semester not found"));
        if (!student.active() || !subject.active()) {
            throw ApiException.badRequest("Student and subject must both be active");
        }
        if (subject.semesterNumber() != semester.semesterNumber()) {
            throw ApiException.badRequest("Subject " + subject.code() + " does not belong to the selected semester");
        }
        long id = enrollments.insert(r.studentId(), r.subjectId(), r.semesterId());
        audit.log(actor, "ENROLLMENT_CREATED", "ENROLLMENT", id, Map.of("studentId", r.studentId(),
                "subjectId", r.subjectId(), "semesterId", r.semesterId()));
        return enrollments.findResponse(id).orElseThrow();
    }

    @Transactional
    public void unenroll(AuthUser actor, long id) {
        enrollments.findResponse(id).orElseThrow(() -> ApiException.notFound("Enrollment not found"));
        if (enrollments.hasMarks(id)) {
            throw ApiException.conflict("Marks already exist for this enrollment; it cannot be removed");
        }
        enrollments.delete(id);
        audit.log(actor, "ENROLLMENT_DELETED", "ENROLLMENT", id, null);
    }
}
