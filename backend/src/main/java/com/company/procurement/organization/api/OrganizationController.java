package com.company.procurement.organization.api;

import com.company.procurement.organization.api.dto.CreateOrganizationRequest;
import com.company.procurement.organization.api.dto.OrganizationResponse;
import com.company.procurement.organization.api.dto.UpdateOrganizationRequest;
import com.company.procurement.organization.api.mapper.OrganizationMapper;
import com.company.procurement.organization.application.CreateOrganizationCommand;
import com.company.procurement.organization.application.OrganizationService;
import com.company.procurement.organization.application.UpdateOrganizationCommand;
import com.company.procurement.organization.domain.Organization;
import com.company.procurement.shared.api.PageResponse;
import com.company.procurement.shared.web.PageRequests;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** REST API for organization registry (tenant root). */
@RestController
@Tag(name = "Organizations", description = "Organization registry (tenant root)")
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

  private static final Set<String> SORTABLE = Set.of("createdAt", "name", "slug");

  private final OrganizationService organizations;
  private final OrganizationMapper mapper;

  public OrganizationController(OrganizationService organizations, OrganizationMapper mapper) {
    this.organizations = organizations;
    this.mapper = mapper;
  }

  @PreAuthorize("@permissions.has('organization:update')")
  @PostMapping
  public ResponseEntity<OrganizationResponse> create(
      @Valid @RequestBody CreateOrganizationRequest request) {
    Organization created =
        organizations.create(new CreateOrganizationCommand(request.name(), request.slug()));
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
    return ResponseEntity.created(location).body(mapper.toResponse(created));
  }

  @PreAuthorize("@permissions.has('organization:read')")
  @GetMapping("/{id}")
  public OrganizationResponse get(@PathVariable UUID id) {
    return mapper.toResponse(organizations.get(id));
  }

  @PreAuthorize("@permissions.has('organization:read')")
  @GetMapping
  public PageResponse<OrganizationResponse> list(
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String slug,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt,desc") String sort) {
    Pageable pageable = PageRequests.of(page, size, sort, SORTABLE);
    return PageResponse.from(organizations.search(name, slug, pageable), mapper::toResponse);
  }

  @PreAuthorize("@permissions.has('organization:update')")
  @PutMapping("/{id}")
  public OrganizationResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateOrganizationRequest request) {
    return mapper.toResponse(
        organizations.update(
            id, new UpdateOrganizationCommand(request.name(), request.invoiceTolerancePercent())));
  }
}
