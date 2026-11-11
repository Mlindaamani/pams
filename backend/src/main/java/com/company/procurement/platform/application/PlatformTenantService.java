package com.company.procurement.platform.application;

import com.company.procurement.organization.domain.Organization;
import com.company.procurement.organization.domain.OrganizationRepository;
import com.company.procurement.organization.domain.OrganizationStatus;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ConflictException;
import com.company.procurement.shared.error.PreconditionFailedException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.security.CurrentUser;
import com.company.procurement.subscription.application.SubscriptionService;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformTenantService {

  private final OrganizationRepository organizations;
  private final PlatformAuditService audit;
  private final TenantStatusService statuses;
  private final SubscriptionService subscriptions;
  private final ApplicationEventPublisher events;
  private final JdbcTemplate jdbc;
  private final CurrentUser currentUser;
  private final Clock clock;

  public PlatformTenantService(
      OrganizationRepository organizations,
      PlatformAuditService audit,
      TenantStatusService statuses,
      SubscriptionService subscriptions,
      ApplicationEventPublisher events,
      JdbcTemplate jdbc,
      CurrentUser currentUser,
      Clock clock) {
    this.organizations = organizations;
    this.audit = audit;
    this.statuses = statuses;
    this.subscriptions = subscriptions;
    this.events = events;
    this.jdbc = jdbc;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Page<Organization> search(String query, OrganizationStatus status, Pageable pageable) {
    return organizations.searchAll(query, status, pageable);
  }

  @Transactional(readOnly = true)
  public Organization get(UUID id) {
    return organizations
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Tenant %s not found".formatted(id)));
  }

  @Transactional(readOnly = true)
  public TenantDetail detail(UUID id) {
    Organization organization = get(id);
    setTenant(id);
    long users =
        count(
            "select count(*) from user_profile where organization_id = ? and deleted_at is null and active = true",
            id);
    long departments =
        count(
            "select count(*) from department where organization_id = ? and deleted_at is null", id);
    long requests = count("select count(*) from purchase_request where organization_id = ?", id);
    long pending =
        count(
            """
            select count(*)
              from approval_step s
              join approval_instance ai on ai.id = s.instance_id
             where s.organization_id = ?
               and ai.current_sequence = s.sequence
               and s.status = 'PENDING'
            """,
            id);
    ProvisioningInfo provisioning =
        jdbc
            .query(
                """
                select admin_email, admin_name, status, last_error, updated_at
                  from tenant_provisioning
                 where organization_id = ?
                """,
                (rs, row) ->
                    new ProvisioningInfo(
                        rs.getString("admin_email"),
                        rs.getString("admin_name"),
                        rs.getString("status"),
                        rs.getString("last_error"),
                        rs.getTimestamp("updated_at") == null
                            ? null
                            : rs.getTimestamp("updated_at").toInstant()),
                id)
            .stream()
            .findFirst()
            .orElse(null);
    var subscription =
        subscriptions.summaries().stream()
            .filter(summary -> summary.organizationId().equals(id))
            .findFirst()
            .orElse(null);
    setTenant(null);
    return new TenantDetail(
        organization,
        subscription,
        provisioning,
        new TenantUsage(users, departments, requests, pending));
  }

  private void setTenant(UUID organizationId) {
    jdbc.queryForObject(
        "select set_config('app.current_org', ?, false)",
        String.class,
        organizationId == null ? "" : organizationId.toString());
  }

  private long count(String sql, Object... args) {
    Long value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0 : value;
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
    jdbc.update(
        """
        insert into tenant_provisioning
            (organization_id, admin_email, admin_name, status, created_at, updated_at)
        values (?, ?, ?, 'PENDING', now(), now())
        on conflict (organization_id) do update
           set admin_email = excluded.admin_email,
               admin_name = excluded.admin_name,
               status = 'PENDING',
               last_error = null,
               updated_at = now()
        """,
        id,
        email,
        name);
    events.publishEvent(new TenantAdminInviteRequested(id, email, name));
    audit.record("tenant_admin_invite_requested", id, Map.of("email", email));
  }

  @Transactional
  public void retryProvisioning(UUID id) {
    var rows =
        jdbc.queryForList(
            "select admin_email, admin_name from tenant_provisioning where organization_id = ?",
            id);
    if (rows.isEmpty()) {
      throw new ResourceNotFoundException(
          "No provisioning request exists for tenant %s".formatted(id));
    }
    String email = (String) rows.get(0).get("admin_email");
    String name = (String) rows.get(0).get("admin_name");
    jdbc.update(
        "update tenant_provisioning set status = 'PENDING', last_error = null, updated_at = now() where organization_id = ?",
        id);
    events.publishEvent(new TenantAdminInviteRequested(id, email, name));
    audit.record("tenant_provisioning_retried", id, Map.of("adminEmail", email));
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

  public record TenantDetail(
      Organization organization,
      com.company.procurement.subscription.application.SubscriptionService.SubscriptionSummary
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
