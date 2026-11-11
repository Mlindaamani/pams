package com.company.procurement.request.api;

import com.company.procurement.request.api.dto.PurchaseRequestRequest;
import com.company.procurement.request.api.dto.PurchaseRequestResponse;
import com.company.procurement.request.api.mapper.PurchaseRequestMapper;
import com.company.procurement.request.application.PurchaseRequestActionService;
import com.company.procurement.request.application.PurchaseRequestActionsResponse;
import com.company.procurement.request.application.PurchaseRequestCommand;
import com.company.procurement.request.application.PurchaseRequestLineCommand;
import com.company.procurement.request.application.PurchaseRequestService;
import com.company.procurement.request.domain.PurchaseRequest;
import com.company.procurement.request.domain.PurchaseRequestFilter;
import com.company.procurement.request.domain.RequestStatus;
import com.company.procurement.shared.api.PageResponse;
import com.company.procurement.shared.web.ConditionalRequests;
import com.company.procurement.shared.web.PageRequests;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
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
@RequestMapping("/api/v1/purchase-requests")
@Tag(
    name = "Purchase requests",
    description = "Draft requests, totals, submission and budget reservation")
@com.company.procurement.subscription.application.RequiresFeature("requests")
public class PurchaseRequestController {

  private static final Set<String> SORTABLE =
      Set.of("createdAt", "totalAmount", "status", "neededBy", "submittedAt");

  private final PurchaseRequestService requests;
  private final PurchaseRequestActionService actionService;
  private final PurchaseRequestMapper mapper;

  public PurchaseRequestController(
      PurchaseRequestService requests,
      PurchaseRequestActionService actionService,
      PurchaseRequestMapper mapper) {
    this.requests = requests;
    this.actionService = actionService;
    this.mapper = mapper;
  }

  @PostMapping
  @PreAuthorize("@permissions.has('purchase_request:create')")
  public ResponseEntity<PurchaseRequestResponse> create(
      @Valid @RequestBody PurchaseRequestRequest request) {
    PurchaseRequest created = requests.create(toCommand(request));
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
    return ResponseEntity.created(location)
        .eTag(ConditionalRequests.etag(created.getVersion()))
        .body(mapper.toResponse(created));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@permissions.has('purchase_request:read')")
  public ResponseEntity<PurchaseRequestResponse> get(@PathVariable UUID id) {
    PurchaseRequest request = requests.get(id);
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(request.getVersion()))
        .body(mapper.toResponse(request));
  }

  @GetMapping
  @PreAuthorize("@permissions.has('purchase_request:read')")
  public PageResponse<PurchaseRequestResponse> list(
      @RequestParam(required = false) RequestStatus status,
      @RequestParam(required = false) UUID departmentId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Instant createdFrom,
      @RequestParam(required = false) Instant createdTo,
      @RequestParam(required = false) BigDecimal amountMin,
      @RequestParam(required = false) BigDecimal amountMax,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt,desc") String sort) {
    Pageable pageable = PageRequests.of(page, size, sort, SORTABLE);
    PurchaseRequestFilter filter =
        new PurchaseRequestFilter(
            status, departmentId, null, createdFrom, createdTo, amountMin, amountMax, q);
    return PageResponse.from(requests.search(filter, pageable), mapper::toSummary);
  }

  @PutMapping("/{id}")
  @PreAuthorize("@permissions.has('purchase_request:update')")
  public ResponseEntity<PurchaseRequestResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody PurchaseRequestRequest request,
      HttpServletRequest httpRequest) {
    long expectedVersion = ConditionalRequests.requireIfMatch(httpRequest);
    PurchaseRequest updated = requests.update(id, toCommand(request), expectedVersion);
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(updated.getVersion()))
        .body(mapper.toResponse(updated));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@permissions.has('purchase_request:delete')")
  public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest httpRequest) {
    requests.delete(id, ConditionalRequests.requireIfMatch(httpRequest));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/submit")
  @PreAuthorize("@permissions.has('purchase_request:submit')")
  public ResponseEntity<PurchaseRequestResponse> submit(
      @PathVariable UUID id, HttpServletRequest httpRequest) {
    long expectedVersion = ConditionalRequests.requireIfMatch(httpRequest);
    PurchaseRequest submitted = requests.submit(id, expectedVersion);
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(submitted.getVersion()))
        .body(mapper.toResponse(submitted));
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("@permissions.has('purchase_request:cancel')")
  public ResponseEntity<PurchaseRequestResponse> cancel(
      @PathVariable UUID id, HttpServletRequest httpRequest) {
    long expectedVersion = ConditionalRequests.requireIfMatch(httpRequest);
    PurchaseRequest cancelled = requests.cancel(id, expectedVersion);
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(cancelled.getVersion()))
        .body(mapper.toResponse(cancelled));
  }

  @GetMapping("/{id}/actions")
  @PreAuthorize("@permissions.has('purchase_request:read')")
  public PurchaseRequestActionsResponse actions(@PathVariable UUID id) {
    return actionService.forRequest(id);
  }

  private static PurchaseRequestCommand toCommand(PurchaseRequestRequest request) {
    List<PurchaseRequestLineCommand> lines =
        request.lines().stream()
            .map(
                line ->
                    new PurchaseRequestLineCommand(
                        line.description(),
                        line.quantity(),
                        line.unit(),
                        line.unitPrice(),
                        line.catalogItemId()))
            .toList();
    return new PurchaseRequestCommand(
        request.title(),
        request.justification(),
        request.departmentId(),
        request.neededBy(),
        request.currency(),
        Boolean.TRUE.equals(request.budgetExceptionRequested()),
        lines);
  }
}
