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

  private static final Set<String> ACTIONS =
      Set.of(
          "EDIT",
          "DELETE",
          "SUBMIT",
          "CANCEL",
          "APPROVE",
          "REJECT",
          "REQUEST_CHANGES",
          "CREATE_PO");

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
    for (WorkflowRule rule : rules) {
      if (!ACTIONS.contains(rule.actionCode())) {
        throw new BusinessRuleException("Unknown workflow action " + rule.actionCode());
      }
      if (!knownPermissions.contains(rule.requiredPermission())) {
        throw new BusinessRuleException("Unknown permission " + rule.requiredPermission());
      }
      if (rule.fromStatus() == null || rule.fromStatus().isBlank()) {
        throw new BusinessRuleException("Workflow status is required");
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
