package com.portifolio.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record PortfolioPagina<T>(List<T> content, int page, int size, long totalElements, boolean hasMore) {
    public static <T> PortfolioPagina<T> of(Page<T> page) {
        return new PortfolioPagina<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.hasNext());
    }
}
