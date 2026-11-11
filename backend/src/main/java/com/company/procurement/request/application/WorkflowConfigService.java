package com.company.procurement.request.application;

import com.company.procurement.audit.application.AuditEventService;
import com.company.procurement.shared.error.BusinessRuleException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  private final JdbcTemplate jdbcTemplate;
  private final AuditEventService audit;

  public WorkflowConfigService(JdbcTemplate jdbcTemplate, AuditEventService audit) {
    this.jdbcTemplate = jdbcTemplate;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<WorkflowRule> rules(String entityType) {
    return jdbcTemplate.query(
        """
        select entity_type, from_status, action_code, required_permission
        from workflow_action_rule
        where entity_type = ?
        order by from_status, action_code
        """,
        (resultSet, row) ->
            new WorkflowRule(
                resultSet.getString("entity_type"),
                resultSet.getString("from_status"),
                resultSet.getString("action_code"),
                resultSet.getString("required_permission")),
        entityType);
  }

  @Transactional(readOnly = true)
  public List<WorkflowStatus> statuses(String entityType) {
    return jdbcTemplate.query(
        """
        select status_code, display_name, sort_order
        from workflow_status
        where entity_type = ?
        order by sort_order
        """,
        (resultSet, row) ->
            new WorkflowStatus(
                resultSet.getString("status_code"),
                resultSet.getString("display_name"),
                resultSet.getInt("sort_order")),
        entityType);
  }

  @Transactional
  public List<WorkflowRule> replace(String entityType, List<WorkflowRule> rules) {
    if (rules == null || rules.isEmpty()) {
      throw new BusinessRuleException("A workflow needs at least one action rule");
    }
    Set<String> knownPermissions =
        Set.copyOf(jdbcTemplate.queryForList("select code from permission", String.class));
    Set<String> knownStatuses =
        Set.copyOf(
            jdbcTemplate.queryForList(
                "select status_code from workflow_status where entity_type = ?",
                String.class,
                entityType));
    Set<String> seen = new java.util.HashSet<>();
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
    jdbcTemplate.update("delete from workflow_action_rule where entity_type = ?", entityType);
    rules.forEach(
        rule ->
            jdbcTemplate.update(
                """
                insert into workflow_action_rule
                    (entity_type, from_status, action_code, required_permission)
                values (?, ?, ?, ?)
                """,
                entityType,
                rule.fromStatus(),
                rule.actionCode(),
                rule.requiredPermission()));
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
