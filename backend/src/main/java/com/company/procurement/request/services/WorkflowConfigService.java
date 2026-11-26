package com.company.procurement.request.services;

import com.company.procurement.access.repositories.PermissionRepository;
import com.company.procurement.audit.services.AuditEventService;
import com.company.procurement.request.domain.WorkflowActionRule;
import com.company.procurement.request.repositories.WorkflowActionRuleRepository;
import com.company.procurement.request.repositories.WorkflowStatusRepository;
import com.company.procurement.shared.error.BusinessRuleException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exposes request workflow action and policy configuration. */
@Service
public class WorkflowConfigService {

  private static final Map<String, Set<String>> ACTIONS_BY_ENTITY =
      Map.of(
          "purchase_request",
          Set.of(
              "EDIT",
              "DELETE",
              "SUBMIT",
              "CANCEL",
              "APPROVE",
              "REJECT",
              "REQUEST_CHANGES",
              "CREATE_PO"),
          "vendor_payment",
          Set.of("APPROVE", "CANCEL", "MARK_PAID"),
          "purchase_order",
          Set.of(
              "EDIT",
              "SUBMIT_APPROVAL",
              "ISSUE",
              "CANCEL",
              "APPROVE",
              "REJECT",
              "REQUEST_CHANGES",
              "RECEIVE",
              "CREATE_INVOICE"));

  private final WorkflowActionRuleRepository actionRules;
  private final WorkflowStatusRepository workflowStatuses;
  private final PermissionRepository permissions;
  private final AuditEventService audit;

  public WorkflowConfigService(
      WorkflowActionRuleRepository actionRules,
      WorkflowStatusRepository workflowStatuses,
      PermissionRepository permissions,
      AuditEventService audit) {
    this.actionRules = actionRules;
    this.workflowStatuses = workflowStatuses;
    this.permissions = permissions;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<WorkflowRule> rules(String entityType) {
    return actionRules.findByEntityType(entityType).stream()
        .map(
            rule ->
                new WorkflowRule(
                    rule.getId().getEntityType(),
                    rule.getId().getFromStatus(),
                    rule.getId().getActionCode(),
                    rule.getRequiredPermission()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<WorkflowStatus> statuses(String entityType) {
    return workflowStatuses.findByEntityType(entityType).stream()
        .map(
            status ->
                new WorkflowStatus(
                    status.getId().getStatusCode(), status.getDisplayName(), status.getSortOrder()))
        .toList();
  }

  @Transactional
  public List<WorkflowRule> replace(String entityType, List<WorkflowRule> rules) {
    if (rules == null || rules.isEmpty()) {
      throw new BusinessRuleException("A workflow needs at least one action rule");
    }
    Set<String> knownPermissions = permissions.findAllCodes();
    Set<String> knownStatuses = workflowStatuses.findStatusCodesByEntityType(entityType);
    Set<String> seen = new HashSet<>();
    Set<String> allowedActions = ACTIONS_BY_ENTITY.getOrDefault(entityType, Set.of());
    for (WorkflowRule rule : rules) {
      if (!allowedActions.contains(rule.actionCode())) {
        throw new BusinessRuleException(
            "Workflow action %s is not available for %s".formatted(rule.actionCode(), entityType));
      }
      if (!knownPermissions.contains(rule.requiredPermission())) {
        throw new BusinessRuleException("Unknown permission " + rule.requiredPermission());
      }
      if (rule.fromStatus() == null || rule.fromStatus().isBlank()) {
        throw new BusinessRuleException("Workflow status is required");
      }
      if (!knownStatuses.contains(rule.fromStatus())) {
        throw new BusinessRuleException(
            "Unknown workflow status %s for %s".formatted(rule.fromStatus(), entityType));
      }
      if (!seen.add(rule.fromStatus() + "|" + rule.actionCode())) {
        throw new BusinessRuleException(
            "Duplicate workflow action %s for status %s"
                .formatted(rule.actionCode(), rule.fromStatus()));
      }
    }
    actionRules.deleteByEntityType(entityType);
    actionRules.saveAll(
        rules.stream()
            .map(
                rule ->
                    new WorkflowActionRule(
                        entityType,
                        rule.fromStatus(),
                        rule.actionCode(),
                        rule.requiredPermission()))
            .toList());
    audit.recordCurrentActor(
        "workflow_action_rule",
        UUID.nameUUIDFromBytes(entityType.getBytes(StandardCharsets.UTF_8)),
        "workflow_actions_updated",
        Map.of("entityType", entityType, "rules", rules.size()));
    return rules(entityType);
  }

  public record WorkflowRule(
      String entityType, String fromStatus, String actionCode, String requiredPermission) {}

  public record WorkflowStatus(String statusCode, String displayName, int sortOrder) {}
}
