package com.company.procurement.vendor.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.procurement.TestSecurityConfig;
import com.company.procurement.shared.error.ProblemDetailFactory;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.vendor.api.mapper.VendorMapperImpl;
import com.company.procurement.vendor.application.VendorService;
import com.company.procurement.vendor.domain.Vendor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = VendorController.class)
@Import({
  TestSecurityConfig.class,
  ProblemDetailFactory.class,
  VendorMapperImpl.class,
  VendorControllerTest.ClockConfig.class
})
class VendorControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private VendorService vendors;

  @TestConfiguration
  static class ClockConfig {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Test
  void rejectsMissingRequiredFields() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/vendors")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taxId\":\"TAX-1\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  void rejectsUnknownJsonProperties() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/vendors")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Acme\",\"taxId\":\"TAX-1\",\"organizationId\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("malformed-request"));
  }

  @Test
  void rejectsMalformedId() throws Exception {
    mockMvc
        .perform(get("/api/v1/vendors/not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"));
  }

  @Test
  void rejectsUnlistedSortField() throws Exception {
    mockMvc
        .perform(get("/api/v1/vendors").param("sort", "password,desc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-request"));
  }

  @Test
  void returnsVendorDetail() throws Exception {
    UUID id = UUID.randomUUID();
    Vendor vendor = new Vendor(UUID.randomUUID(), "Acme Supplies", "TAX-1");
    given(vendors.get(id)).willReturn(vendor);

    mockMvc
        .perform(get("/api/v1/vendors/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Acme Supplies"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void mapsMissingVendorToNotFoundProblem() throws Exception {
    UUID id = UUID.randomUUID();
    given(vendors.get(id)).willThrow(new ResourceNotFoundException("Vendor not found"));

    mockMvc
        .perform(get("/api/v1/vendors/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("resource-not-found"));
  }

  @Test
  void returnsEmptyPageEnvelope() throws Exception {
    given(vendors.search(isNull(), isNull(), any()))
        .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc
        .perform(get("/api/v1/vendors"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20));
  }
}
