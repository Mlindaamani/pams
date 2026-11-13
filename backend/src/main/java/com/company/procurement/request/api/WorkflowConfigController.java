package com.company.procurement.request.api;

import com.company.procurement.request.application.WorkflowConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for database-driven status action permissions. */
@RestController
@RequestMapping("/api/v1/admin/workflow-actions")
@PreAuthorize("@permissions.has('workflow:manage')")
@Tag(name = "Workflow configuration", description = "Database-driven status action permissions")
public class WorkflowConfigController {

  private final WorkflowConfigService workflow;

  public WorkflowConfigController(WorkflowConfigService workflow) {
    this.workflow = workflow;
  }

  @GetMapping("/{entityType}")
  public List<WorkflowConfigService.WorkflowRule> rules(@PathVariable String entityType) {
    return workflow.rules(entityType);
  }

  @GetMapping("/{entityType}/statuses")
  public List<WorkflowConfigService.WorkflowStatus> statuses(@PathVariable String entityType) {
    return workflow.statuses(entityType);
  }

  @PutMapping("/{entityType}")
  public List<WorkflowConfigService.WorkflowRule> replace(
      @PathVariable String entityType, @Valid @RequestBody ReplaceRulesRequest request) {
    return workflow.replace(
        entityType,
        request.rules().stream()
            .map(
                rule ->
                    new WorkflowConfigService.WorkflowRule(
                        entityType,
                        rule.fromStatus(),
                        rule.actionCode(),
                        rule.requiredPermission()))
            .toList());
  }

  public record ReplaceRulesRequest(@NotEmpty List<@Valid RuleRequest> rules) {}

  public record RuleRequest(
      @NotBlank String fromStatus,
      @NotBlank String actionCode,
      @NotBlank String requiredPermission) {}
}
