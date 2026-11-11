package com.company.procurement.shared.web;

import com.company.procurement.shared.error.BadRequestException;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Builds validated {@link Pageable}s with capped size and whitelisted sort fields. */
public final class PageRequests {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  private PageRequests() {}

  public static Pageable of(int page, int size, String sort, Set<String> allowedSortFields) {
    if (page < 0) {
      throw new BadRequestException("page must be zero or positive");
    }
    int effectiveSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    return PageRequest.of(page, effectiveSize, parseSort(sort, allowedSortFields));
  }

  private static Sort parseSort(String sort, Set<String> allowedSortFields) {
    if (sort == null || sort.isBlank()) {
      return allowedSortFields.contains("createdAt")
          ? Sort.by(Sort.Direction.DESC, "createdAt")
          : Sort.unsorted();
    }
    String[] parts = sort.split(",");
    if (parts.length > 2) {
      throw new BadRequestException("sort must be 'field' or 'field,asc|desc'");
    }
    String field = parts[0].trim();
    if (!allowedSortFields.contains(field)) {
      throw new BadRequestException(
          "sort field '%s' is not allowed; allowed: %s"
              .formatted(field, String.join(", ", allowedSortFields)));
    }
    Sort.Direction direction =
        parts.length == 2 ? parseDirection(parts[1].trim()) : Sort.Direction.ASC;
    return Sort.by(direction, field);
  }

  private static Sort.Direction parseDirection(String raw) {
    return switch (raw.toLowerCase()) {
      case "asc" -> Sort.Direction.ASC;
      case "desc" -> Sort.Direction.DESC;
      default -> throw new BadRequestException("sort direction must be 'asc' or 'desc'");
    };
  }
}
