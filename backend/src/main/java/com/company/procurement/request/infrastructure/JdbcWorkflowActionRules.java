package com.company.procurement.request.infrastructure;

import com.company.procurement.request.application.WorkflowActionRules;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcWorkflowActionRules implements WorkflowActionRules {

  private final JdbcTemplate jdbcTemplate;

  public JdbcWorkflowActionRules(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public List<Rule> find(String entityType, String fromStatus) {
    return jdbcTemplate.query(
        """
        select action_code, required_permission
        from workflow_action_rule
        where entity_type = ? and from_status = ?
        order by action_code
        """,
        (resultSet, row) ->
            new Rule(
                resultSet.getString("action_code"), resultSet.getString("required_permission")),
        entityType,
        fromStatus);
  }
}
