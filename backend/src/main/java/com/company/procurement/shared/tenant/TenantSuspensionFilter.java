package com.company.procurement.shared.tenant;

import com.company.procurement.shared.error.ErrorCodes;
import com.company.procurement.shared.error.ProblemDetailFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects requests for tenants suspended from the platform admin area. */
public class TenantSuspensionFilter extends OncePerRequestFilter {

  private final TenantAvailabilityPort statuses;
  private final ProblemDetailFactory problemDetailFactory;
  private final ObjectMapper objectMapper;

  public TenantSuspensionFilter(
      TenantAvailabilityPort statuses,
      ProblemDetailFactory problemDetailFactory,
      ObjectMapper objectMapper) {
    this.statuses = statuses;
    this.problemDetailFactory = problemDetailFactory;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/api/v1/platform/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    UUID organizationId = TenantContext.get().orElse(null);
    if (organizationId != null && statuses.isSuspended(organizationId)) {
      ProblemDetail problem =
          problemDetailFactory.create(
              HttpStatus.FORBIDDEN,
              ErrorCodes.TENANT_SUSPENDED,
              "This tenant is suspended. Contact the platform administrator.",
              request,
              java.util.List.of());
      response.setStatus(HttpStatus.FORBIDDEN.value());
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      objectMapper.writeValue(response.getOutputStream(), problem);
      return;
    }
    filterChain.doFilter(request, response);
  }
}
