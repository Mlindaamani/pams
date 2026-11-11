package com.company.procurement.shared.web;

import com.company.procurement.shared.error.PreconditionFailedException;
import com.company.procurement.shared.error.PreconditionRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/** Helpers for optimistic concurrency via ETag / If-Match. */
public final class ConditionalRequests {

  private ConditionalRequests() {}

  public static String etag(long version) {
    return "\"" + version + "\"";
  }

  /** Returns the expected version, or -1 when the client supplied {@code If-Match: *}. */
  public static long requireIfMatch(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.IF_MATCH);
    if (header == null || header.isBlank()) {
      throw new PreconditionRequiredException("If-Match header is required for this operation");
    }
    String value = header.trim();
    if ("*".equals(value)) {
      return -1;
    }
    if (value.startsWith("W/")) {
      value = value.substring(2).trim();
    }
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      value = value.substring(1, value.length() - 1);
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      throw new PreconditionFailedException("If-Match must contain a numeric resource version");
    }
  }
}
