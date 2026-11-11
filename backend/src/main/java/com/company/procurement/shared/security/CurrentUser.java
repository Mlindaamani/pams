package com.company.procurement.shared.security;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Access to the authenticated principal without leaking Spring Security details into domain code.
 */
@Component
public class CurrentUser {

  public String subject() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
      return jwtAuthentication.getToken().getSubject();
    }
    return authentication == null ? "system" : authentication.getName();
  }

  public String email() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
      String email = jwtAuthentication.getToken().getClaimAsString("email");
      if (email != null && !email.isBlank()) {
        return email;
      }
    }
    return username() + "@pams.local";
  }

  public String displayName() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
      String name = jwtAuthentication.getToken().getClaimAsString("name");
      if (name != null && !name.isBlank()) {
        return name;
      }
    }
    return username();
  }

  public String username() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication == null ? "system" : authentication.getName();
  }

  public boolean hasRole(String role) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return false;
    }
    String expected = "ROLE_" + role;
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(expected::equals);
  }

  public boolean hasAnyRole(String... roles) {
    for (String role : roles) {
      if (hasRole(role)) {
        return true;
      }
    }
    return false;
  }

  public List<String> roles() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return List.of();
    }
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(authority -> authority.startsWith("ROLE_"))
        .map(authority -> authority.substring("ROLE_".length()))
        .toList();
  }
}
