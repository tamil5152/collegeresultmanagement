package com.college.result.common;

/** Validated page request (0-based page, size 1..100). */
public record Paging(int page, int size) {

    public static final int MAX_SIZE = 100;

    public static Paging of(int page, int size) {
        return new Paging(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE));
    }

    public int offset() {
        return page * size;
    }

    /** Builds a LIKE pattern, escaping wildcard characters; null/blank search matches everything. */
    public static String like(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        String escaped = search.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
