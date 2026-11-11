package com.company.procurement.approval.api;

import com.company.procurement.approval.api.dto.ApprovalPolicyResponse;
import com.company.procurement.approval.api.dto.CreateApprovalPolicyRequest;
import com.company.procurement.approval.api.dto.PolicyStepRequest;
import com.company.procurement.approval.api.dto.UpdateApprovalPolicyRequest;
import com.company.procurement.approval.api.mapper.ApprovalPolicyMapper;
import com.company.procurement.approval.application.ApprovalService;
import com.company.procurement.approval.application.CreatePolicyCommand;
import com.company.procurement.approval.application.PolicyStepCommand;
import com.company.procurement.approval.application.UpdatePolicyCommand;
import com.company.procurement.approval.domain.ApprovalPolicy;
import com.company.procurement.shared.api.PageResponse;
import com.company.procurement.shared.web.PageRequests;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/approval-policies")
@Tag(name = "Approval policies", description = "Versioned, amount-tiered approval chains")
@PreAuthorize("@permissions.has('approval_policy:manage')")
@com.company.procurement.subscription.application.RequiresFeature("approvals")
public class ApprovalPolicyController {

  private static final Set<String> SORTABLE = Set.of("createdAt", "amountMin", "policyVersion");

  private final ApprovalService approvals;
  private final ApprovalPolicyMapper mapper;

  public ApprovalPolicyController(ApprovalService approvals, ApprovalPolicyMapper mapper) {
    this.approvals = approvals;
    this.mapper = mapper;
  }

  @PostMapping
  public ResponseEntity<ApprovalPolicyResponse> create(
      @Valid @RequestBody CreateApprovalPolicyRequest request) {
    ApprovalPolicy created =
        approvals.createPolicy(
            new CreatePolicyCommand(
                request.name(),
                request.entityType(),
                request.amountMin(),
                request.amountMax(),
                toStepCommands(request.steps())));
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
    return ResponseEntity.created(location).body(mapper.toResponse(created));
  }

  @PutMapping("/{id}")
  public ApprovalPolicyResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateApprovalPolicyRequest request) {
    ApprovalPolicy updated =
        approvals.updatePolicy(
            id,
            new UpdatePolicyCommand(
                request.name(),
                request.entityType(),
                request.amountMin(),
                request.amountMax(),
                toStepCommands(request.steps())));
    return mapper.toResponse(updated);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    approvals.deletePolicy(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}")
  public ApprovalPolicyResponse get(@PathVariable UUID id) {
    return mapper.toResponse(approvals.getPolicy(id));
  }

  @GetMapping
  public PageResponse<ApprovalPolicyResponse> list(
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt,desc") String sort) {
    Pageable pageable = PageRequests.of(page, size, sort, SORTABLE);
    return PageResponse.from(approvals.searchPolicies(active, pageable), mapper::toResponse);
  }

  private static List<PolicyStepCommand> toStepCommands(List<PolicyStepRequest> steps) {
    return steps.stream()
        .map(
            step ->
                new PolicyStepCommand(
                    step.name(),
                    step.approverType(),
                    step.approverRole(),
                    step.approverUserId(),
                    step.slaHours(),
                    step.stage()))
        .toList();
  }
}
