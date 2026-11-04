package com.college.result.common;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(List<T> items, Paging paging, long total) {
        int pages = (int) Math.ceil(total / (double) paging.size());
        return new PageResponse<>(items, paging.page(), paging.size(), total, pages);
    }
}
