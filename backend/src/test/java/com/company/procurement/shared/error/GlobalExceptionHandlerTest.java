package com.company.procurement.shared.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.procurement.TestSecurityConfig;
import com.company.procurement.shared.web.RequestIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ErrorDemoController.class)
@Import({
  TestSecurityConfig.class,
  ProblemDetailFactory.class,
  RequestIdFilter.class,
  GlobalExceptionHandlerTest.ClockConfig.class
})
class GlobalExceptionHandlerTest {

  private static final String REQUEST_ID = "test-request-id";

  @Autowired private MockMvc mockMvc;

  @TestConfiguration
  static class ClockConfig {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Test
  void mapsDomainNotFoundToProblemDetail() throws Exception {
    mockMvc
        .perform(get("/demo/missing").header(RequestIdFilter.HEADER, REQUEST_ID))
        .andExpect(status().isNotFound())
        .andExpect(header().string(RequestIdFilter.HEADER, REQUEST_ID))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value(ErrorCodes.RESOURCE_NOT_FOUND))
        .andExpect(jsonPath("$.type").value("urn:pams:error:resource-not-found"))
        .andExpect(jsonPath("$.title").value("Not Found"))
        .andExpect(jsonPath("$.detail").value("Organization not found"))
        .andExpect(jsonPath("$.instance").value("/demo/missing"))
        .andExpect(jsonPath("$.traceId").value(REQUEST_ID))
        .andExpect(jsonPath("$.timestamp").value("2026-01-01T00:00:00Z"));
  }

  @Test
  void generatesRequestIdWhenAbsent() throws Exception {
    mockMvc
        .perform(get("/demo/missing"))
        .andExpect(status().isNotFound())
        .andExpect(header().exists(RequestIdFilter.HEADER))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  @Test
  void mapsBeanValidationFailures() throws Exception {
    mockMvc
        .perform(
            post("/demo/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_FAILED))
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  void mapsMalformedBody() throws Exception {
    mockMvc
        .perform(
            post("/demo/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCodes.MALFORMED_REQUEST));
  }

  @Test
  void hidesUnexpectedExceptionDetails() throws Exception {
    mockMvc
        .perform(get("/demo/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value(ErrorCodes.INTERNAL_ERROR))
        .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
  }
}
