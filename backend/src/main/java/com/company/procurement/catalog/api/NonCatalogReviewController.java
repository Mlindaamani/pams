package com.company.procurement.catalog.api;

import com.company.procurement.catalog.application.NonCatalogReviewService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for catalog items used by purchase requests. */
@RestController
@RequestMapping("/api/v1/catalog-items/non-catalog-review")
@Tag(name = "Catalog", description = "Catalog items used by purchase requests")
@PreAuthorize("@permissions.has('catalog:manage')")
public class NonCatalogReviewController {

  private final NonCatalogReviewService reviews;

  public NonCatalogReviewController(NonCatalogReviewService reviews) {
    this.reviews = reviews;
  }

  @GetMapping
  public List<NonCatalogReviewService.NonCatalogLine> pending() {
    return reviews.pending();
  }

  @PutMapping("/{lineId}")
  public void resolve(@PathVariable UUID lineId, @Valid @RequestBody ResolveRequest request) {
    reviews.resolve(lineId, request.status());
  }

  public record ResolveRequest(@NotBlank String status) {}
}
