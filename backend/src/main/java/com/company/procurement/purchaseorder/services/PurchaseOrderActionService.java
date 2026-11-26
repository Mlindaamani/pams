package com.company.procurement.purchaseorder.services;

import com.company.procurement.purchaseorder.domain.PurchaseOrder;
import com.company.procurement.purchaseorder.repositories.PurchaseOrderInvoicingPort;
import com.company.procurement.request.services.WorkflowActionRules;
import com.company.procurement.shared.services.PermissionService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calculates purchase-order actions from database workflow rules and current permissions. */
@Service
public class PurchaseOrderActionService {

  private final PurchaseOrderService orders;
  private final WorkflowActionRules rules;
  private final PermissionService permissions;
  private final PurchaseOrderInvoicingPort invoicing;

  public PurchaseOrderActionService(
      PurchaseOrderService orders,
      WorkflowActionRules rules,
      PermissionService permissions,
      PurchaseOrderInvoicingPort invoicing) {
    this.orders = orders;
    this.rules = rules;
    this.permissions = permissions;
    this.invoicing = invoicing;
  }

  @Transactional(readOnly = true)
  public PurchaseOrderActionsResponse forOrder(UUID id) {
    PurchaseOrder order = orders.get(id);
    Set<String> actions = new LinkedHashSet<>();
    for (WorkflowActionRules.Rule rule : rules.find("purchase_order", order.getStatus().name())) {
      if (!permissions.has(rule.requiredPermission())) {
        continue;
      }
      if ("CREATE_INVOICE".equals(rule.actionCode()) && !invoicing.hasInvoiceableRemaining(order)) {
        continue;
      }
      actions.add(rule.actionCode());
    }
    return new PurchaseOrderActionsResponse(actions.stream().sorted().toList());
  }

  public record PurchaseOrderActionsResponse(List<String> actions) {}
}
