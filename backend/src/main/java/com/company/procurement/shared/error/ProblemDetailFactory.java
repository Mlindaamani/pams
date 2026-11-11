package com.company.procurement.shared.error;

import com.company.procurement.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Builds RFC 7807 responses with the project-wide extension members. */
@Component
public class ProblemDetailFactory {

  private static final String TYPE_PREFIX = "urn:pams:error:";

  private final Clock clock;

  public ProblemDetailFactory(Clock clock) {
    this.clock = clock;
  }

  public ProblemDetail create(
      HttpStatus status,
      String code,
      String detail,
      HttpServletRequest request,
      List<ValidationError> errors) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(TYPE_PREFIX + code));
    problem.setTitle(status.getReasonPhrase());
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", code);
    problem.setProperty("traceId", currentTraceId(request));
    problem.setProperty("timestamp", clock.instant().toString());
    if (errors != null && !errors.isEmpty()) {
      problem.setProperty("errors", errors);
    }
    return problem;
  }

  private static String currentTraceId(HttpServletRequest request) {
    String mdcValue = MDC.get(RequestIdFilter.MDC_KEY);
    if (mdcValue != null) {
      return mdcValue;
    }
    Object attribute = request.getAttribute(RequestIdFilter.ATTRIBUTE);
    return attribute == null ? null : attribute.toString();
  }
}
