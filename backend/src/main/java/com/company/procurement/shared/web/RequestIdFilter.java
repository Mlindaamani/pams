package com.company.procurement.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ensures every request has a correlation id: accepts a client-supplied {@code X-Request-ID} or
 * generates one, exposes it via MDC and echoes it in the response header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Request-ID";
  public static final String MDC_KEY = "requestId";
  public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

  private static final int MAX_LENGTH = 128;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = sanitize(request.getHeader(HEADER));
    if (requestId == null) {
      requestId = UUID.randomUUID().toString();
    }

    request.setAttribute(ATTRIBUTE, requestId);
    response.setHeader(HEADER, requestId);
    MDC.put(MDC_KEY, requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  private static String sanitize(String candidate) {
    if (candidate == null || candidate.isBlank()) {
      return null;
    }
    String trimmed = candidate.trim();
    if (trimmed.length() > MAX_LENGTH) {
      return trimmed.substring(0, MAX_LENGTH);
    }
    return trimmed.chars().allMatch(c -> c >= 0x20 && c != 0x7f) ? trimmed : null;
  }
}
