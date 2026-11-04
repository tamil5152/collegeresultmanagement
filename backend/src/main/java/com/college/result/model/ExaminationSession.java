package com.college.result.model;

import java.time.LocalDate;

public record ExaminationSession(long id, String name, long semesterId, int semesterNumber, LocalDate startDate,
                                 LocalDate endDate, String status) {

    public boolean isOpen() {
        return "OPEN".equals(status);
    }
}
