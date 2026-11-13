package com.company.procurement.organization.application;

import com.company.procurement.organization.domain.Department;
import com.company.procurement.organization.domain.DepartmentRepository;
import com.company.procurement.shared.actor.CurrentActorProvider;
import com.company.procurement.shared.error.ConflictException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.tenant.TenantContext;
import com.company.procurement.subscription.application.SubscriptionAccessService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages departments within a tenant. */
@Service
public class DepartmentService {

  private final DepartmentRepository departments;
  private final Clock clock;
  private final CurrentActorProvider actorProvider;
  private final SubscriptionAccessService subscriptionAccess;

  public DepartmentService(
      DepartmentRepository departments,
      Clock clock,
      CurrentActorProvider actorProvider,
      SubscriptionAccessService subscriptionAccess) {
    this.departments = departments;
    this.clock = clock;
    this.actorProvider = actorProvider;
    this.subscriptionAccess = subscriptionAccess;
  }

  @Transactional
  public Department create(DepartmentCommand command) {
    UUID organizationId = TenantContext.require();
    subscriptionAccess.assertCanAddDepartment(organizationId);
    String name = command.name().trim();
    if (departments.existsByOrganizationIdAndNameIgnoreCase(organizationId, name)) {
      throw new ConflictException("A department named '%s' already exists".formatted(name));
    }
    return departments.save(new Department(organizationId, name));
  }

  @Transactional
  public Department update(UUID id, DepartmentCommand command) {
    UUID organizationId = TenantContext.require();
    Department department = get(id);
    String name = command.name().trim();
    if (departments.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, name, id)) {
      throw new ConflictException("A department named '%s' already exists".formatted(name));
    }
    department.rename(name);
    return departments.save(department);
  }

  @Transactional
  public void delete(UUID id) {
    Department department = get(id);
    department.softDelete(clock.instant(), actorProvider.currentActor());
    departments.save(department);
  }

  @Transactional
  public int bulkDelete(List<UUID> ids) {
    ids.forEach(this::delete);
    return ids.size();
  }

  @Transactional(readOnly = true)
  public Department get(UUID id) {
    UUID organizationId = TenantContext.require();
    return departments
        .findByIdAndOrganizationId(id, organizationId)
        .orElseThrow(() -> new ResourceNotFoundException("Department %s not found".formatted(id)));
  }

  @Transactional(readOnly = true)
  public Page<Department> search(String name, Pageable pageable) {
    UUID organizationId = TenantContext.require();
    return departments.search(organizationId, name, pageable);
  }
}
