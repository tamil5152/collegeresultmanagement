package com.college.result.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.college.result.common.ApiException;
import com.college.result.dto.MarksDtos.MarkEntry;
import com.college.result.dto.MarksDtos.SaveMarksRequest;
import com.college.result.dto.UserDtos.FacultyResponse;
import com.college.result.model.Assignment;
import com.college.result.model.ExaminationSession;
import com.college.result.model.Mark;
import com.college.result.model.MarkStatus;
import com.college.result.model.Role;
import com.college.result.model.Subject;
import com.college.result.repository.AssignmentRepository;
import com.college.result.repository.EnrollmentRepository;
import com.college.result.repository.EnrollmentRepository.EnrolledStudent;
import com.college.result.repository.ExaminationSessionRepository;
import com.college.result.repository.FacultyRepository;
import com.college.result.repository.MarksRepository;
import com.college.result.repository.SubjectRepository;
import com.college.result.security.AuthUser;
import com.college.result.service.AuditService;
import com.college.result.service.FacultyMarksService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FacultyMarksServiceTest {

    @Mock FacultyRepository facultyRepo;
    @Mock AssignmentRepository assignmentRepo;
    @Mock ExaminationSessionRepository sessionRepo;
    @Mock SubjectRepository subjectRepo;
    @Mock EnrollmentRepository enrollmentRepo;
    @Mock MarksRepository marksRepo;
    @Mock AuditService audit;

    FacultyMarksService service;
    final AuthUser user = new AuthUser(10, "fac1", "Faculty One", Role.FACULTY, "csrf");

    @BeforeEach
    void setUp() {
        service = new FacultyMarksService(facultyRepo, assignmentRepo, sessionRepo, subjectRepo, enrollmentRepo,
                marksRepo, audit);
        when(facultyRepo.findByUserId(10)).thenReturn(Optional.of(new FacultyResponse(1, 10, "fac1", "f@x.test",
                "Faculty One", 1, "CSE", "E1", null, null, true)));
        when(assignmentRepo.findById(5)).thenReturn(Optional.of(new Assignment(5, 9, 1, 3)));
        when(sessionRepo.findById(3)).thenReturn(Optional.of(session("OPEN")));
        when(subjectRepo.findById(9)).thenReturn(Optional.of(new Subject(9, 1, "CS303", "OS", 3, new BigDecimal("3"),
                new BigDecimal("40"), new BigDecimal("60"), new BigDecimal("16"), new BigDecimal("24"),
                new BigDecimal("40"), true)));
        when(enrollmentRepo.enrolledStudents(9, 3)).thenReturn(List.of(new EnrolledStudent(1, "R1", "One"),
                new EnrolledStudent(2, "R2", "Two")));
        when(marksRepo.findBySessionAndSubjectForUpdate(3, 9)).thenReturn(List.of());
    }

    private static ExaminationSession session(String status) {
        return new ExaminationSession(3, "ESE", 3, 3, LocalDate.now(), LocalDate.now(), status);
    }

    private static SaveMarksRequest req(Long student, String internal, String external) {
        return new SaveMarksRequest(List.of(new MarkEntry(student, internal == null ? null : new BigDecimal(internal),
                external == null ? null : new BigDecimal(external))));
    }

    @Test
    void rejectsInternalMarksAboveMaximumAndWritesNothing() {
        assertThatThrownBy(() -> service.saveDraft(user, 5, 3, req(1L, "40.01", "10")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(400));
        verify(marksRepo, never()).insert(anyLong(), anyLong(), anyLong(), any(), any(), anyLong());
    }

    @Test
    void rejectsExternalMarksAboveMaximum() {
        assertThatThrownBy(() -> service.saveDraft(user, 5, 3, req(1L, "10", "60.5")))
                .isInstanceOf(ApiException.class).hasMessageContaining("External marks");
    }

    @Test
    void acceptsMarksExactlyAtMaximum() {
        when(marksRepo.insert(anyLong(), anyLong(), anyLong(), any(), any(), anyLong())).thenReturn(100L);
        service.saveDraft(user, 5, 3, req(1L, "40", "60"));
        verify(marksRepo).insert(eq(1L), eq(9L), eq(3L), eq(new BigDecimal("40")), eq(new BigDecimal("60")), eq(1L));
        verify(audit).log(eq(user), eq("MARKS_CREATED"), eq("MARK"), eq(100L), any());
    }

    @Test
    void facultyCannotTouchAnotherFacultysAssignment() {
        when(assignmentRepo.findById(5)).thenReturn(Optional.of(new Assignment(5, 9, 2, 3)));
        assertThatThrownBy(() -> service.saveDraft(user, 5, 3, req(1L, "10", "10")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(403));
        assertThatThrownBy(() -> service.sheet(user, 5, 3))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(403));
        assertThatThrownBy(() -> service.students(user, 5)).isInstanceOf(ApiException.class);
    }

    @Test
    void closedSessionBlocksEntry() {
        when(sessionRepo.findById(3)).thenReturn(Optional.of(session("CLOSED")));
        assertThatThrownBy(() -> service.saveDraft(user, 5, 3, req(1L, "10", "10")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(409));
    }

    @Test
    void sessionOfAnotherSemesterIsRejected() {
        when(sessionRepo.findById(3)).thenReturn(Optional.of(
                new ExaminationSession(3, "ESE", 99, 3, LocalDate.now(), LocalDate.now(), "OPEN")));
        assertThatThrownBy(() -> service.saveDraft(user, 5, 3, req(1L, "10", "10"))).isInstanceOf(ApiException.class);
    }

    @Test
    void studentNotEnrolledIsRejected() {
        assertThatThrownBy(() -> service.saveDraft(user, 5, 3, req(77L, "10", "10")))
                .isInstanceOf(ApiException.class).hasMessageContaining("not enrolled");
    }

    @Test
    void duplicateStudentInOneRequestIsRejected() {
        SaveMarksRequest dup = new SaveMarksRequest(List.of(new MarkEntry(1L, BigDecimal.ONE, BigDecimal.ONE),
                new MarkEntry(1L, BigDecimal.TEN, BigDecimal.TEN)));
        assertThatThrownBy(() -> service.saveDraft(user, 5, 3, dup)).isInstanceOf(ApiException.class);
    }

    @Test
    void submittedMarksCannotBeEditedByFaculty() {
        when(marksRepo.findBySessionAndSubjectForUpdate(3, 9)).thenReturn(List.of(
                new Mark(50, 1, 9, 3, BigDecimal.TEN, BigDecimal.TEN, MarkStatus.SUBMITTED, 1)));
        assertThatThrownBy(() -> service.saveDraft(user, 5, 3, req(1L, "11", "11")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(409));
        verify(marksRepo, never()).updateDraft(anyLong(), any(), any(), anyLong());
    }

    @Test
    void rejectedMarksCanBeEditedAndGoBackToDraft() {
        when(marksRepo.findBySessionAndSubjectForUpdate(3, 9)).thenReturn(List.of(
                new Mark(50, 1, 9, 3, BigDecimal.TEN, BigDecimal.TEN, MarkStatus.REJECTED, 1)));
        service.saveDraft(user, 5, 3, req(1L, "10", "10"));
        verify(marksRepo).updateDraft(eq(50L), any(), any(), eq(1L));
    }

    @Test
    void submitRequiresCompleteMarksForEveryEnrolledStudent() {
        when(marksRepo.findBySessionAndSubjectForUpdate(3, 9)).thenReturn(List.of(
                new Mark(50, 1, 9, 3, BigDecimal.TEN, BigDecimal.TEN, MarkStatus.DRAFT, 1),
                new Mark(51, 2, 9, 3, BigDecimal.TEN, null, MarkStatus.DRAFT, 1)));
        assertThatThrownBy(() -> service.submit(user, 5, 3))
                .isInstanceOf(ApiException.class).hasMessageContaining("R2");
        verify(marksRepo, never()).markSubmitted(anyLong());
    }

    @Test
    void submitMovesCompleteDraftsToSubmittedAndAudits() {
        when(marksRepo.findBySessionAndSubjectForUpdate(3, 9)).thenReturn(List.of(
                new Mark(50, 1, 9, 3, BigDecimal.TEN, BigDecimal.TEN, MarkStatus.DRAFT, 1),
                new Mark(51, 2, 9, 3, BigDecimal.TEN, BigDecimal.ONE, MarkStatus.DRAFT, 1)));
        assertThat(service.submit(user, 5, 3).submitted()).isEqualTo(2);
        verify(marksRepo).markSubmitted(50);
        verify(marksRepo).markSubmitted(51);
        verify(audit, org.mockito.Mockito.times(2)).log(eq(user), eq("MARKS_SUBMITTED"), eq("MARK"), anyLong(), any());
    }
}
