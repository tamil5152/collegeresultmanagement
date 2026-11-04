package com.college.result.service;

import com.college.result.common.ApiException;
import com.college.result.common.PageResponse;
import com.college.result.common.Paging;
import com.college.result.dto.MasterDtos.AcademicYearRequest;
import com.college.result.dto.MasterDtos.AcademicYearResponse;
import com.college.result.dto.MasterDtos.DepartmentRequest;
import com.college.result.dto.MasterDtos.DepartmentResponse;
import com.college.result.dto.MasterDtos.ExamSessionRequest;
import com.college.result.dto.MasterDtos.ExamSessionResponse;
import com.college.result.dto.MasterDtos.SemesterRequest;
import com.college.result.dto.MasterDtos.SemesterResponse;
import com.college.result.dto.MasterDtos.SubjectRequest;
import com.college.result.dto.MasterDtos.SubjectResponse;
import com.college.result.model.Subject;
import com.college.result.repository.AcademicYearRepository;
import com.college.result.repository.DepartmentRepository;
import com.college.result.repository.ExaminationSessionRepository;
import com.college.result.repository.SemesterRepository;
import com.college.result.repository.SubjectRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Departments, academic years, semesters, subjects and examination sessions. */
@Service
public class MasterDataService {

    private final DepartmentRepository departments;
    private final AcademicYearRepository years;
    private final SemesterRepository semesters;
    private final SubjectRepository subjects;
    private final ExaminationSessionRepository sessions;

    public MasterDataService(DepartmentRepository departments, AcademicYearRepository years,
                             SemesterRepository semesters, SubjectRepository subjects,
                             ExaminationSessionRepository sessions) {
        this.departments = departments;
        this.years = years;
        this.semesters = semesters;
        this.subjects = subjects;
        this.sessions = sessions;
    }

    // ------------------------------------------------------------------ departments
    public PageResponse<DepartmentResponse> listDepartments(String search, Paging p) {
        return PageResponse.of(departments.search(search, p), p, departments.count(search));
    }

    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest r) {
        long id = departments.insert(r.code().trim().toUpperCase(), r.name().trim());
        return departments.findById(id).orElseThrow();
    }

    @Transactional
    public DepartmentResponse updateDepartment(long id, DepartmentRequest r) {
        requireUpdated(departments.update(id, r.code().trim().toUpperCase(), r.name().trim()), "Department");
        return departments.findById(id).orElseThrow();
    }

    @Transactional
    public DepartmentResponse setDepartmentActive(long id, boolean active) {
        requireUpdated(departments.setActive(id, active), "Department");
        return departments.findById(id).orElseThrow();
    }

    // ------------------------------------------------------------------ academic years
    public PageResponse<AcademicYearResponse> listYears(String search, Paging p) {
        return PageResponse.of(years.search(search, p), p, years.count(search));
    }

    @Transactional
    public AcademicYearResponse createYear(AcademicYearRequest r) {
        requireOrdered(r.startDate(), r.endDate(), "Academic year");
        long id = years.insert(r.label().trim(), r.startDate(), r.endDate());
        return years.findById(id).orElseThrow();
    }

    @Transactional
    public AcademicYearResponse updateYear(long id, AcademicYearRequest r) {
        requireOrdered(r.startDate(), r.endDate(), "Academic year");
        requireUpdated(years.update(id, r.label().trim(), r.startDate(), r.endDate()), "Academic year");
        return years.findById(id).orElseThrow();
    }

    @Transactional
    public AcademicYearResponse setYearActive(long id, boolean active) {
        requireUpdated(years.setActive(id, active), "Academic year");
        return years.findById(id).orElseThrow();
    }

    // ------------------------------------------------------------------ semesters
    public PageResponse<SemesterResponse> listSemesters(String search, Paging p) {
        return PageResponse.of(semesters.search(search, p), p, semesters.count(search));
    }

    @Transactional
    public SemesterResponse createSemester(SemesterRequest r) {
        requireOrdered(r.startDate(), r.endDate(), "Semester");
        years.findById(r.academicYearId()).orElseThrow(() -> ApiException.badRequest("Academic year not found"));
        long id = semesters.insert(r.academicYearId(), r.semesterNumber(), r.name().trim(), r.startDate(), r.endDate());
        return semesters.findById(id).orElseThrow();
    }

    @Transactional
    public SemesterResponse updateSemester(long id, SemesterRequest r) {
        requireOrdered(r.startDate(), r.endDate(), "Semester");
        years.findById(r.academicYearId()).orElseThrow(() -> ApiException.badRequest("Academic year not found"));
        requireUpdated(semesters.update(id, r.academicYearId(), r.semesterNumber(), r.name().trim(), r.startDate(),
                r.endDate()), "Semester");
        return semesters.findById(id).orElseThrow();
    }

    // ------------------------------------------------------------------ subjects
    public PageResponse<SubjectResponse> listSubjects(String search, long departmentId, int semesterNumber, Paging p) {
        return PageResponse.of(subjects.search(search, departmentId, semesterNumber, p), p,
                subjects.count(search, departmentId, semesterNumber));
    }

    @Transactional
    public SubjectResponse createSubject(SubjectRequest r) {
        validateSubject(r);
        long id = subjects.insert(normalise(r));
        return subjects.findResponse(id).orElseThrow();
    }

    @Transactional
    public SubjectResponse updateSubject(long id, SubjectRequest r) {
        validateSubject(r);
        Subject existing = subjects.findById(id).orElseThrow(() -> ApiException.notFound("Subject not found"));
        boolean rulesChanged = existing.internalMax().compareTo(r.internalMax()) != 0
                || existing.externalMax().compareTo(r.externalMax()) != 0
                || existing.internalPass().compareTo(r.internalPass()) != 0
                || existing.externalPass().compareTo(r.externalPass()) != 0
                || existing.totalPass().compareTo(r.totalPass()) != 0
                || existing.credits().compareTo(r.credits()) != 0;
        if (rulesChanged && subjects.hasMarks(id)) {
            throw ApiException.conflict("Maximum marks, pass criteria and credits cannot be changed once marks "
                    + "exist for this subject");
        }
        subjects.update(id, normalise(r));
        return subjects.findResponse(id).orElseThrow();
    }

    @Transactional
    public SubjectResponse setSubjectActive(long id, boolean active) {
        requireUpdated(subjects.setActive(id, active), "Subject");
        return subjects.findResponse(id).orElseThrow();
    }

    private SubjectRequest normalise(SubjectRequest r) {
        return new SubjectRequest(r.departmentId(), r.code().trim().toUpperCase(), r.name().trim(), r.semesterNumber(),
                r.credits(), r.internalMax(), r.externalMax(), r.internalPass(), r.externalPass(), r.totalPass());
    }

    private void validateSubject(SubjectRequest r) {
        departments.findById(r.departmentId()).orElseThrow(() -> ApiException.badRequest("Department not found"));
        BigDecimal maxTotal = r.internalMax().add(r.externalMax());
        if (maxTotal.signum() <= 0) {
            throw ApiException.badRequest("Internal and external maximum marks cannot both be zero");
        }
        if (r.internalPass().compareTo(r.internalMax()) > 0) {
            throw ApiException.badRequest("Internal pass mark cannot exceed internal maximum marks");
        }
        if (r.externalPass().compareTo(r.externalMax()) > 0) {
            throw ApiException.badRequest("External pass mark cannot exceed external maximum marks");
        }
        if (r.totalPass().compareTo(maxTotal) > 0) {
            throw ApiException.badRequest("Total pass mark cannot exceed the total maximum marks");
        }
    }

    // ------------------------------------------------------------------ examination sessions
    public PageResponse<ExamSessionResponse> listSessions(String search, Paging p) {
        return PageResponse.of(sessions.search(search, p), p, sessions.count(search));
    }

    @Transactional
    public ExamSessionResponse createSession(ExamSessionRequest r) {
        validateSession(r);
        long id = sessions.insert(normalise(r));
        return sessions.findResponse(id).orElseThrow();
    }

    @Transactional
    public ExamSessionResponse updateSession(long id, ExamSessionRequest r) {
        validateSession(r);
        var existing = sessions.findById(id).orElseThrow(() -> ApiException.notFound("Examination session not found"));
        if (existing.semesterId() != r.semesterId() && sessions.hasMarks(id)) {
            throw ApiException.conflict("The semester cannot be changed once marks exist for this session");
        }
        sessions.update(id, normalise(r));
        return sessions.findResponse(id).orElseThrow();
    }

    private ExamSessionRequest normalise(ExamSessionRequest r) {
        return new ExamSessionRequest(r.name().trim(), r.semesterId(), r.startDate(), r.endDate(),
                r.status().trim().toUpperCase());
    }

    private void validateSession(ExamSessionRequest r) {
        String status = r.status().trim().toUpperCase();
        if (!status.equals("OPEN") && !status.equals("CLOSED")) {
            throw ApiException.badRequest("Status must be OPEN or CLOSED");
        }
        if (r.endDate().isBefore(r.startDate())) {
            throw ApiException.badRequest("End date cannot be before start date");
        }
        semesters.findById(r.semesterId()).orElseThrow(() -> ApiException.badRequest("Semester not found"));
    }

    // ------------------------------------------------------------------ helpers
    private static void requireUpdated(int rows, String what) {
        if (rows == 0) {
            throw ApiException.notFound(what + " not found");
        }
    }

    private static void requireOrdered(java.time.LocalDate start, java.time.LocalDate end, String what) {
        if (!end.isAfter(start)) {
            throw ApiException.badRequest(what + " end date must be after the start date");
        }
    }
}
