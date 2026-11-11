package com.company.procurement.shared.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * Writes RFC 7807 bodies for authentication/authorization failures raised inside the filter chain.
 */
@Component
public class SecurityProblemResponder {

  private final ProblemDetailFactory problemDetailFactory;
  private final ObjectMapper objectMapper;

  public SecurityProblemResponder(
      ProblemDetailFactory problemDetailFactory, ObjectMapper objectMapper) {
    this.problemDetailFactory = problemDetailFactory;
    this.objectMapper = objectMapper;
  }

  public void unauthorized(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    write(
        request,
        response,
        problemDetailFactory.create(
            HttpStatus.UNAUTHORIZED,
            ErrorCodes.UNAUTHORIZED,
            "Authentication is required",
            request,
            java.util.List.of()));
  }

  public void forbidden(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    write(
        request,
        response,
        problemDetailFactory.create(
            HttpStatus.FORBIDDEN,
            ErrorCodes.FORBIDDEN,
            "You do not have permission to perform this action",
            request,
            java.util.List.of()));
  }

  private void write(
      HttpServletRequest request, HttpServletResponse response, ProblemDetail problem)
      throws IOException {
    response.setStatus(problem.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getOutputStream(), problem);
  }
}
