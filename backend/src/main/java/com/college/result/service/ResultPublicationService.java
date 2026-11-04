package com.college.result.service;

import com.college.result.common.ApiException;
import com.college.result.dto.ResultDtos.PublicationRequest;
import com.college.result.dto.ResultDtos.PublicationStatus;
import com.college.result.dto.ResultDtos.PublishResult;
import com.college.result.model.ExaminationSession;
import com.college.result.model.GradingRule;
import com.college.result.model.Mark;
import com.college.result.model.MarkStatus;
import com.college.result.model.Subject;
import com.college.result.repository.DepartmentRepository;
import com.college.result.repository.ExaminationSessionRepository;
import com.college.result.repository.GradingRuleRepository;
import com.college.result.repository.MarksRepository;
import com.college.result.repository.ResultRepository;
import com.college.result.repository.ResultRepository.ResultHeader;
import com.college.result.repository.ResultRepository.ScopeRow;
import com.college.result.repository.SubjectRepository;
import com.college.result.security.AuthUser;
import com.college.result.service.ResultCalculator.SemesterOutcome;
import com.college.result.service.ResultCalculator.SubjectOutcome;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-only result publication. A department's results for one examination session are published in a single
 * transaction and only when every enrolled subject of every (active) student has complete, APPROVED marks.
 */
@Service
public class ResultPublicationService {

    private final ExaminationSessionRepository sessions;
    private final DepartmentRepository departments;
    private final ResultRepository results;
    private final MarksRepository marks;
    private final SubjectRepository subjects;
    private final GradingRuleRepository rules;
    private final AuditService audit;

    public ResultPublicationService(ExaminationSessionRepository sessions, DepartmentRepository departments,
                                    ResultRepository results, MarksRepository marks, SubjectRepository subjects,
                                    GradingRuleRepository rules, AuditService audit) {
        this.sessions = sessions;
        this.departments = departments;
        this.results = results;
        this.marks = marks;
        this.subjects = subjects;
        this.rules = rules;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PublicationStatus status(long sessionId, long departmentId) {
        ExaminationSession session = sessions.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Examination session not found"));
        String deptName = departments.findById(departmentId)
                .orElseThrow(() -> ApiException.notFound("Department not found")).name();
        Map<Long, List<ScopeRow>> scope = groupByStudent(results.scope(sessionId, session.semesterId(), departmentId));
        long ready = 0;
        long missing = 0;
        long unapproved = 0;
        List<String> blocked = new ArrayList<>();
        for (List<ScopeRow> rows : scope.values()) {
            Blocker b = blocker(rows);
            switch (b) {
                case NONE -> ready++;
                case MISSING -> missing++;
                case UNAPPROVED -> unapproved++;
            }
            if (b != Blocker.NONE && blocked.size() < 25) {
                blocked.add(rows.get(0).rollNumber() + " - " + rows.get(0).studentName() + " ("
                        + (b == Blocker.MISSING ? "marks not entered/submitted" : "marks not approved") + ")");
            }
        }
        String rule = rules.findActive().map(GradingRule::name).orElse(null);
        boolean can = !scope.isEmpty() && ready == scope.size() && rule != null;
        String message;
        if (scope.isEmpty()) {
            message = "No unpublished results remain for this session and department.";
        } else if (rule == null) {
            message = "No grading rule is active. Activate one under Grading Rules first.";
        } else if (ready != scope.size()) {
            message = "Publication is blocked until every student's marks are entered, submitted and approved.";
        } else {
            message = "Ready to publish.";
        }
        return new PublicationStatus(sessionId, departmentId, session.name(), deptName, scope.size(),
                results.countAlreadyPublished(sessionId, departmentId), ready, missing, unapproved, rule, can, message,
                blocked);
    }

    @Transactional
    public PublishResult publish(AuthUser admin, PublicationRequest request) {
        // Serialise concurrent publications of the same session.
        ExaminationSession session = sessions.lockById(request.sessionId())
                .orElseThrow(() -> ApiException.notFound("Examination session not found"));
        departments.findById(request.departmentId()).orElseThrow(() -> ApiException.notFound("Department not found"));
        GradingRule rule = rules.findActive()
                .orElseThrow(() -> ApiException.conflict("No grading rule is active. Activate one before publishing."));
        Map<Long, List<ScopeRow>> scope = groupByStudent(
                results.scope(session.id(), session.semesterId(), request.departmentId()));
        if (scope.isEmpty()) {
            throw ApiException.conflict("No unpublished results remain for this session and department");
        }
        long blocked = scope.values().stream().filter(rows -> blocker(rows) != Blocker.NONE).count();
        if (blocked > 0) {
            throw ApiException.conflict(blocked + " student(s) have missing or unapproved marks. All marks must be "
                    + "entered, submitted and approved before results can be published.");
        }

        Map<Long, Subject> subjectCache = new HashMap<>();
        List<Long> publishedMarkIds = new ArrayList<>();
        int count = 0;
        for (Map.Entry<Long, List<ScopeRow>> entry : scope.entrySet()) {
            List<SubjectOutcome> outcomes = new ArrayList<>();
            List<ScopeRow> rows = entry.getValue();
            for (ScopeRow row : rows) {
                Subject subject = subjectCache.computeIfAbsent(row.subjectId(),
                        id -> subjects.findById(id).orElseThrow());
                outcomes.add(ResultCalculator.evaluate(subject, row.internal(), row.external(), rule));
            }
            SemesterOutcome summary = ResultCalculator.summarise(outcomes, rule);
            long resultId = results.insertResult(entry.getKey(), session.id(), rule.id(), summary.totalCredits(),
                    summary.earnedCredits(), summary.sgpa(), summary.passed() ? "PASS" : "FAIL", admin.userId());
            for (int i = 0; i < rows.size(); i++) {
                ScopeRow row = rows.get(i);
                SubjectOutcome o = outcomes.get(i);
                Subject subject = subjectCache.get(row.subjectId());
                results.insertSubject(resultId, subject.id(), row.markId(), subject.code(), subject.name(),
                        o.credits(), o.internal(), o.external(), o.total(), o.maxTotal(), o.percentage(), o.grade(),
                        o.gradePoint(), o.passed());
                publishedMarkIds.add(row.markId());
            }
            audit.log(admin, "RESULT_PUBLISHED", "RESULT", resultId, Map.of("studentId", entry.getKey(),
                    "sessionId", session.id(), "sgpa", summary.sgpa(), "status", summary.passed() ? "PASS" : "FAIL",
                    "gradingRuleId", rule.id()));
            count++;
        }
        marks.setPublished(publishedMarkIds);
        audit.log(admin, "RESULTS_PUBLISHED_BATCH", "EXAMINATION_SESSION", session.id(),
                Map.of("departmentId", request.departmentId(), "students", count, "gradingRuleId", rule.id()));
        return new PublishResult(count);
    }

    /**
     * Recomputes a student's already published result after an authorised correction. Uses the grading rule that
     * the result was originally published with and bumps the revision. Must run inside the caller's transaction.
     */
    public void rebuildPublished(AuthUser admin, long studentId, long sessionId) {
        ResultHeader header = results.lockByStudentAndSession(studentId, sessionId).orElse(null);
        if (header == null) {
            return;
        }
        GradingRule rule = rules.findById(header.ruleId()).orElseThrow();
        List<Mark> published = marks.findForStudentSession(studentId, sessionId).stream()
                .filter(m -> m.status() == MarkStatus.PUBLISHED).toList();
        List<SubjectOutcome> outcomes = new ArrayList<>();
        results.deleteSubjects(header.id());
        for (Mark m : published) {
            Subject subject = subjects.findById(m.subjectId()).orElseThrow();
            SubjectOutcome o = ResultCalculator.evaluate(subject, m.internalMarks(), m.externalMarks(), rule);
            outcomes.add(o);
            results.insertSubject(header.id(), subject.id(), m.id(), subject.code(), subject.name(), o.credits(),
                    o.internal(), o.external(), o.total(), o.maxTotal(), o.percentage(), o.grade(), o.gradePoint(),
                    o.passed());
        }
        SemesterOutcome summary = ResultCalculator.summarise(outcomes, rule);
        results.updateTotals(header.id(), summary.totalCredits(), summary.earnedCredits(), summary.sgpa(),
                summary.passed() ? "PASS" : "FAIL");
        audit.log(admin, "RESULT_REVISED", "RESULT", header.id(), Map.of("studentId", studentId, "sessionId", sessionId,
                "newRevision", header.revision() + 1, "sgpa", summary.sgpa()));
    }

    private enum Blocker { NONE, MISSING, UNAPPROVED }

    private Blocker blocker(List<ScopeRow> rows) {
        boolean unapproved = false;
        for (ScopeRow r : rows) {
            if (r.markId() == null || r.internal() == null || r.external() == null) {
                return Blocker.MISSING;
            }
            if (!"APPROVED".equals(r.markStatus())) {
                unapproved = true;
            }
        }
        return unapproved ? Blocker.UNAPPROVED : Blocker.NONE;
    }

    private Map<Long, List<ScopeRow>> groupByStudent(List<ScopeRow> rows) {
        Map<Long, List<ScopeRow>> grouped = new LinkedHashMap<>();
        for (ScopeRow r : rows) {
            grouped.computeIfAbsent(r.studentId(), k -> new ArrayList<>()).add(r);
        }
        return grouped;
    }
}
