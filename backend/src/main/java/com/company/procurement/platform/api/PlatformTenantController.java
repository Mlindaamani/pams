package com.company.procurement.platform.api;

import com.company.procurement.organization.api.dto.OrganizationResponse;
import com.company.procurement.organization.api.mapper.OrganizationMapper;
import com.company.procurement.organization.domain.Organization;
import com.company.procurement.organization.domain.OrganizationStatus;
import com.company.procurement.platform.api.dto.CreateTenantRequest;
import com.company.procurement.platform.api.dto.InviteTenantAdminRequest;
import com.company.procurement.platform.api.dto.UpdateTenantRequest;
import com.company.procurement.platform.application.CreateTenantCommand;
import com.company.procurement.platform.application.PlatformTenantService;
import com.company.procurement.platform.application.UpdateTenantCommand;
import com.company.procurement.shared.api.PageResponse;
import com.company.procurement.shared.web.ConditionalRequests;
import com.company.procurement.shared.web.PageRequests;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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

@RestController
@RequestMapping("/api/v1/platform/tenants")
@Tag(name = "Platform tenants", description = "Cross-tenant administration")
public class PlatformTenantController {

  private static final Set<String> SORTABLE = Set.of("createdAt", "name", "slug", "status");

  private final PlatformTenantService tenants;
  private final OrganizationMapper mapper;

  public PlatformTenantController(PlatformTenantService tenants, OrganizationMapper mapper) {
    this.tenants = tenants;
    this.mapper = mapper;
  }

  @GetMapping
  @PreAuthorize("@permissions.has('platform:tenant_read')")
  public PageResponse<OrganizationResponse> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) OrganizationStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt,desc") String sort) {
    Pageable pageable = PageRequests.of(page, size, sort, SORTABLE);
    return PageResponse.from(tenants.search(q, status, pageable), mapper::toResponse);
  }

  @GetMapping("/{id}")
  @PreAuthorize("@permissions.has('platform:tenant_read')")
  public ResponseEntity<OrganizationResponse> get(@PathVariable UUID id) {
    Organization tenant = tenants.get(id);
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(tenant.getVersion()))
        .body(mapper.toResponse(tenant));
  }

  @GetMapping("/{id}/detail")
  @PreAuthorize("@permissions.has('platform:tenant_read')")
  public TenantDetailResponse detail(@PathVariable UUID id) {
    PlatformTenantService.TenantDetail detail = tenants.detail(id);
    return new TenantDetailResponse(
        mapper.toResponse(detail.organization()),
        detail.subscription(),
        detail.provisioning(),
        detail.usage());
  }

  @PostMapping
  @PreAuthorize("@permissions.has('platform:tenant_manage')")
  public ResponseEntity<OrganizationResponse> create(
      @Valid @RequestBody CreateTenantRequest request) {
    Organization created =
        tenants.create(
            new CreateTenantCommand(
                request.name(),
                request.slug(),
                request.invoiceTolerancePercent(),
                request.adminEmail(),
                request.adminName(),
                request.planCode()));
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/platform/tenants/{id}")
            .buildAndExpand(created.getId())
            .toUri();
    return ResponseEntity.created(location)
        .eTag(ConditionalRequests.etag(created.getVersion()))
        .body(mapper.toResponse(created));
  }

  @PutMapping("/{id}")
  @PreAuthorize("@permissions.has('platform:tenant_manage')")
  public ResponseEntity<OrganizationResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateTenantRequest request,
      HttpServletRequest httpRequest) {
    Organization updated =
        tenants.update(
            id,
            ConditionalRequests.requireIfMatch(httpRequest),
            new UpdateTenantCommand(
                request.name(), request.slug(), request.invoiceTolerancePercent()));
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(updated.getVersion()))
        .body(mapper.toResponse(updated));
  }

  @PostMapping("/{id}/administrator-invite")
  @PreAuthorize("@permissions.has('platform:tenant_manage')")
  public org.springframework.http.ResponseEntity<Void> inviteAdministrator(
      @PathVariable UUID id, @Valid @RequestBody InviteTenantAdminRequest request) {
    tenants.inviteAdministrator(id, request.email(), request.name());
    return org.springframework.http.ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/provisioning/retry")
  @PreAuthorize("@permissions.has('platform:tenant_manage')")
  public org.springframework.http.ResponseEntity<Void> retryProvisioning(@PathVariable UUID id) {
    tenants.retryProvisioning(id);
    return org.springframework.http.ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/suspend")
  @PreAuthorize("@permissions.has('platform:tenant_manage')")
  public ResponseEntity<OrganizationResponse> suspend(
      @PathVariable UUID id, HttpServletRequest httpRequest) {
    Organization updated = tenants.suspend(id, ConditionalRequests.requireIfMatch(httpRequest));
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(updated.getVersion()))
        .body(mapper.toResponse(updated));
  }

  @PostMapping("/{id}/activate")
  @PreAuthorize("@permissions.has('platform:tenant_manage')")
  public ResponseEntity<OrganizationResponse> activate(
      @PathVariable UUID id, HttpServletRequest httpRequest) {
    Organization updated = tenants.activate(id, ConditionalRequests.requireIfMatch(httpRequest));
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(updated.getVersion()))
        .body(mapper.toResponse(updated));
  }

  public record TenantDetailResponse(
      OrganizationResponse tenant,
      com.company.procurement.subscription.application.SubscriptionService.SubscriptionSummary
          subscription,
      PlatformTenantService.ProvisioningInfo provisioning,
      PlatformTenantService.TenantUsage usage) {}
}
