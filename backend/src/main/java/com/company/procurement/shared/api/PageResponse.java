package com.company.procurement.shared.api;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Stable pagination envelope used by every list endpoint. */
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {

  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
    return from(page.map(mapper));
  }
}
