package com.company.procurement.vendor.api;

import com.company.procurement.shared.api.BulkIdsRequest;
import com.company.procurement.shared.api.BulkOperationResponse;
import com.company.procurement.shared.api.PageResponse;
import com.company.procurement.shared.web.PageRequests;
import com.company.procurement.vendor.api.dto.CreateVendorRequest;
import com.company.procurement.vendor.api.dto.UpdateVendorRequest;
import com.company.procurement.vendor.api.dto.VendorResponse;
import com.company.procurement.vendor.api.mapper.VendorMapper;
import com.company.procurement.vendor.application.CreateVendorCommand;
import com.company.procurement.vendor.application.UpdateVendorCommand;
import com.company.procurement.vendor.application.VendorService;
import com.company.procurement.vendor.domain.Vendor;
import com.company.procurement.vendor.domain.VendorStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
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
@Tag(name = "Vendors", description = "Vendor master data and status lifecycle")
@RequestMapping("/api/v1/vendors")
@com.company.procurement.subscription.application.RequiresFeature("vendors")
public class VendorController {

  private static final Set<String> SORTABLE = Set.of("createdAt", "name", "status", "taxId");

  private final VendorService vendors;
  private final VendorMapper mapper;

  public VendorController(VendorService vendors, VendorMapper mapper) {
    this.vendors = vendors;
    this.mapper = mapper;
  }

  @PreAuthorize("@permissions.has('vendor:manage')")
  @PostMapping
  public ResponseEntity<VendorResponse> create(@Valid @RequestBody CreateVendorRequest request) {
    Vendor created =
        vendors.create(
            new CreateVendorCommand(
                request.name(),
                request.taxId(),
                request.contactName(),
                request.contactEmail(),
                request.contactPhone(),
                request.addressLine(),
                request.bankName(),
                request.bankAccountName(),
                request.bankAccountNumber()));
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
    return ResponseEntity.created(location).body(mapper.toResponse(created));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@permissions.has('vendor:read')")
  public VendorResponse get(@PathVariable UUID id) {
    return mapper.toResponse(vendors.get(id));
  }

  @GetMapping
  @PreAuthorize("@permissions.has('vendor:read')")
  public PageResponse<VendorResponse> list(
      @RequestParam(required = false) VendorStatus status,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt,desc") String sort) {
    Pageable pageable = PageRequests.of(page, size, sort, SORTABLE);
    return PageResponse.from(vendors.search(status, q, pageable), mapper::toResponse);
  }

  @PreAuthorize("@permissions.has('vendor:manage')")
  @PutMapping("/{id}")
  public VendorResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateVendorRequest request) {
    return mapper.toResponse(
        vendors.update(
            id,
            new UpdateVendorCommand(
                request.name(),
                request.taxId(),
                request.contactName(),
                request.contactEmail(),
                request.contactPhone(),
                request.addressLine(),
                request.bankName(),
                request.bankAccountName(),
                request.bankAccountNumber())));
  }

  @PreAuthorize("@permissions.has('vendor:manage')")
  @PostMapping("/{id}/activate")
  public VendorResponse activate(@PathVariable UUID id) {
    return mapper.toResponse(vendors.activate(id));
  }

  @PreAuthorize("@permissions.has('vendor:manage')")
  @PostMapping("/{id}/suspend")
  public VendorResponse suspend(@PathVariable UUID id) {
    return mapper.toResponse(vendors.suspend(id));
  }

  @PreAuthorize("@permissions.has('vendor:manage')")
  @PostMapping("/{id}/blacklist")
  public VendorResponse blacklist(@PathVariable UUID id) {
    return mapper.toResponse(vendors.blacklist(id));
  }

  @PreAuthorize("@permissions.has('vendor:manage')")
  @PostMapping("/bulk-delete")
  public BulkOperationResponse bulkDelete(@Valid @RequestBody BulkIdsRequest request) {
    return new BulkOperationResponse(vendors.bulkDelete(request.ids()));
  }

  @PreAuthorize("@permissions.has('vendor:manage')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    vendors.delete(id);
    return ResponseEntity.noContent().build();
  }
}
