package com.company.procurement.platform.services;

import com.company.procurement.approval.repositories.ApprovalStepRepository;
import com.company.procurement.organization.domain.Organization;
import com.company.procurement.organization.domain.OrganizationStatus;
import com.company.procurement.organization.repositories.DepartmentRepository;
import com.company.procurement.organization.repositories.OrganizationRepository;
import com.company.procurement.organization.repositories.UserProfileRepository;
import com.company.procurement.platform.domain.TenantProvisioning;
import com.company.procurement.platform.repositories.TenantProvisioningRepository;
import com.company.procurement.request.repositories.PurchaseRequestRepository;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ConflictException;
import com.company.procurement.shared.error.PreconditionFailedException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.security.CurrentUser;
import com.company.procurement.shared.tenant.TenantContext;
import com.company.procurement.subscription.services.SubscriptionService;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates, updates, suspends and reactivates tenants. */
@Service
public class PlatformTenantService {

  private final OrganizationRepository organizations;
  private final PlatformAuditService audit;
  private final TenantStatusService statuses;
  private final SubscriptionService subscriptions;
  private final TenantProvisioningRepository provisionings;
  private final UserProfileRepository userProfiles;
  private final DepartmentRepository departments;
  private final PurchaseRequestRepository requests;
  private final ApprovalStepRepository approvalSteps;
  private final ApplicationEventPublisher events;
  private final CurrentUser currentUser;
  private final Clock clock;

  public PlatformTenantService(
      OrganizationRepository organizations,
      PlatformAuditService audit,
      TenantStatusService statuses,
      SubscriptionService subscriptions,
      TenantProvisioningRepository provisionings,
      UserProfileRepository userProfiles,
      DepartmentRepository departments,
      PurchaseRequestRepository requests,
      ApprovalStepRepository approvalSteps,
      ApplicationEventPublisher events,
      CurrentUser currentUser,
      Clock clock) {
    this.organizations = organizations;
    this.audit = audit;
    this.statuses = statuses;
    this.subscriptions = subscriptions;
    this.provisionings = provisionings;
    this.userProfiles = userProfiles;
    this.departments = departments;
    this.requests = requests;
    this.approvalSteps = approvalSteps;
    this.events = events;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Page<TenantListEntry> search(String query, OrganizationStatus status, Pageable pageable) {
    Page<Organization> page = organizations.searchAll(query, status, pageable);
    Map<UUID, TenantAdminContact> contacts = adminContacts(page.getContent());
    return page.map(
        organization -> {
          TenantAdminContact contact = contacts.get(organization.getId());
          return new TenantListEntry(
              organization,
              contact == null ? null : contact.adminName(),
              contact == null ? null : contact.adminEmail());
        });
  }

  private Map<UUID, TenantAdminContact> adminContacts(List<Organization> organizations) {
    if (organizations.isEmpty()) {
      return Map.of();
    }
    List<TenantProvisioning> contacts =
        provisionings.findByOrganizationIdIn(
            organizations.stream().map(Organization::getId).toList());
    Map<UUID, TenantAdminContact> byOrganization = new HashMap<>();
    contacts.forEach(
        contact ->
            byOrganization.put(
                contact.getOrganizationId(),
                new TenantAdminContact(contact.getAdminName(), contact.getAdminEmail())));
    return byOrganization;
  }

  @Transactional(readOnly = true)
  public Organization get(UUID id) {
    return organizations
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Tenant %s not found".formatted(id)));
  }

  @Transactional(readOnly = true)
  public TenantDetail detail(UUID id) {
    UUID previous = TenantContext.get().orElse(null);
    TenantContext.set(id);
    try {
      Organization organization = get(id);
      long users = userProfiles.countActive(id);
      long departmentCount = departments.countActive(id);
      long requestCount = requests.countTotal(id);
      long pending = approvalSteps.countCurrentPending(id);
      ProvisioningInfo provisioning =
          provisionings
              .findByOrganizationId(id)
              .map(PlatformTenantService::provisioningInfo)
              .orElse(null);
      var subscription =
          subscriptions.summaries().stream()
              .filter(summary -> summary.organizationId().equals(id))
              .findFirst()
              .orElse(null);
      return new TenantDetail(
          organization,
          subscription,
          provisioning,
          new TenantUsage(users, departmentCount, requestCount, pending));
    } finally {
      if (previous == null) {
        TenantContext.clear();
      } else {
        TenantContext.set(previous);
      }
    }
  }

  @Transactional
  public Organization create(CreateTenantCommand command) {
    String slug = normalizeSlug(command.slug());
    if (organizations.existsBySlug(slug)) {
      throw new ConflictException("A tenant with slug '%s' already exists".formatted(slug));
    }
    Organization organization = new Organization(command.name(), slug);
    if (command.invoiceTolerancePercent() != null) {
      organization.changeInvoiceTolerance(command.invoiceTolerancePercent());
    }
    Organization saved = organizations.save(organization);
    organizations.flush();
    subscriptions.startTrial(
        saved.getId(),
        command.planCode() == null ? "GROWTH" : command.planCode(),
        command.adminEmail(),
        command.adminName());
    audit.record(
        "tenant_created",
        saved.getId(),
        Map.of(
            "name",
            saved.getName(),
            "slug",
            saved.getSlug(),
            "planCode",
            command.planCode() == null ? "GROWTH" : command.planCode(),
            "adminEmail",
            command.adminEmail() == null ? "" : command.adminEmail()));
    if (command.adminEmail() != null && !command.adminEmail().isBlank()) {
      events.publishEvent(
          new TenantAdminInviteRequested(
              saved.getId(),
              command.adminEmail(),
              command.adminName() == null ? command.adminEmail() : command.adminName()));
    }
    return saved;
  }

  @Transactional
  public void inviteAdministrator(UUID id, String email, String name) {
    get(id);
    TenantProvisioning provisioning =
        provisionings
            .findByOrganizationId(id)
            .orElseGet(() -> new TenantProvisioning(id, email, name, clock.instant()));
    provisioning.updateAdministrator(email, name, clock.instant());
    provisionings.save(provisioning);
    events.publishEvent(new TenantAdminInviteRequested(id, email, name));
    audit.record("tenant_admin_invite_requested", id, Map.of("email", email));
  }

  @Transactional
  public void retryProvisioning(UUID id) {
    TenantProvisioning provisioning =
        provisionings
            .findByOrganizationId(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No provisioning request exists for tenant %s".formatted(id)));
    provisioning.markPending(clock.instant());
    provisionings.save(provisioning);
    events.publishEvent(
        new TenantAdminInviteRequested(
            id, provisioning.getAdminEmail(), provisioning.getAdminName()));
    audit.record(
        "tenant_provisioning_retried", id, Map.of("adminEmail", provisioning.getAdminEmail()));
  }

  @Transactional
  public Organization update(UUID id, long expectedVersion, UpdateTenantCommand command) {
    Organization organization = get(id);
    assertVersion(organization, expectedVersion);
    String slug = normalizeSlug(command.slug());
    if (organizations.existsBySlugAndIdNot(slug, id)) {
      throw new ConflictException("A tenant with slug '%s' already exists".formatted(slug));
    }
    organization.rename(command.name());
    organization.changeSlug(slug);
    if (command.invoiceTolerancePercent() != null) {
      organization.changeInvoiceTolerance(command.invoiceTolerancePercent());
    }
    Organization saved = organizations.save(organization);
    audit.record(
        "tenant_updated", saved.getId(), Map.of("name", saved.getName(), "slug", saved.getSlug()));
    return saved;
  }

  @Transactional
  public Organization suspend(UUID id, long expectedVersion) {
    Organization organization = get(id);
    assertVersion(organization, expectedVersion);
    organization.suspend(clock.instant(), currentActor());
    Organization saved = organizations.save(organization);
    statuses.evict(id);
    audit.record("tenant_suspended", id, Map.of("name", saved.getName()));
    return saved;
  }

  @Transactional
  public Organization activate(UUID id, long expectedVersion) {
    Organization organization = get(id);
    assertVersion(organization, expectedVersion);
    organization.activate();
    Organization saved = organizations.save(organization);
    statuses.evict(id);
    audit.record("tenant_activated", id, Map.of("name", saved.getName()));
    return saved;
  }

  private String currentActor() {
    return currentUser.username();
  }

  private static void assertVersion(Organization organization, long expectedVersion) {
    if (expectedVersion >= 0 && organization.getVersion() != expectedVersion) {
      throw new PreconditionFailedException(
          "The tenant was modified by another operation; reload and retry");
    }
  }

  private static String normalizeSlug(String slug) {
    String value = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
    if (value.isBlank()) {
      throw new BusinessRuleException("Tenant slug is required");
    }
    return value;
  }

  private static ProvisioningInfo provisioningInfo(TenantProvisioning provisioning) {
    return new ProvisioningInfo(
        provisioning.getAdminEmail(),
        provisioning.getAdminName(),
        provisioning.getStatus().name(),
        provisioning.getLastError(),
        provisioning.getUpdatedAt());
  }

  public record TenantListEntry(Organization organization, String adminName, String adminEmail) {}

  private record TenantAdminContact(String adminName, String adminEmail) {}

  public record TenantDetail(
      Organization organization,
      com.company.procurement.subscription.services.SubscriptionService.SubscriptionSummary
          subscription,
      ProvisioningInfo provisioning,
      TenantUsage usage) {}

  public record ProvisioningInfo(
      String adminEmail,
      String adminName,
      String status,
      String lastError,
      java.time.Instant updatedAt) {}

  public record TenantUsage(long users, long departments, long requests, long pendingApprovals) {}
}
