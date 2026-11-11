package com.company.procurement.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.procurement.shared.config.RateLimitProperties;
import com.company.procurement.shared.error.ProblemDetailFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RateLimitFilterTest {

  private final RateLimitFilter filter =
      new RateLimitFilter(
          new RateLimitProperties(true, 1, 1, 60),
          new ProblemDetailFactory(
              Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)),
          new ObjectMapper());

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void rejectsWritesAfterBucketIsExhausted() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("test-user", null, List.of()));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/vendors");
    AtomicInteger invocations = new AtomicInteger();
    FilterChain chain = (req, res) -> invocations.incrementAndGet();

    filter.doFilter(request, new MockHttpServletResponse(), chain);
    MockHttpServletResponse second = new MockHttpServletResponse();
    filter.doFilter(request, second, chain);

    assertThat(invocations).hasValue(1);
    assertThat(second.getStatus()).isEqualTo(429);
    assertThat(second.getHeader("Retry-After")).isEqualTo("60");
    assertThat(second.getContentAsString()).contains("rate-limited");
  }

  @Test
  void doesNotLimitReads() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/vendors");
    AtomicInteger invocations = new AtomicInteger();
    FilterChain chain = (req, res) -> invocations.incrementAndGet();
    filter.doFilter(request, new MockHttpServletResponse(), chain);
    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(invocations).hasValue(2);
  }
}
