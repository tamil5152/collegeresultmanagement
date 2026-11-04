package com.college.result.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.college.result.model.GradeBand;
import com.college.result.model.GradingRule;
import com.college.result.model.Subject;
import com.college.result.service.ResultCalculator;
import com.college.result.service.ResultCalculator.Attempt;
import com.college.result.service.ResultCalculator.SemesterOutcome;
import com.college.result.service.ResultCalculator.SubjectOutcome;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultCalculatorTest {

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    private static GradingRule rule(boolean includeFailed) {
        return new GradingRule(1, "test", includeFailed, true, List.of(
                new GradeBand("O", d("90"), d("10"), false),
                new GradeBand("A", d("75"), d("8"), false),
                new GradeBand("B", d("60"), d("6"), false),
                new GradeBand("P", d("40"), d("4"), false),
                new GradeBand("F", d("0"), d("0"), true)));
    }

    /** internal max 40 / external max 60, pass marks 16 / 24 / 40, 4 credits unless stated. */
    private static Subject subject(long id, String credits) {
        return new Subject(id, 1, "S" + id, "Subject " + id, 1, d(credits), d("40"), d("60"), d("16"), d("24"),
                d("40"), true);
    }

    @Test
    void gradeBoundaryIsInclusive() {
        SubjectOutcome exactly90 = ResultCalculator.evaluate(subject(1, "4"), d("36"), d("54"), rule(true));
        assertThat(exactly90.grade()).isEqualTo("O");
        assertThat(exactly90.percentage()).isEqualByComparingTo("90.00");
        SubjectOutcome justBelow = ResultCalculator.evaluate(subject(1, "4"), d("36"), d("53.99"), rule(true));
        assertThat(justBelow.grade()).isEqualTo("A");
        assertThat(justBelow.gradePoint()).isEqualByComparingTo("8");
    }

    @Test
    void failsWhenInternalBelowItsOwnPassMark() {
        SubjectOutcome o = ResultCalculator.evaluate(subject(1, "4"), d("15.5"), d("60"), rule(true));
        assertThat(o.passed()).isFalse();
        assertThat(o.grade()).isEqualTo("F");
        assertThat(o.gradePoint()).isEqualByComparingTo("0");
    }

    @Test
    void failsWhenExternalBelowItsOwnPassMarkEvenIfTotalIsHigh() {
        SubjectOutcome o = ResultCalculator.evaluate(subject(1, "4"), d("40"), d("23.99"), rule(true));
        assertThat(o.passed()).isFalse();
        assertThat(o.grade()).isEqualTo("F");
    }

    @Test
    void failsWhenTotalBelowTotalPassMark() {
        Subject s = new Subject(1, 1, "S1", "S", 1, d("4"), d("40"), d("60"), d("0"), d("0"), d("50"), true);
        assertThat(ResultCalculator.evaluate(s, d("20"), d("29.99"), rule(true)).passed()).isFalse();
        assertThat(ResultCalculator.evaluate(s, d("20"), d("30"), rule(true)).passed()).isTrue();
    }

    @Test
    void passingSubjectBelowEveryBandGetsLowestPassingBand() {
        Subject lenient = new Subject(1, 1, "S1", "S", 1, d("4"), d("40"), d("60"), d("0"), d("0"), d("30"), true);
        SubjectOutcome o = ResultCalculator.evaluate(lenient, d("10"), d("25"), rule(true)); // 35%
        assertThat(o.passed()).isTrue();
        assertThat(o.grade()).isEqualTo("P");
    }

    @Test
    void percentageUsesHalfUpRoundingToTwoDecimals() {
        Subject s = new Subject(1, 1, "S1", "S", 1, d("3"), d("30"), d("45"), d("0"), d("0"), d("0"), true);
        SubjectOutcome o = ResultCalculator.evaluate(s, d("10"), d("14"), rule(true)); // 24/75 = 32.00
        assertThat(o.percentage()).isEqualByComparingTo("32.00");
        SubjectOutcome o2 = ResultCalculator.evaluate(s, d("10"), d("14.01"), rule(true)); // 32.013333
        assertThat(o2.percentage()).isEqualByComparingTo("32.01");
        SubjectOutcome o3 = ResultCalculator.evaluate(s, d("10"), d("14.005"), rule(true)); // 32.0066 -> 32.01
        assertThat(o3.percentage()).isEqualByComparingTo("32.01");
    }

    @Test
    void sgpaIsCreditWeightedAndRoundedHalfUp() {
        // grade points 10 (4cr), 8 (4cr), 6 (3cr) -> (40+32+18)/11 = 8.1818
        List<SubjectOutcome> outcomes = List.of(
                ResultCalculator.evaluate(subject(1, "4"), d("40"), d("60"), rule(true)),
                ResultCalculator.evaluate(subject(2, "4"), d("30"), d("45"), rule(true)),
                ResultCalculator.evaluate(subject(3, "3"), d("25"), d("36"), rule(true)));
        SemesterOutcome s = ResultCalculator.summarise(outcomes, rule(true));
        assertThat(s.sgpa()).isEqualByComparingTo("8.18");
        assertThat(s.totalCredits()).isEqualByComparingTo("11");
        assertThat(s.earnedCredits()).isEqualByComparingTo("11");
        assertThat(s.passed()).isTrue();
    }

    @Test
    void halfUpAtExactMidpoint() {
        // 7.125 -> 7.13 (not banker's rounding 7.12)
        List<Attempt> attempts = List.of(new Attempt(1, d("8"), d("7.125"), true));
        assertThat(ResultCalculator.gradePointAverage(attempts, true)).isEqualByComparingTo("7.13");
    }

    @Test
    void failedCreditsAffectGpaOnlyWhenRuleSaysSo() {
        List<SubjectOutcome> outcomes = List.of(
                ResultCalculator.evaluate(subject(1, "4"), d("40"), d("60"), rule(true)),    // O  10, 4cr
                ResultCalculator.evaluate(subject(2, "4"), d("10"), d("60"), rule(true)));   // fail 0, 4cr
        SemesterOutcome withFail = ResultCalculator.summarise(outcomes, rule(true));
        SemesterOutcome withoutFail = ResultCalculator.summarise(outcomes, rule(false));
        assertThat(withFail.sgpa()).isEqualByComparingTo("5.00");
        assertThat(withoutFail.sgpa()).isEqualByComparingTo("10.00");
        assertThat(withFail.passed()).isFalse();
        assertThat(withFail.earnedCredits()).isEqualByComparingTo("4");
        assertThat(withFail.totalCredits()).isEqualByComparingTo("8");
    }

    @Test
    void cgpaUsesOnlyTheLatestAttemptOfARepeatedSubject() {
        List<Attempt> chronological = List.of(
                new Attempt(1, d("4"), d("0"), false),   // failed first time
                new Attempt(2, d("4"), d("8"), true),
                new Attempt(1, d("4"), d("6"), true));   // re-attempt passed
        assertThat(ResultCalculator.cgpa(chronological, true)).isEqualByComparingTo("7.00");
        assertThat(ResultCalculator.cgpa(chronological.subList(0, 2), true)).isEqualByComparingTo("4.00");
    }

    @Test
    void emptyInputGivesZero() {
        assertThat(ResultCalculator.cgpa(List.of(), true)).isEqualByComparingTo("0.00");
        assertThat(ResultCalculator.summarise(List.of(), rule(true)).passed()).isFalse();
    }

    @Test
    void missingMarksAreRejected() {
        assertThatThrownBy(() -> ResultCalculator.evaluate(subject(1, "4"), null, d("10"), rule(true)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ruleWithoutFailBandIsReportedAsConfigurationError() {
        GradingRule broken = new GradingRule(2, "broken", true, true, List.of(new GradeBand("P", d("40"), d("4"), false)));
        assertThatThrownBy(() -> ResultCalculator.evaluate(subject(1, "4"), d("1"), d("1"), broken))
                .isInstanceOf(IllegalStateException.class);
    }
}
