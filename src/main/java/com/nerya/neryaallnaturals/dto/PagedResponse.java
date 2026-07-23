package com.nerya.neryaallnaturals.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A trimmed, stable pagination envelope for API responses — avoids leaking Spring Data's
 * verbose {@code Page} serialization (which also carries a deprecation warning).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagedResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;

    /**
     * Build from already-mapped content plus the source page's metadata.
     */
    public static <T> PagedResponse<T> of(List<T> content, Page<?> source) {
        return PagedResponse.<T>builder()
                .content(content)
                .page(source.getNumber())
                .size(source.getSize())
                .totalElements(source.getTotalElements())
                .totalPages(source.getTotalPages())
                .last(source.isLast())
                .build();
    }
}
