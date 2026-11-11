package com.company.procurement.organization.api;

import com.company.procurement.organization.application.OnboardingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding", description = "Tenant activation checklist")
public class OnboardingController {

  private final OnboardingService onboarding;

  public OnboardingController(OnboardingService onboarding) {
    this.onboarding = onboarding;
  }

  @GetMapping
  public OnboardingService.Checklist checklist() {
    return onboarding.checklist();
  }
}
