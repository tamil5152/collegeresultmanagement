package com.college.result.model;

import java.math.BigDecimal;

/** One boundary of a grading rule: percentage >= minPercentage earns {@code grade} worth {@code gradePoint}. */
public record GradeBand(String grade, BigDecimal minPercentage, BigDecimal gradePoint, boolean failGrade) {}
