package com.company.procurement.purchaseorder.application;

import com.company.procurement.purchaseorder.domain.PurchaseOrder;
import com.company.procurement.request.application.WorkflowActionRules;
import com.company.procurement.shared.authorization.PermissionService;
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

  public PurchaseOrderActionService(
      PurchaseOrderService orders, WorkflowActionRules rules, PermissionService permissions) {
    this.orders = orders;
    this.rules = rules;
    this.permissions = permissions;
  }

  @Transactional(readOnly = true)
  public PurchaseOrderActionsResponse forOrder(UUID id) {
    PurchaseOrder order = orders.get(id);
    Set<String> actions = new LinkedHashSet<>();
    for (WorkflowActionRules.Rule rule : rules.find("purchase_order", order.getStatus().name())) {
      if (permissions.has(rule.requiredPermission())) {
        actions.add(rule.actionCode());
      }
    }
    return new PurchaseOrderActionsResponse(actions.stream().sorted().toList());
  }

  public record PurchaseOrderActionsResponse(List<String> actions) {}
}
