package com.college.result.service;

import com.college.result.common.ApiException;
import com.college.result.dto.AssignmentDtos.AssignmentResponse;
import com.college.result.dto.MarksDtos.*;
import com.college.result.dto.UserDtos.FacultyResponse;
import com.college.result.model.Assignment;
import com.college.result.model.ExaminationSession;
import com.college.result.model.Mark;
import com.college.result.model.Subject;
import com.college.result.repository.AssignmentRepository;
import com.college.result.repository.EnrollmentRepository;
import com.college.result.repository.EnrollmentRepository.EnrolledStudent;
import com.college.result.repository.ExaminationSessionRepository;
import com.college.result.repository.FacultyRepository;
import com.college.result.repository.MarksRepository;
import com.college.result.repository.SubjectRepository;
import com.college.result.security.AuthUser;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks entry for faculty. Every operation first proves that the authenticated faculty member owns the assignment;
 * the faculty identity is derived from the verified token, never from request data.
 */
@Service
public class FacultyMarksService {

    private record Context(FacultyResponse faculty, Assignment assignment, ExaminationSession session,
                           Subject subject) {}

    private final FacultyRepository facultyRepo;
    private final AssignmentRepository assignmentRepo;
    private final ExaminationSessionRepository sessionRepo;
    private final SubjectRepository subjectRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final MarksRepository marksRepo;
    private final AuditService audit;

    public FacultyMarksService(FacultyRepository facultyRepo, AssignmentRepository assignmentRepo,
                               ExaminationSessionRepository sessionRepo, SubjectRepository subjectRepo,
                               EnrollmentRepository enrollmentRepo, MarksRepository marksRepo, AuditService audit) {
        this.facultyRepo = facultyRepo;
        this.assignmentRepo = assignmentRepo;
        this.sessionRepo = sessionRepo;
        this.subjectRepo = subjectRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.marksRepo = marksRepo;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ read side
    public List<FacultyAssignment> myAssignments(AuthUser user) {
        FacultyResponse faculty = faculty(user);
        return assignmentRepo.findByFaculty(faculty.id()).stream().map(a -> new FacultyAssignment(a.id(),
                a.subjectId(), a.subjectCode(), a.subjectName(), a.semesterId(), a.semesterLabel(),
                a.enrolledStudents(), sessionRepo.findBySemester(a.semesterId()).stream()
                .map(s -> new SessionBrief(s.id(), s.name(), s.status())).toList())).toList();
    }

    public List<EnrolledStudentResponse> students(AuthUser user, long assignmentId) {
        FacultyResponse faculty = faculty(user);
        Assignment a = ownedAssignment(faculty, assignmentId);
        return enrollmentRepo.enrolledStudents(a.subjectId(), a.semesterId()).stream()
                .map(s -> new EnrolledStudentResponse(s.studentId(), s.rollNumber(), s.fullName())).toList();
    }

    public MarkSheet sheet(AuthUser user, long assignmentId, long sessionId) {
        return buildSheet(authorize(user, assignmentId, sessionId, false));
    }

    public List<SubmissionSummary> submissions(AuthUser user) {
        return marksRepo.submissionSummaries(faculty(user).id());
    }

    public FacultyDashboard dashboard(AuthUser user) {
        FacultyResponse f = faculty(user);
        return new FacultyDashboard(assignmentRepo.findByFaculty(f.id()).size(),
                marksRepo.countOpenSessionsForFaculty(f.id()),
                marksRepo.countByFacultyAndStatus(f.id(), "DRAFT", "REJECTED"),
                marksRepo.countByFacultyAndStatus(f.id(), "SUBMITTED"),
                marksRepo.countByFacultyAndStatus(f.id(), "APPROVED"),
                marksRepo.countByFacultyAndStatus(f.id(), "PUBLISHED"));
    }

    // ------------------------------------------------------------------ write side
    @Transactional
    public MarkSheet saveDraft(AuthUser user, long assignmentId, long sessionId, SaveMarksRequest request) {
        Context ctx = authorize(user, assignmentId, sessionId, true);
        Set<Long> enrolled = enrollmentRepo.enrolledStudents(ctx.subject().id(), ctx.assignment().semesterId())
                .stream().map(EnrolledStudent::studentId).collect(Collectors.toSet());
        Map<Long, Mark> existing = marksRepo.findBySessionAndSubjectForUpdate(sessionId, ctx.subject().id())
                .stream().collect(Collectors.toMap(Mark::studentId, m -> m));

        Set<Long> seen = new HashSet<>();
        for (MarkEntry e : request.entries()) {
            if (!seen.add(e.studentId())) {
                throw ApiException.badRequest("Student " + e.studentId() + " appears more than once");
            }
            if (!enrolled.contains(e.studentId())) {
                throw ApiException.badRequest("Student " + e.studentId() + " is not enrolled in this subject");
            }
            validateRange(ctx.subject(), e);
            Mark current = existing.get(e.studentId());
            if (current != null && !current.status().editableByFaculty()) {
                throw ApiException.conflict("Marks for student " + e.studentId() + " are " + current.status()
                        + " and can no longer be edited by faculty");
            }
        }

        for (MarkEntry e : request.entries()) {
            Mark current = existing.get(e.studentId());
            if (current == null) {
                long id = marksRepo.insert(e.studentId(), ctx.subject().id(), sessionId, e.internalMarks(),
                        e.externalMarks(), ctx.faculty().id());
                audit.log(user, "MARKS_CREATED", "MARK", id, details(ctx, e.studentId(), null, null,
                        e.internalMarks(), e.externalMarks()));
            } else if (changed(current, e) || current.status() != com.college.result.model.MarkStatus.DRAFT) {
                marksRepo.updateDraft(current.id(), e.internalMarks(), e.externalMarks(), ctx.faculty().id());
                audit.log(user, "MARKS_UPDATED", "MARK", current.id(), details(ctx, e.studentId(),
                        current.internalMarks(), current.externalMarks(), e.internalMarks(), e.externalMarks()));
            }
        }
        return buildSheet(ctx);
    }

    @Transactional
    public SubmitResult submit(AuthUser user, long assignmentId, long sessionId) {
        Context ctx = authorize(user, assignmentId, sessionId, true);
        List<EnrolledStudent> enrolled = enrollmentRepo.enrolledStudents(ctx.subject().id(),
                ctx.assignment().semesterId());
        if (enrolled.isEmpty()) {
            throw ApiException.conflict("No students are enrolled in this subject");
        }
        Map<Long, Mark> marks = marksRepo.findBySessionAndSubjectForUpdate(sessionId, ctx.subject().id())
                .stream().collect(Collectors.toMap(Mark::studentId, m -> m));
        List<String> incomplete = new java.util.ArrayList<>();
        for (EnrolledStudent s : enrolled) {
            Mark m = marks.get(s.studentId());
            if (m == null || !m.complete()) {
                incomplete.add(s.rollNumber());
            }
        }
        if (!incomplete.isEmpty()) {
            throw ApiException.conflict("Enter both internal and external marks for every student before submitting. "
                    + "Incomplete: " + String.join(", ", incomplete.stream().limit(10).toList())
                    + (incomplete.size() > 10 ? " and " + (incomplete.size() - 10) + " more" : ""));
        }
        int submitted = 0;
        for (EnrolledStudent s : enrolled) {
            Mark m = marks.get(s.studentId());
            if (m.status().editableByFaculty()) {
                marksRepo.markSubmitted(m.id());
                audit.log(user, "MARKS_SUBMITTED", "MARK", m.id(), details(ctx, m.studentId(), m.internalMarks(),
                        m.externalMarks(), m.internalMarks(), m.externalMarks()));
                submitted++;
            }
        }
        if (submitted == 0) {
            throw ApiException.conflict("There are no draft or rejected marks to submit");
        }
        return new SubmitResult(submitted);
    }

    // ------------------------------------------------------------------ helpers
    private FacultyResponse faculty(AuthUser user) {
        return facultyRepo.findByUserId(user.userId())
                .orElseThrow(() -> ApiException.forbidden("No faculty profile is linked to this account"));
    }

    private Assignment ownedAssignment(FacultyResponse faculty, long assignmentId) {
        Assignment a = assignmentRepo.findById(assignmentId)
                .orElseThrow(() -> ApiException.notFound("Assignment not found"));
        if (a.facultyId() != faculty.id()) {
            throw ApiException.forbidden("You are not assigned to this subject");
        }
        return a;
    }

    private Context authorize(AuthUser user, long assignmentId, long sessionId, boolean requireOpen) {
        FacultyResponse faculty = faculty(user);
        Assignment assignment = ownedAssignment(faculty, assignmentId);
        ExaminationSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Examination session not found"));
        if (session.semesterId() != assignment.semesterId()) {
            throw ApiException.badRequest("This examination session does not belong to the assignment's semester");
        }
        if (requireOpen && !session.isOpen()) {
            throw ApiException.conflict("The examination session is closed for marks entry");
        }
        Subject subject = subjectRepo.findById(assignment.subjectId()).orElseThrow();
        return new Context(faculty, assignment, session, subject);
    }

    private MarkSheet buildSheet(Context ctx) {
        Subject s = ctx.subject();
        return new MarkSheet(ctx.assignment().id(), ctx.session().id(), ctx.session().name(), ctx.session().status(),
                s.id(), s.code(), s.name(), s.internalMax(), s.externalMax(), s.internalPass(), s.externalPass(),
                s.totalPass(), ctx.session().isOpen(),
                marksRepo.sheetRows(s.id(), ctx.assignment().semesterId(), ctx.session().id(),
                        ctx.session().isOpen()));
    }

    /** Server-side maximum-marks validation (the UI check is only a convenience). */
    private void validateRange(Subject subject, MarkEntry e) {
        if (e.internalMarks() != null && e.internalMarks().compareTo(subject.internalMax()) > 0) {
            throw ApiException.badRequest("Internal marks " + e.internalMarks().stripTrailingZeros().toPlainString()
                    + " exceed the maximum of " + subject.internalMax().stripTrailingZeros().toPlainString()
                    + " for " + subject.code());
        }
        if (e.externalMarks() != null && e.externalMarks().compareTo(subject.externalMax()) > 0) {
            throw ApiException.badRequest("External marks " + e.externalMarks().stripTrailingZeros().toPlainString()
                    + " exceed the maximum of " + subject.externalMax().stripTrailingZeros().toPlainString()
                    + " for " + subject.code());
        }
    }

    private boolean changed(Mark current, MarkEntry e) {
        return differs(current.internalMarks(), e.internalMarks()) || differs(current.externalMarks(), e.externalMarks());
    }

    private static boolean differs(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a != b;
        }
        return a.compareTo(b) != 0;
    }

    private Map<String, Object> details(Context ctx, long studentId, BigDecimal oldI, BigDecimal oldE,
                                        BigDecimal newI, BigDecimal newE) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("studentId", studentId);
        d.put("subjectId", ctx.subject().id());
        d.put("sessionId", ctx.session().id());
        d.put("oldInternal", oldI);
        d.put("oldExternal", oldE);
        d.put("newInternal", newI);
        d.put("newExternal", newE);
        return d;
    }
}
