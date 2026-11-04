package com.college.result.service;

import com.college.result.common.ApiException;
import com.college.result.common.PageResponse;
import com.college.result.common.Paging;
import com.college.result.dto.UserDtos.*;
import com.college.result.model.Role;
import com.college.result.repository.DepartmentRepository;
import com.college.result.repository.FacultyRepository;
import com.college.result.repository.StudentRepository;
import com.college.result.repository.UserRepository;
import com.college.result.security.AuthUser;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin management of student and faculty accounts (user row + profile row are always written together). */
@Service
public class UserManagementService {

    private final UserRepository users;
    private final StudentRepository students;
    private final FacultyRepository faculty;
    private final DepartmentRepository departments;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserManagementService(UserRepository users, StudentRepository students, FacultyRepository faculty,
                                 DepartmentRepository departments, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.students = students;
        this.faculty = faculty;
        this.departments = departments;
        this.encoder = encoder;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ students
    public PageResponse<StudentResponse> listStudents(String search, long departmentId, int semester, int active,
                                                      Paging p) {
        return PageResponse.of(students.search(search, departmentId, semester, active, p), p,
                students.count(search, departmentId, semester, active));
    }

    public StudentResponse getStudent(long id) {
        return students.findById(id).orElseThrow(() -> ApiException.notFound("Student not found"));
    }

    @Transactional
    public StudentResponse createStudent(AuthUser actor, StudentCreateRequest r) {
        requireDepartment(r.departmentId());
        String username = r.username().trim();
        String roll = r.rollNumber().trim();
        if (users.usernameTaken(username)) {
            throw ApiException.conflict("Username '" + username + "' is already taken");
        }
        if (users.emailTaken(r.email().trim(), 0)) {
            throw ApiException.conflict("Email is already in use");
        }
        if (students.rollTaken(roll, 0)) {
            throw ApiException.conflict("Roll number '" + roll + "' already exists");
        }
        long userId = users.insert(username, r.email().trim(), encoder.encode(r.password()), r.fullName().trim(),
                Role.STUDENT);
        long id = students.insert(userId, r.departmentId(), roll, r.batchYear(), r.currentSemester(),
                blankToNull(r.phone()), r.dateOfBirth());
        audit.log(actor, "STUDENT_CREATED", "STUDENT", id, Map.of("username", username, "rollNumber", roll));
        return getStudent(id);
    }

    @Transactional
    public StudentResponse updateStudent(AuthUser actor, long id, StudentUpdateRequest r) {
        StudentResponse existing = getStudent(id);
        requireDepartment(r.departmentId());
        String roll = r.rollNumber().trim();
        if (users.emailTaken(r.email().trim(), existing.userId())) {
            throw ApiException.conflict("Email is already in use");
        }
        if (students.rollTaken(roll, id)) {
            throw ApiException.conflict("Roll number '" + roll + "' already exists");
        }
        users.updateProfile(existing.userId(), r.fullName().trim(), r.email().trim());
        students.update(id, new StudentUpdateRequest(null, r.email(), r.fullName(), r.departmentId(), roll,
                r.batchYear(), r.currentSemester(), blankToNull(r.phone()), r.dateOfBirth()), roll);
        resetPasswordIfGiven(actor, existing.userId(), r.password());
        audit.log(actor, "STUDENT_UPDATED", "STUDENT", id, Map.of("rollNumber", roll));
        return getStudent(id);
    }

    @Transactional
    public StudentResponse setStudentActive(AuthUser actor, long id, boolean active) {
        StudentResponse existing = getStudent(id);
        users.setActive(existing.userId(), active);
        audit.log(actor, active ? "STUDENT_ACTIVATED" : "STUDENT_DEACTIVATED", "STUDENT", id, null);
        return getStudent(id);
    }

    // ------------------------------------------------------------------ faculty
    public PageResponse<FacultyResponse> listFaculty(String search, long departmentId, int active, Paging p) {
        return PageResponse.of(faculty.search(search, departmentId, active, p), p,
                faculty.count(search, departmentId, active));
    }

    public FacultyResponse getFaculty(long id) {
        return faculty.findById(id).orElseThrow(() -> ApiException.notFound("Faculty member not found"));
    }

    @Transactional
    public FacultyResponse createFaculty(AuthUser actor, FacultyCreateRequest r) {
        requireDepartment(r.departmentId());
        String username = r.username().trim();
        String code = r.employeeCode().trim();
        if (users.usernameTaken(username)) {
            throw ApiException.conflict("Username '" + username + "' is already taken");
        }
        if (users.emailTaken(r.email().trim(), 0)) {
            throw ApiException.conflict("Email is already in use");
        }
        if (faculty.codeTaken(code, 0)) {
            throw ApiException.conflict("Employee code '" + code + "' already exists");
        }
        long userId = users.insert(username, r.email().trim(), encoder.encode(r.password()), r.fullName().trim(),
                Role.FACULTY);
        long id = faculty.insert(userId, r.departmentId(), code, blankToNull(r.designation()), blankToNull(r.phone()));
        audit.log(actor, "FACULTY_CREATED", "FACULTY", id, Map.of("username", username, "employeeCode", code));
        return getFaculty(id);
    }

    @Transactional
    public FacultyResponse updateFaculty(AuthUser actor, long id, FacultyUpdateRequest r) {
        FacultyResponse existing = getFaculty(id);
        requireDepartment(r.departmentId());
        String code = r.employeeCode().trim();
        if (users.emailTaken(r.email().trim(), existing.userId())) {
            throw ApiException.conflict("Email is already in use");
        }
        if (faculty.codeTaken(code, id)) {
            throw ApiException.conflict("Employee code '" + code + "' already exists");
        }
        users.updateProfile(existing.userId(), r.fullName().trim(), r.email().trim());
        faculty.update(id, new FacultyUpdateRequest(null, r.email(), r.fullName(), r.departmentId(), code,
                blankToNull(r.designation()), blankToNull(r.phone())), code);
        resetPasswordIfGiven(actor, existing.userId(), r.password());
        audit.log(actor, "FACULTY_UPDATED", "FACULTY", id, Map.of("employeeCode", code));
        return getFaculty(id);
    }

    @Transactional
    public FacultyResponse setFacultyActive(AuthUser actor, long id, boolean active) {
        FacultyResponse existing = getFaculty(id);
        users.setActive(existing.userId(), active);
        audit.log(actor, active ? "FACULTY_ACTIVATED" : "FACULTY_DEACTIVATED", "FACULTY", id, null);
        return getFaculty(id);
    }

    // ------------------------------------------------------------------ helpers
    private void resetPasswordIfGiven(AuthUser actor, long userId, String password) {
        if (password != null && !password.isBlank()) {
            users.updatePassword(userId, encoder.encode(password));
            audit.log(actor, "PASSWORD_RESET", "USER", userId, null);
        }
    }

    private void requireDepartment(long departmentId) {
        departments.findById(departmentId).orElseThrow(() -> ApiException.badRequest("Department not found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
