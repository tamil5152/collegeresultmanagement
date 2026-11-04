package com.college.result.model;

import java.util.List;

public record GradingRule(long id, String name, boolean includeFailedInGpa, boolean active, List<GradeBand> bands) {}
