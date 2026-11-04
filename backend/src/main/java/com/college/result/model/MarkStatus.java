package com.college.result.model;

public enum MarkStatus {
    DRAFT, SUBMITTED, APPROVED, REJECTED, PUBLISHED;

    /** Faculty may only change marks that are still drafts or were sent back. */
    public boolean editableByFaculty() {
        return this == DRAFT || this == REJECTED;
    }
}
