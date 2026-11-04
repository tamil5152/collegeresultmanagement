package com.college.result.service;

import com.college.result.common.ApiException;
import com.college.result.dto.ResultDtos.ResultDetail;
import com.college.result.dto.ResultDtos.StudentDashboard;
import com.college.result.dto.ResultDtos.StudentResultSummary;
import com.college.result.dto.UserDtos.StudentResponse;
import com.college.result.repository.ResultRepository;
import com.college.result.repository.ResultRepository.AttemptRow;
import com.college.result.repository.ResultRepository.ResultRow;
import com.college.result.repository.StudentRepository;
import com.college.result.security.AuthUser;
import com.college.result.service.ResultCalculator.Attempt;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side for students. The student is always resolved from the authenticated user id; a result id supplied by
 * the client is only honoured if it belongs to that student (otherwise 404, so existence is not leaked).
 */
@Service
@Transactional(readOnly = true)
public class StudentResultService {

    private final StudentRepository students;
    private final ResultRepository results;

    public StudentResultService(StudentRepository students, ResultRepository results) {
        this.students = students;
        this.results = results;
    }

    public StudentResponse profile(AuthUser user) {
        return student(user);
    }

    public List<StudentResultSummary> list(AuthUser user) {
        StudentResponse s = student(user);
        List<ResultRow> rows = results.rowsForStudent(s.id());
        Map<Long, BigDecimal> cgpa = cgpaByResult(rows, results.attemptsForStudent(s.id()));
        List<StudentResultSummary> out = new ArrayList<>();
        for (ResultRow r : rows) {
            out.add(new StudentResultSummary(r.id(), r.sessionId(), r.sessionName(), r.academicYear(),
                    r.semesterNumber(), r.sgpa(), cgpa.get(r.id()), r.totalCredits(), r.earnedCredits(), r.status(),
                    r.revision(), r.publishedAt()));
        }
        Collections.reverse(out); // latest first
        return out;
    }

    public ResultDetail detail(AuthUser user, long resultId) {
        StudentResponse s = student(user);
        ResultRow row = results.findRow(resultId)
                .filter(r -> r.studentId() == s.id())
                .orElseThrow(() -> ApiException.notFound("Result not found"));
        Map<Long, BigDecimal> cgpa = cgpaByResult(results.rowsForStudent(s.id()),
                results.attemptsForStudent(s.id()));
        return new ResultDetail(row.id(), s.id(), row.rollNumber(), row.studentName(), row.departmentName(),
                row.batchYear(), row.sessionName(), row.academicYear(), row.semesterNumber(), row.semesterName(),
                results.subjects(row.id()), row.totalCredits(), row.earnedCredits(), row.sgpa(), cgpa.get(row.id()),
                row.status(), row.revision(), row.ruleName(), row.ruleSample(), row.publishedAt());
    }

    public StudentDashboard dashboard(AuthUser user) {
        StudentResponse s = student(user);
        List<ResultRow> rows = results.rowsForStudent(s.id());
        if (rows.isEmpty()) {
            return new StudentDashboard(s.fullName(), s.rollNumber(), s.departmentName(), s.currentSemester(), 0, null,
                    null, BigDecimal.ZERO.setScale(2), null);
        }
        Map<Long, BigDecimal> cgpa = cgpaByResult(rows, results.attemptsForStudent(s.id()));
        ResultRow latest = rows.get(rows.size() - 1);
        BigDecimal earned = rows.stream().map(ResultRow::earnedCredits).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StudentDashboard(s.fullName(), s.rollNumber(), s.departmentName(), s.currentSemester(), rows.size(),
                latest.sgpa(), cgpa.get(latest.id()), earned, latest.sessionName());
    }

    /** CGPA after each published result, in chronological order; only the latest attempt of a subject counts. */
    static Map<Long, BigDecimal> cgpaByResult(List<ResultRow> rows, List<AttemptRow> attempts) {
        Map<Long, List<AttemptRow>> byResult = new HashMap<>();
        for (AttemptRow a : attempts) {
            byResult.computeIfAbsent(a.resultId(), k -> new ArrayList<>()).add(a);
        }
        Map<Long, BigDecimal> out = new LinkedHashMap<>();
        List<Attempt> chronological = new ArrayList<>();
        for (ResultRow r : rows) {
            for (AttemptRow a : byResult.getOrDefault(r.id(), List.of())) {
                chronological.add(new Attempt(a.subjectId(), a.credits(), a.gradePoint(), a.passed()));
            }
            out.put(r.id(), ResultCalculator.cgpa(chronological, r.includeFailed()));
        }
        return out;
    }

    private StudentResponse student(AuthUser user) {
        return students.findByUserId(user.userId())
                .orElseThrow(() -> ApiException.forbidden("No student profile is linked to this account"));
    }
}
