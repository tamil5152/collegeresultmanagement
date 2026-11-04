package com.college.result.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.college.result.common.Paging;
import com.college.result.model.GradingRule;
import com.college.result.model.Mark;
import com.college.result.model.MarkStatus;
import com.college.result.repository.EnrollmentRepository;
import com.college.result.repository.GradingRuleRepository;
import com.college.result.repository.MarksRepository;
import com.college.result.repository.ResultRepository;
import com.college.result.repository.StudentRepository;
import com.college.result.repository.SubjectRepository;
import com.college.result.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/** Repository / schema behaviour against the real database. */
class RepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired MarksRepository marks;
    @Autowired StudentRepository students;
    @Autowired SubjectRepository subjects;
    @Autowired EnrollmentRepository enrollments;
    @Autowired GradingRuleRepository rules;
    @Autowired ResultRepository results;

    @Test
    void duplicateMarksForSameStudentSubjectAndExaminationAreRejectedByTheDatabase() {
        // student 1 / subject 9 (CS303) / session 3 has no marks yet
        marks.insert(1, 9, 3, new BigDecimal("10"), new BigDecimal("20"), 1);
        assertThatThrownBy(() -> marks.insert(1, 9, 3, new BigDecimal("11"), new BigDecimal("21"), 1))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void negativeMarksViolateTheCheckConstraint() {
        assertThatThrownBy(() -> marks.insert(1, 9, 3, new BigDecimal("-1"), null, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void foreignKeysAreEnforced() {
        assertThatThrownBy(() -> marks.insert(9999, 9, 3, BigDecimal.ONE, BigDecimal.ONE, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void marksKeepTwoDecimalPrecisionWithoutBinaryRounding() {
        long id = marks.insert(1, 9, 3, new BigDecimal("33.33"), new BigDecimal("44.44"), 1);
        Mark m = marks.findByIdForUpdate(id).orElseThrow();
        assertThat(m.internalMarks()).isEqualByComparingTo("33.33");
        assertThat(m.externalMarks()).isEqualByComparingTo("44.44");
        assertThat(m.status()).isEqualTo(MarkStatus.DRAFT);
    }

    @Test
    void studentSearchPagesAndFiltersWithParameterisedQueries() {
        var first = students.search("", 0, 0, -1, Paging.of(0, 4));
        assertThat(first).hasSize(4);
        assertThat(students.count("", 0, 0, -1)).isEqualTo(10);
        assertThat(students.count("", 2, 0, -1)).isEqualTo(4);               // ECE only
        assertThat(students.count("diya", 0, 0, -1)).isEqualTo(1);
        // wildcard characters from user input are escaped, SQL metacharacters are inert
        assertThat(students.count("%", 0, 0, -1)).isZero();
        assertThat(students.count("x' OR '1'='1", 0, 0, -1)).isZero();
        assertThat(students.search("", 0, 0, -1, Paging.of(2, 4))).hasSize(2);
    }

    @Test
    void subjectFiltersByDepartmentAndSemester() {
        assertThat(subjects.count("", 1, 3)).isEqualTo(3);
        assertThat(subjects.count("", 2, 0)).isEqualTo(2);
        assertThat(subjects.search("CS30", 0, 0, Paging.of(0, 10))).hasSize(3);
    }

    @Test
    void bulkEnrollIsIdempotent() {
        long before = enrollments.count("", 3, 0, 0);
        assertThat(enrollments.bulkEnroll(3, 1, 3)).isZero();
        assertThat(enrollments.count("", 3, 0, 0)).isEqualTo(before);
    }

    @Test
    void activeGradingRuleRoundTrip() {
        GradingRule rule = rules.findActive().orElseThrow();
        assertThat(rule.bands()).hasSize(8);
        assertThat(rule.bands()).anyMatch(b -> b.failGrade() && b.grade().equals("F"));
    }

    @Test
    void publicationScopeListsOnlyUnpublishedStudents() {
        var scope = results.scope(1, 1, 1);
        assertThat(scope).hasSize(18); // 6 students x 3 subjects
        assertThat(scope).allMatch(r -> "APPROVED".equals(r.markStatus()));
        assertThat(results.scope(3, 3, 1)).anyMatch(r -> r.markId() == null); // marks not entered yet
    }
}
