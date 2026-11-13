package com.company.procurement.organization.application;

import com.company.procurement.audit.application.AuditEventService;
import com.company.procurement.organization.domain.Organization;
import com.company.procurement.organization.domain.OrganizationRepository;
import com.company.procurement.shared.error.ConflictException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.tenant.TenantContext;
import com.company.procurement.subscription.application.SubscriptionService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages organization profile and settings. */
@Service
public class OrganizationService {

  private final OrganizationRepository organizations;
  private final SubscriptionService subscriptions;
  private final AuditEventService audit;

  public OrganizationService(
      OrganizationRepository organizations,
      SubscriptionService subscriptions,
      AuditEventService audit) {
    this.organizations = organizations;
    this.subscriptions = subscriptions;
    this.audit = audit;
  }

  @Transactional
  public Organization create(CreateOrganizationCommand command) {
    String slug = normalizeSlug(command.slug());
    if (organizations.existsBySlug(slug)) {
      throw new ConflictException("An organization with slug '%s' already exists".formatted(slug));
    }
    Organization saved = organizations.save(new Organization(command.name(), slug));
    organizations.flush();
    subscriptions.startTrial(saved.getId(), "GROWTH", null, null);
    return saved;
  }

  @Transactional
  public Organization update(UUID id, UpdateOrganizationCommand command) {
    Organization organization = get(id);
    organization.rename(command.name());
    if (command.invoiceTolerancePercent() != null) {
      organization.changeInvoiceTolerance(command.invoiceTolerancePercent());
    }
    Organization saved = organizations.save(organization);
    audit.recordCurrentActor(
        "organization",
        saved.getId(),
        "organization_updated",
        Map.of(
            "name", saved.getName(),
            "invoiceTolerancePercent", saved.getInvoiceTolerancePercent()));
    return saved;
  }

  @Transactional(readOnly = true)
  public Organization get(UUID id) {
    UUID tenant = TenantContext.get().orElse(null);
    if (tenant != null && !tenant.equals(id)) {
      throw new ResourceNotFoundException("Organization %s not found".formatted(id));
    }
    return organizations
        .findById(id)
        .orElseThrow(
            () -> new ResourceNotFoundException("Organization %s not found".formatted(id)));
  }

  @Transactional(readOnly = true)
  public java.math.BigDecimal currentInvoiceTolerancePercent() {
    return get(TenantContext.require()).getInvoiceTolerancePercent();
  }

  @Transactional(readOnly = true)
  public List<UUID> allOrganizationIds() {
    return organizations.findAllIds();
  }

  @Transactional(readOnly = true)
  public Page<Organization> search(String name, String slug, Pageable pageable) {
    return organizations.search(TenantContext.get().orElse(null), name, slug, pageable);
  }

  private static String normalizeSlug(String slug) {
    return slug.trim().toLowerCase(Locale.ROOT);
  }
}
