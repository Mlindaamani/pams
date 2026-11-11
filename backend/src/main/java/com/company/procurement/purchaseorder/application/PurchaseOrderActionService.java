package com.company.procurement.purchaseorder.application;

import com.company.procurement.purchaseorder.domain.PurchaseOrder;
import com.company.procurement.request.application.WorkflowActionRules;
import com.company.procurement.shared.authorization.PermissionService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calculates purchase-order actions from database workflow rules and current permissions. */
@Service
public class PurchaseOrderActionService {

  private final PurchaseOrderService orders;
  private final WorkflowActionRules rules;
  private final PermissionService permissions;
  private final JdbcTemplate jdbc;

  public PurchaseOrderActionService(
      PurchaseOrderService orders,
      WorkflowActionRules rules,
      PermissionService permissions,
      JdbcTemplate jdbc) {
    this.orders = orders;
    this.rules = rules;
    this.permissions = permissions;
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public PurchaseOrderActionsResponse forOrder(UUID id) {
    PurchaseOrder order = orders.get(id);
    Set<String> actions = new LinkedHashSet<>();
    for (WorkflowActionRules.Rule rule : rules.find("purchase_order", order.getStatus().name())) {
      if (!permissions.has(rule.requiredPermission())) {
        continue;
      }
      if ("CREATE_INVOICE".equals(rule.actionCode()) && !hasInvoiceableRemaining(order)) {
        continue;
      }
      actions.add(rule.actionCode());
    }
    return new PurchaseOrderActionsResponse(actions.stream().sorted().toList());
  }

  private boolean hasInvoiceableRemaining(PurchaseOrder order) {
    Integer count =
        jdbc.queryForObject(
            """
            select count(*)
              from purchase_order_line pol
             where pol.organization_id = ?
               and pol.purchase_order_id = ?
               and pol.received_quantity > coalesce((
                   select sum(vil.quantity)
                     from vendor_invoice_line vil
                     join vendor_invoice vi on vi.id = vil.vendor_invoice_id
                    where vil.organization_id = pol.organization_id
                      and vil.purchase_order_line_id = pol.id
                      and vi.status in ('MATCHED', 'APPROVED', 'PARTIALLY_PAID', 'PAID')
               ), 0)
            """,
            Integer.class,
            order.getOrganizationId(),
            order.getId());
    return count != null && count > 0;
  }

  public record PurchaseOrderActionsResponse(List<String> actions) {}
}
