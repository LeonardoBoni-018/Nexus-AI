package com.nexusai.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** Envelope de paginacao estavel para a API (nao expoe o formato interno do Spring Data). */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        int pages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, page, size, total, pages);
    }
}
