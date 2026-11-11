package com.company.procurement.request.application;

import com.company.procurement.approval.application.ApprovalService;
import com.company.procurement.audit.application.AuditEventService;
import com.company.procurement.budget.application.BudgetService;
import com.company.procurement.budget.domain.BudgetExceededException;
import com.company.procurement.catalog.application.CatalogItemService;
import com.company.procurement.organization.application.DepartmentService;
import com.company.procurement.request.domain.PurchaseRequest;
import com.company.procurement.request.domain.PurchaseRequestFilter;
import com.company.procurement.request.domain.PurchaseRequestLineDraft;
import com.company.procurement.request.domain.PurchaseRequestRepository;
import com.company.procurement.request.domain.RequestStatus;
import com.company.procurement.shared.actor.CurrentActorProvider;
import com.company.procurement.shared.authorization.PermissionService;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.PreconditionFailedException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.security.CurrentUser;
import com.company.procurement.shared.tenant.TenantContext;
import com.company.procurement.subscription.application.SubscriptionAccessService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseRequestService {

  private final PurchaseRequestRepository requests;
  private final DepartmentService departments;
  private final CatalogItemService catalogItems;
  private final BudgetService budgets;
  private final ApprovalService approvals;
  private final AuditEventService audit;
  private final CurrentUser currentUser;
  private final CurrentActorProvider actorProvider;
  private final PermissionService permissions;
  private final SubscriptionAccessService subscriptionAccess;
  private final Clock clock;

  public PurchaseRequestService(
      PurchaseRequestRepository requests,
      DepartmentService departments,
      CatalogItemService catalogItems,
      BudgetService budgets,
      ApprovalService approvals,
      AuditEventService audit,
      CurrentUser currentUser,
      CurrentActorProvider actorProvider,
      PermissionService permissions,
      SubscriptionAccessService subscriptionAccess,
      Clock clock) {
    this.requests = requests;
    this.departments = departments;
    this.catalogItems = catalogItems;
    this.budgets = budgets;
    this.approvals = approvals;
    this.audit = audit;
    this.currentUser = currentUser;
    this.actorProvider = actorProvider;
    this.permissions = permissions;
    this.subscriptionAccess = subscriptionAccess;
    this.clock = clock;
  }

  @Transactional
  public PurchaseRequest create(PurchaseRequestCommand command) {
    UUID organizationId = TenantContext.require();
    departments.get(command.departmentId());
    validateCatalogReferences(command.lines());
    PurchaseRequest request =
        new PurchaseRequest(
            organizationId,
            currentUser.subject(),
            currentUser.username(),
            command.departmentId(),
            command.title(),
            command.justification(),
            command.neededBy(),
            command.currency(),
            command.budgetExceptionRequested());
    request.replaceLines(toDrafts(command.lines()));
    return requests.save(request);
  }

  @Transactional
  public PurchaseRequest update(UUID id, PurchaseRequestCommand command, long expectedVersion) {
    PurchaseRequest request = requireEditableByOwner(id);
    assertVersion(request, expectedVersion);
    departments.get(command.departmentId());
    validateCatalogReferences(command.lines());
    request.updateHeader(
        command.title(),
        command.justification(),
        command.neededBy(),
        command.departmentId(),
        command.currency());
    request.replaceLines(toDrafts(command.lines()));
    return requests.save(request);
  }

  @Transactional
  public void delete(UUID id, long expectedVersion) {
    PurchaseRequest request = requestForOwner(id);
    assertVersion(request, expectedVersion);
    if (request.getStatus() != RequestStatus.DRAFT) {
      throw new BusinessRuleException("Only draft requests can be deleted");
    }
    request.softDelete(clock.instant(), actorProvider.currentActor());
    requests.save(request);
  }

  @Transactional
  public PurchaseRequest submit(UUID id, long expectedVersion) {
    UUID organizationId = TenantContext.require();
    subscriptionAccess.assertCanSubmitRequest(organizationId);
    PurchaseRequest request = requireEditableByOwner(id);
    assertVersion(request, expectedVersion);

    if (request.isBudgetReserved() && request.getBudgetId() != null) {
      budgets.release(organizationId, request.getBudgetId(), request.getReservedAmount());
      request.clearBudgetReservation();
    }

    if (request.getTotalAmount().signum() > 0) {
      int fiscalYear = LocalDate.now(clock).getYear();
      try {
        UUID budgetId =
            budgets.reserveForDepartment(
                organizationId,
                request.getDepartmentId(),
                fiscalYear,
                request.getTotalAmount(),
                request.getCurrency());
        request.markBudgetReserved(budgetId, request.getTotalAmount());
      } catch (BudgetExceededException ex) {
        if (request.isBudgetExceptionRequested()) {
          request.markBudgetException();
        } else {
          throw ex;
        }
      }
    }

    ApprovalService.ApprovalStartResult approval =
        approvals.startApproval(
            request.getId(),
            request.getRequesterSubject(),
            request.getCurrency(),
            request.getTotalAmount(),
            request.isBudgetException());
    request.attachApprovalPolicy(approval.policyId(), approval.policyVersion());
    request.submit(clock.instant());
    request.advanceToInApproval();
    PurchaseRequest saved = requests.save(request);
    audit.recordCurrentActor(
        "purchase_request",
        saved.getId(),
        "request_submitted",
        Map.of(
            "policyId", approval.policyId().toString(),
            "policyVersion", approval.policyVersion(),
            "total", saved.getTotalAmount().toPlainString()));
    return saved;
  }

  @Transactional
  public PurchaseRequest cancel(UUID id, long expectedVersion) {
    UUID organizationId = TenantContext.require();
    PurchaseRequest request = requestForOwner(id);
    assertVersion(request, expectedVersion);
    if (request.isBudgetReserved() && request.getBudgetId() != null) {
      budgets.release(organizationId, request.getBudgetId(), request.getReservedAmount());
      request.clearBudgetReservation();
    }
    approvals.cancelForRequest(id);
    request.cancel();
    PurchaseRequest saved = requests.save(request);
    audit.recordCurrentActor("purchase_request", saved.getId(), "request_cancelled", Map.of());
    return saved;
  }

  @Transactional
  public void markApproved(UUID id) {
    PurchaseRequest request = requestDetail(id);
    if (request.getStatus() == RequestStatus.IN_APPROVAL) {
      request.approve();
      requests.save(request);
    }
  }

  @Transactional
  public void markRejected(UUID id) {
    PurchaseRequest request = requestDetail(id);
    if (request.getStatus() != RequestStatus.IN_APPROVAL) {
      return;
    }
    if (request.isBudgetReserved() && request.getBudgetId() != null) {
      budgets.release(TenantContext.require(), request.getBudgetId(), request.getReservedAmount());
      request.clearBudgetReservation();
    }
    request.reject();
    requests.save(request);
  }

  @Transactional(readOnly = true)
  public PurchaseRequestSnapshot snapshotForPurchaseOrder(UUID id) {
    PurchaseRequest request = requestDetail(id);
    if (request.getStatus() != RequestStatus.APPROVED) {
      throw new BusinessRuleException("Only approved requests can be converted to purchase orders");
    }
    return new PurchaseRequestSnapshot(
        request.getId(),
        request.getRequesterSubject(),
        request.getRequesterName(),
        request.getDepartmentId(),
        request.getCurrency(),
        request.getTotalAmount(),
        request.getBudgetId(),
        request.getReservedAmount(),
        request.getLines().stream()
            .map(
                line ->
                    new PurchaseRequestLineSnapshot(
                        line.getDescription(),
                        line.getQuantity(),
                        line.getUnit(),
                        line.getUnitPrice(),
                        line.getLineTotal()))
            .toList());
  }

  @Transactional
  public void markConvertedToPurchaseOrder(UUID id) {
    PurchaseRequest request = requestDetail(id);
    request.convertToPurchaseOrder();
    requests.save(request);
  }

  @Transactional
  public void reopenAfterPurchaseOrderCancellation(UUID id) {
    PurchaseRequest request = requestDetail(id);
    if (request.getStatus() != RequestStatus.CONVERTED_TO_PO) return;
    request.reopenAfterPurchaseOrderCancellation();
    PurchaseRequest saved = requests.save(request);
    audit.recordCurrentActor(
        "purchase_request", saved.getId(), "request_reopened_after_po_cancel", Map.of());
  }

  @Transactional
  public void markChangesRequested(UUID id) {
    PurchaseRequest request = requestDetail(id);
    if (request.getStatus() == RequestStatus.IN_APPROVAL) {
      request.requestChanges();
      requests.save(request);
    }
  }

  @Transactional(readOnly = true)
  public PurchaseRequest get(UUID id) {
    PurchaseRequest request = requestDetail(id);
    assertCanView(request);
    return request;
  }

  @Transactional(readOnly = true)
  public Page<PurchaseRequest> search(PurchaseRequestFilter filter, Pageable pageable) {
    UUID organizationId = TenantContext.require();
    PurchaseRequestFilter effectiveFilter =
        canViewAll()
            ? filter
            : new PurchaseRequestFilter(
                filter.status(),
                filter.departmentId(),
                currentUser.subject(),
                filter.createdFrom(),
                filter.createdTo(),
                filter.amountMin(),
                filter.amountMax(),
                filter.titleContains());
    return requests.search(organizationId, effectiveFilter, pageable);
  }

  @Transactional(readOnly = true)
  public PurchaseRequest requireVisible(UUID id) {
    PurchaseRequest request = requestDetail(id);
    assertCanView(request);
    return request;
  }

  @Transactional
  public PurchaseRequest requireEditableByOwner(UUID id) {
    PurchaseRequest request = requestDetail(id);
    assertOwnedBy(request);
    request.assertEditable();
    return request;
  }

  @Transactional(readOnly = true)
  public PurchaseRequest requestDetail(UUID id) {
    UUID organizationId = TenantContext.require();
    return requests
        .findDetailByIdAndOrganizationId(id, organizationId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Purchase request %s not found".formatted(id)));
  }

  private PurchaseRequest requestForOwner(UUID id) {
    PurchaseRequest request = requestDetail(id);
    assertOwnedBy(request);
    return request;
  }

  private void assertOwnedBy(PurchaseRequest request) {
    if (!request.isOwnedBy(currentUser.subject())) {
      throw new ResourceNotFoundException(
          "Purchase request %s not found".formatted(request.getId()));
    }
  }

  private void assertCanView(PurchaseRequest request) {
    if (request.isOwnedBy(currentUser.subject()) || canViewAll()) {
      return;
    }
    throw new ResourceNotFoundException("Purchase request %s not found".formatted(request.getId()));
  }

  private boolean canViewAll() {
    return permissions.has("purchase_request:view_all");
  }

  private static void assertVersion(PurchaseRequest request, long expectedVersion) {
    if (expectedVersion >= 0 && request.getVersion() != expectedVersion) {
      throw new PreconditionFailedException(
          "The request was modified by another operation; reload and retry");
    }
  }

  private void validateCatalogReferences(List<PurchaseRequestLineCommand> lines) {
    if (lines == null) {
      return;
    }
    lines.stream()
        .map(PurchaseRequestLineCommand::catalogItemId)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .forEach(catalogItems::get);
  }

  private static List<PurchaseRequestLineDraft> toDrafts(List<PurchaseRequestLineCommand> lines) {
    if (lines == null) {
      return List.of();
    }
    return lines.stream()
        .map(
            line ->
                new PurchaseRequestLineDraft(
                    line.description(),
                    line.quantity(),
                    line.unit(),
                    line.unitPrice(),
                    line.catalogItemId()))
        .toList();
  }
}
