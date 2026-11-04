package com.college.result.model;

import java.math.BigDecimal;

public record Subject(long id, long departmentId, String code, String name, int semesterNumber, BigDecimal credits,
                      BigDecimal internalMax, BigDecimal externalMax, BigDecimal internalPass,
                      BigDecimal externalPass, BigDecimal totalPass, boolean active) {

    public BigDecimal maxTotal() {
        return internalMax.add(externalMax);
    }
}
