package com.company.procurement.approval.api;

import com.company.procurement.approval.api.dto.DelegationRequest;
import com.company.procurement.approval.api.dto.DelegationResponse;
import com.company.procurement.approval.api.mapper.DelegationMapper;
import com.company.procurement.approval.application.ApprovalService;
import com.company.procurement.approval.application.CreateDelegationCommand;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** REST API for time-boxed approval delegation. */
@RestController
@RequestMapping("/api/v1/delegations")
@Tag(name = "Delegations", description = "Time-boxed approval delegation")
@com.company.procurement.subscription.application.RequiresFeature("approvals")
public class DelegationController {

  private final ApprovalService approvals;
  private final DelegationMapper mapper;

  public DelegationController(ApprovalService approvals, DelegationMapper mapper) {
    this.approvals = approvals;
    this.mapper = mapper;
  }

  @PostMapping
  @PreAuthorize("@permissions.has('delegation:manage')")
  public ResponseEntity<DelegationResponse> create(@Valid @RequestBody DelegationRequest request) {
    var delegation =
        approvals.createDelegation(
            new CreateDelegationCommand(
                request.delegateUserId(), request.startsAt(), request.endsAt()));
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(delegation.getId())
            .toUri();
    return ResponseEntity.created(location).body(mapper.toResponse(delegation));
  }

  @GetMapping("/mine")
  @PreAuthorize("@permissions.has('delegation:read')")
  public List<DelegationResponse> mine() {
    return approvals.myDelegations().stream().map(mapper::toResponse).toList();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@permissions.has('delegation:manage')")
  public ResponseEntity<Void> revoke(@PathVariable UUID id) {
    approvals.deactivateDelegation(id);
    return ResponseEntity.noContent().build();
  }
}
