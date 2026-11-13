package com.company.procurement.approval.api;

import com.company.procurement.approval.api.dto.ApprovalInstanceResponse;
import com.company.procurement.approval.api.dto.ApprovalStepResponse;
import com.company.procurement.approval.api.dto.ApproveRequest;
import com.company.procurement.approval.api.dto.BulkApproveRequest;
import com.company.procurement.approval.api.dto.CommentRequest;
import com.company.procurement.approval.api.mapper.ApprovalMapper;
import com.company.procurement.approval.application.ApprovalService;
import com.company.procurement.approval.domain.ApprovalInstance;
import com.company.procurement.shared.api.BulkOperationResponse;
import com.company.procurement.shared.web.ConditionalRequests;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for approval inbox, actions and request approval timeline. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Approvals", description = "Approval inbox, actions and request approval timeline")
@com.company.procurement.subscription.application.RequiresFeature("approvals")
public class ApprovalController {

  private final ApprovalService approvals;
  private final ApprovalMapper mapper;

  public ApprovalController(ApprovalService approvals, ApprovalMapper mapper) {
    this.approvals = approvals;
    this.mapper = mapper;
  }

  @GetMapping("/approvals/inbox")
  @PreAuthorize("@permissions.has('approval:read')")
  public List<ApprovalStepResponse> inbox() {
    return mapper.toInboxResponses(approvals.inbox());
  }

  @PostMapping("/approvals/bulk-approve")
  @PreAuthorize("@permissions.has('approval:act')")
  public BulkOperationResponse bulkApprove(@Valid @RequestBody BulkApproveRequest request) {
    return new BulkOperationResponse(approvals.bulkApprove(request.ids(), request.comment()));
  }

  @PostMapping("/approvals/{stepId}/approve")
  @PreAuthorize("@permissions.has('approval:act')")
  public ResponseEntity<ApprovalInstanceResponse> approve(
      @PathVariable UUID stepId, @RequestBody(required = false) ApproveRequest request) {
    ApprovalInstance instance =
        approvals.approve(stepId, request == null ? null : request.comment());
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(instance.getVersion()))
        .body(mapper.toResponse(instance));
  }

  @PostMapping("/approvals/{stepId}/reject")
  @PreAuthorize("@permissions.has('approval:act')")
  public ResponseEntity<ApprovalInstanceResponse> reject(
      @PathVariable UUID stepId, @Valid @RequestBody CommentRequest request) {
    ApprovalInstance instance = approvals.reject(stepId, request.comment());
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(instance.getVersion()))
        .body(mapper.toResponse(instance));
  }

  @PostMapping("/approvals/{stepId}/request-changes")
  @PreAuthorize("@permissions.has('approval:act')")
  public ResponseEntity<ApprovalInstanceResponse> requestChanges(
      @PathVariable UUID stepId, @Valid @RequestBody CommentRequest request) {
    ApprovalInstance instance = approvals.requestChanges(stepId, request.comment());
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(instance.getVersion()))
        .body(mapper.toResponse(instance));
  }

  @GetMapping("/purchase-requests/{requestId}/approval")
  @PreAuthorize("@permissions.has('approval:read') or @permissions.has('purchase_request:read')")
  public ApprovalInstanceResponse timeline(@PathVariable UUID requestId) {
    return mapper.toResponse(approvals.getForRequest(requestId));
  }
}
