package com.college.result.service;

import com.college.result.model.GradeBand;
import com.college.result.model.GradingRule;
import com.college.result.model.Subject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure (database free) result arithmetic. All values are BigDecimal; no floating point is used.
 *
 * <ul>
 *   <li>A subject is passed only if internal >= internal pass mark AND external >= external pass mark
 *       AND total >= total pass mark (all configured per subject).</li>
 *   <li>Percentage = total / (internal max + external max) * 100, rounded HALF_UP to 2 decimals.</li>
 *   <li>Grade = highest non-fail band whose minimum is <= percentage. A failed subject receives the fail band.
 *       A subject that meets its pass criteria but is below every band minimum receives the lowest passing band.</li>
 *   <li>GPA = sum(grade point x credits) / credit denominator, rounded HALF_UP to 2 decimals. The denominator is
 *       the credits of passed subjects plus (if the rule says so) the credits of failed subjects.</li>
 * </ul>
 */
public final class ResultCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ResultCalculator() {}

    public record SubjectOutcome(long subjectId, BigDecimal credits, BigDecimal internal, BigDecimal external,
                                 BigDecimal total, BigDecimal maxTotal, BigDecimal percentage, String grade,
                                 BigDecimal gradePoint, boolean passed) {}

    public record SemesterOutcome(BigDecimal totalCredits, BigDecimal earnedCredits, BigDecimal sgpa,
                                  boolean passed) {}

    /** One subject attempt used for cumulative calculations. */
    public record Attempt(long subjectId, BigDecimal credits, BigDecimal gradePoint, boolean passed) {}

    public static SubjectOutcome evaluate(Subject subject, BigDecimal internal, BigDecimal external, GradingRule rule) {
        if (internal == null || external == null) {
            throw new IllegalArgumentException("Internal and external marks are both required");
        }
        BigDecimal total = internal.add(external);
        BigDecimal max = subject.maxTotal();
        BigDecimal percentage = total.multiply(HUNDRED).divide(max, 2, RoundingMode.HALF_UP);
        boolean passed = internal.compareTo(subject.internalPass()) >= 0
                && external.compareTo(subject.externalPass()) >= 0
                && total.compareTo(subject.totalPass()) >= 0;
        GradeBand band = passed ? passingBand(rule, percentage) : failBand(rule);
        return new SubjectOutcome(subject.id(), subject.credits(), internal, external, total, max, percentage,
                band.grade(), band.gradePoint(), passed);
    }

    public static SemesterOutcome summarise(List<SubjectOutcome> outcomes, GradingRule rule) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal earned = BigDecimal.ZERO;
        boolean allPassed = !outcomes.isEmpty();
        for (SubjectOutcome o : outcomes) {
            total = total.add(o.credits());
            if (o.passed()) {
                earned = earned.add(o.credits());
            } else {
                allPassed = false;
            }
        }
        List<Attempt> attempts = outcomes.stream()
                .map(o -> new Attempt(o.subjectId(), o.credits(), o.gradePoint(), o.passed())).toList();
        return new SemesterOutcome(total, earned, gradePointAverage(attempts, rule.includeFailedInGpa()), allPassed);
    }

    /**
     * Cumulative GPA. {@code chronological} must be ordered oldest to newest; when a subject appears more than once
     * (re-attempt) only its most recent attempt counts.
     */
    public static BigDecimal cgpa(List<Attempt> chronological, boolean includeFailedInGpa) {
        Map<Long, Attempt> latest = new LinkedHashMap<>();
        for (Attempt a : chronological) {
            latest.remove(a.subjectId());
            latest.put(a.subjectId(), a);
        }
        return gradePointAverage(List.copyOf(latest.values()), includeFailedInGpa);
    }

    public static BigDecimal gradePointAverage(List<Attempt> attempts, boolean includeFailedInGpa) {
        BigDecimal points = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (Attempt a : attempts) {
            if (!a.passed() && !includeFailedInGpa) {
                continue;
            }
            points = points.add(a.gradePoint().multiply(a.credits()));
            credits = credits.add(a.credits());
        }
        if (credits.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return points.divide(credits, 2, RoundingMode.HALF_UP);
    }

    private static GradeBand passingBand(GradingRule rule, BigDecimal percentage) {
        List<GradeBand> passing = rule.bands().stream().filter(b -> !b.failGrade())
                .sorted(Comparator.comparing(GradeBand::minPercentage).reversed()).toList();
        if (passing.isEmpty()) {
            throw new IllegalStateException("Grading rule '" + rule.name() + "' has no passing grade band");
        }
        return passing.stream().filter(b -> percentage.compareTo(b.minPercentage()) >= 0).findFirst()
                .orElse(passing.get(passing.size() - 1));
    }

    private static GradeBand failBand(GradingRule rule) {
        return rule.bands().stream().filter(GradeBand::failGrade)
                .min(Comparator.comparing(GradeBand::minPercentage))
                .orElseThrow(() -> new IllegalStateException("Grading rule '" + rule.name() + "' has no fail grade band"));
    }
}
