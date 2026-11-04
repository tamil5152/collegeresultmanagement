package com.college.result.model;

import java.math.BigDecimal;

public record Mark(long id, long studentId, long subjectId, long sessionId, BigDecimal internalMarks,
                   BigDecimal externalMarks, MarkStatus status, long enteredByFacultyId) {

    public boolean complete() {
        return internalMarks != null && externalMarks != null;
    }
}
