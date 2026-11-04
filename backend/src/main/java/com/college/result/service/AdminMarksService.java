package com.college.result.service;

import com.college.result.common.ApiException;
import com.college.result.common.PageResponse;
import com.college.result.common.Paging;
import com.college.result.dto.MarksDtos.CorrectionRequest;
import com.college.result.dto.MarksDtos.MarkReviewRow;
import com.college.result.dto.MarksDtos.ReviewRequest;
import com.college.result.dto.MarksDtos.ReviewResult;
import com.college.result.model.Mark;
import com.college.result.model.MarkStatus;
import com.college.result.model.Subject;
import com.college.result.repository.MarksRepository;
import com.college.result.repository.SubjectRepository;
import com.college.result.security.AuthUser;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin review (approve / reject) of submitted marks and the audited correction workflow. */
@Service
public class AdminMarksService {

    private final MarksRepository marks;
    private final SubjectRepository subjects;
    private final ResultPublicationService publication;
    private final AuditService audit;

    public AdminMarksService(MarksRepository marks, SubjectRepository subjects, ResultPublicationService publication,
                             AuditService audit) {
        this.marks = marks;
        this.subjects = subjects;
        this.publication = publication;
        this.audit = audit;
    }

    public PageResponse<MarkReviewRow> list(String search, long sessionId, long departmentId, long subjectId,
                                            String status, Paging p) {
        String st = normaliseStatus(status);
        return PageResponse.of(marks.searchReview(search, sessionId, departmentId, subjectId, st, p), p,
                marks.countReview(search, sessionId, departmentId, subjectId, st));
    }

    @Transactional
    public ReviewResult review(AuthUser admin, ReviewRequest request) {
        String action = request.action().trim().toUpperCase();
        MarkStatus target;
        switch (action) {
            case "APPROVE" -> target = MarkStatus.APPROVED;
            case "REJECT" -> target = MarkStatus.REJECTED;
            default -> throw ApiException.badRequest("Action must be APPROVE or REJECT");
        }
        String remarks = request.remarks() == null || request.remarks().isBlank() ? null : request.remarks().trim();
        if (target == MarkStatus.REJECTED && remarks == null) {
            throw ApiException.badRequest("Remarks are required when rejecting marks");
        }
        List<Long> ids = request.markIds().stream().distinct().toList();
        List<Mark> locked = marks.findByIdsForUpdate(ids);
        if (locked.size() != ids.size()) {
            throw ApiException.notFound("One or more selected marks do not exist");
        }
        for (Mark m : locked) {
            if (m.status() != MarkStatus.SUBMITTED) {
                throw ApiException.conflict("Mark " + m.id() + " is " + m.status() + "; only SUBMITTED marks can be "
                        + action.toLowerCase() + "d");
            }
        }
        for (Mark m : locked) {
            marks.review(m.id(), target, admin.userId(), remarks);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("studentId", m.studentId());
            d.put("subjectId", m.subjectId());
            d.put("sessionId", m.sessionId());
            d.put("internal", m.internalMarks());
            d.put("external", m.externalMarks());
            d.put("remarks", remarks);
            audit.log(admin, target == MarkStatus.APPROVED ? "MARKS_APPROVED" : "MARKS_REJECTED", "MARK", m.id(), d);
        }
        return new ReviewResult(locked.size(), action);
    }

    /**
     * The only way to change approved or published marks: requires a reason, keeps the previous values in
     * mark_corrections and the audit log, and recomputes (and re-versions) an already published result.
     */
    @Transactional
    public void correct(AuthUser admin, long markId, CorrectionRequest request) {
        Mark mark = marks.findByIdForUpdate(markId).orElseThrow(() -> ApiException.notFound("Mark not found"));
        if (mark.status() != MarkStatus.APPROVED && mark.status() != MarkStatus.PUBLISHED) {
            throw ApiException.conflict("Only APPROVED or PUBLISHED marks go through the correction workflow; "
                    + "this mark is " + mark.status());
        }
        Subject subject = subjects.findById(mark.subjectId()).orElseThrow();
        if (request.internalMarks().compareTo(subject.internalMax()) > 0
                || request.externalMarks().compareTo(subject.externalMax()) > 0) {
            throw ApiException.badRequest("Corrected marks exceed the subject maximum (internal "
                    + subject.internalMax().stripTrailingZeros().toPlainString() + ", external "
                    + subject.externalMax().stripTrailingZeros().toPlainString() + ")");
        }
        if (same(mark.internalMarks(), request.internalMarks()) && same(mark.externalMarks(), request.externalMarks())) {
            throw ApiException.badRequest("The corrected marks are identical to the current marks");
        }
        String reason = request.reason().trim();
        marks.insertCorrection(markId, mark.internalMarks(), mark.externalMarks(), request.internalMarks(),
                request.externalMarks(), reason, admin.userId());
        marks.applyCorrection(markId, request.internalMarks(), request.externalMarks());
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("studentId", mark.studentId());
        d.put("subjectId", mark.subjectId());
        d.put("sessionId", mark.sessionId());
        d.put("oldInternal", mark.internalMarks());
        d.put("oldExternal", mark.externalMarks());
        d.put("newInternal", request.internalMarks());
        d.put("newExternal", request.externalMarks());
        d.put("reason", reason);
        d.put("wasPublished", mark.status() == MarkStatus.PUBLISHED);
        audit.log(admin, "MARKS_CORRECTED", "MARK", markId, d);
        if (mark.status() == MarkStatus.PUBLISHED) {
            publication.rebuildPublished(admin, mark.studentId(), mark.sessionId());
        }
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    private static String normaliseStatus(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        try {
            return MarkStatus.valueOf(status.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown status: " + status);
        }
    }
}
