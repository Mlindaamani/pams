package com.company.procurement.shared.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the tenant from the {@code organization_id} JWT claim and exposes it for the request.
 * Runs inside the Spring Security filter chain after bearer-token authentication.
 */
public class TenantContextFilter extends OncePerRequestFilter {

  public static final String CLAIM = "organization_id";

  private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      resolveTenant();
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  private static void resolveTenant() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
      return;
    }
    String claim = jwtAuthentication.getToken().getClaimAsString(CLAIM);
    if (claim == null || claim.isBlank()) {
      return;
    }
    try {
      TenantContext.set(UUID.fromString(claim.trim()));
    } catch (IllegalArgumentException ex) {
      log.warn("Ignoring malformed organization_id claim");
    }
  }
}
