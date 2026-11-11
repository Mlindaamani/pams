package com.company.procurement.shared.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only controller used to exercise the global exception handler. */
@RestController
@RequestMapping("/demo")
public class ErrorDemoController {

  @GetMapping("/missing")
  void missing() {
    throw new ResourceNotFoundException("Organization not found");
  }

  @GetMapping("/boom")
  void boom() {
    throw new IllegalStateException("sensitive internal detail");
  }

  @PostMapping("/validate")
  void validate(@Valid @RequestBody DemoRequest request) {
    // no-op
  }

  public record DemoRequest(@NotBlank String name) {}
}
