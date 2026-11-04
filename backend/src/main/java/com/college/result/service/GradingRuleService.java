package com.college.result.service;

import com.college.result.common.ApiException;
import com.college.result.dto.MasterDtos.BandRequest;
import com.college.result.dto.MasterDtos.GradingRuleRequest;
import com.college.result.dto.MasterDtos.GradingRuleResponse;
import com.college.result.repository.GradingRuleRepository;
import com.college.result.security.AuthUser;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradingRuleService {

    private final GradingRuleRepository rules;
    private final AuditService audit;

    public GradingRuleService(GradingRuleRepository rules, AuditService audit) {
        this.rules = rules;
        this.audit = audit;
    }

    public List<GradingRuleResponse> list() {
        return rules.findAll();
    }

    @Transactional
    public GradingRuleResponse create(AuthUser actor, GradingRuleRequest r) {
        validateBands(r.bands());
        long id = rules.insert(r.name().trim(), blankToNull(r.description()), r.samplePlaceholder(),
                r.includeFailedInGpa());
        rules.replaceBands(id, r.bands());
        audit.log(actor, "GRADING_RULE_CREATED", "GRADING_RULE", id, r);
        return rules.findResponse(id).orElseThrow();
    }

    @Transactional
    public GradingRuleResponse update(AuthUser actor, long id, GradingRuleRequest r) {
        rules.findById(id).orElseThrow(() -> ApiException.notFound("Grading rule not found"));
        if (rules.usedByResults(id)) {
            throw ApiException.conflict("This rule was used to publish results and can no longer be edited. "
                    + "Create a new rule and activate it instead.");
        }
        validateBands(r.bands());
        rules.update(id, r.name().trim(), blankToNull(r.description()), r.samplePlaceholder(), r.includeFailedInGpa());
        rules.replaceBands(id, r.bands());
        audit.log(actor, "GRADING_RULE_UPDATED", "GRADING_RULE", id, r);
        return rules.findResponse(id).orElseThrow();
    }

    @Transactional
    public GradingRuleResponse activate(AuthUser actor, long id) {
        rules.findById(id).orElseThrow(() -> ApiException.notFound("Grading rule not found"));
        rules.deactivateAll();
        rules.activate(id);
        audit.log(actor, "GRADING_RULE_ACTIVATED", "GRADING_RULE", id, null);
        return rules.findResponse(id).orElseThrow();
    }

    private void validateBands(List<BandRequest> bands) {
        Set<String> grades = new HashSet<>();
        Set<String> minimums = new HashSet<>();
        boolean hasFail = false;
        boolean hasPass = false;
        for (BandRequest b : bands) {
            if (!grades.add(b.grade().trim().toUpperCase(Locale.ROOT))) {
                throw ApiException.badRequest("Duplicate grade: " + b.grade());
            }
            if (!minimums.add(b.minPercentage().stripTrailingZeros().toPlainString())) {
                throw ApiException.badRequest("Two grades share the minimum percentage " + b.minPercentage());
            }
            if (b.failGrade()) {
                hasFail = true;
            } else {
                hasPass = true;
            }
        }
        if (!hasFail || !hasPass) {
            throw ApiException.badRequest("A rule needs at least one fail grade and one passing grade");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
