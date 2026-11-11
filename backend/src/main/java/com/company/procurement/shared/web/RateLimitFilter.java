package com.company.procurement.shared.web;

import com.company.procurement.shared.config.RateLimitProperties;
import com.company.procurement.shared.error.ErrorCodes;
import com.company.procurement.shared.error.ProblemDetailFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-user/IP token-bucket rate limiting for write endpoints. */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final Set<String> LIMITED_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

  private final RateLimitProperties properties;
  private final ProblemDetailFactory problemDetailFactory;
  private final ObjectMapper objectMapper;
  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  public RateLimitFilter(
      RateLimitProperties properties,
      ProblemDetailFactory problemDetailFactory,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.problemDetailFactory = problemDetailFactory;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !properties.enabled()
        || !LIMITED_METHODS.contains(request.getMethod())
        || !request.getRequestURI().startsWith("/api/v1");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Bucket bucket =
        buckets.computeIfAbsent(
            requesterKey(request),
            key ->
                Bucket.builder()
                    .addLimit(
                        Bandwidth.builder()
                            .capacity(properties.capacity())
                            .refillGreedy(
                                properties.refillTokens(),
                                Duration.ofSeconds(properties.refillPeriodSeconds()))
                            .build())
                    .build());

    if (bucket.tryConsume(1)) {
      filterChain.doFilter(request, response);
      return;
    }

    ProblemDetail problem =
        problemDetailFactory.create(
            HttpStatus.TOO_MANY_REQUESTS,
            ErrorCodes.RATE_LIMITED,
            "Too many requests. Please retry later.",
            request,
            List.of());
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader("Retry-After", String.valueOf(properties.refillPeriodSeconds()));
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getOutputStream(), problem);
  }

  private static String requesterKey(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
      return "user:" + authentication.getName();
    }
    return "ip:" + request.getRemoteAddr();
  }
}
