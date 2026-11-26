package com.company.procurement.shared.tenant;

import com.company.procurement.shared.error.DomainException;
import com.company.procurement.shared.error.ErrorCodes;
import com.company.procurement.shared.error.ProblemDetailFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

/** Enforces the tenant read-only window while a subscription is inactive. */
public class TenantWriteAccessFilter extends OncePerRequestFilter {

  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

  private final TenantWriteAccessPort access;
  private final ProblemDetailFactory problemDetailFactory;
  private final ObjectMapper objectMapper;

  public TenantWriteAccessFilter(
      TenantWriteAccessPort access,
      ProblemDetailFactory problemDetailFactory,
      ObjectMapper objectMapper) {
    this.access = access;
    this.problemDetailFactory = problemDetailFactory;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (SAFE_METHODS.contains(request.getMethod())) {
      return true;
    }
    String path = request.getRequestURI();
    return path.startsWith("/api/v1/platform/")
        || path.startsWith("/api/v1/public/")
        || path.startsWith("/api/v1/subscription/")
        || "/api/v1/webhooks/stripe".equals(path);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    UUID organizationId = TenantContext.get().orElse(null);
    if (organizationId == null) {
      filterChain.doFilter(request, response);
      return;
    }
    try {
      access.assertWriteAllowed(organizationId);
    } catch (DomainException ex) {
      ProblemDetail problem =
          problemDetailFactory.create(
              HttpStatus.UNPROCESSABLE_ENTITY,
              ErrorCodes.SUBSCRIPTION_INACTIVE,
              ex.getMessage(),
              request,
              java.util.List.of());
      response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      objectMapper.writeValue(response.getOutputStream(), problem);
      return;
    }
    filterChain.doFilter(request, response);
  }
}
