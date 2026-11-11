package com.company.procurement.catalog.api;

import com.company.procurement.catalog.api.dto.CatalogItemResponse;
import com.company.procurement.catalog.api.dto.CreateCatalogItemRequest;
import com.company.procurement.catalog.api.dto.UpdateCatalogItemRequest;
import com.company.procurement.catalog.api.mapper.CatalogItemMapper;
import com.company.procurement.catalog.application.CatalogItemService;
import com.company.procurement.catalog.application.CreateCatalogItemCommand;
import com.company.procurement.catalog.application.UpdateCatalogItemCommand;
import com.company.procurement.catalog.domain.CatalogItem;
import com.company.procurement.shared.api.BulkIdsRequest;
import com.company.procurement.shared.api.BulkOperationResponse;
import com.company.procurement.shared.api.PageResponse;
import com.company.procurement.shared.web.PageRequests;
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
@Tag(name = "Catalog", description = "Catalog items used by purchase requests")
@RequestMapping("/api/v1/catalog-items")
public class CatalogItemController {

  private static final Set<String> SORTABLE =
      Set.of("createdAt", "name", "sku", "defaultPrice", "category");

  private final CatalogItemService items;
  private final CatalogItemMapper mapper;

  public CatalogItemController(CatalogItemService items, CatalogItemMapper mapper) {
    this.items = items;
    this.mapper = mapper;
  }

  @PreAuthorize("@permissions.has('catalog:manage')")
  @PostMapping
  public ResponseEntity<CatalogItemResponse> create(
      @Valid @RequestBody CreateCatalogItemRequest request) {
    CatalogItem created =
        items.create(
            new CreateCatalogItemCommand(
                request.sku(),
                request.name(),
                request.unit(),
                request.defaultPrice().toAmount(),
                request.defaultPrice().currency(),
                request.category(),
                request.defaultVendorId()));
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
    return ResponseEntity.created(location).body(mapper.toResponse(created));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@permissions.has('catalog:read')")
  public CatalogItemResponse get(@PathVariable UUID id) {
    return mapper.toResponse(items.get(id));
  }

  @GetMapping
  @PreAuthorize("@permissions.has('catalog:read')")
  public PageResponse<CatalogItemResponse> list(
      @RequestParam(required = false) String category,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt,desc") String sort) {
    Pageable pageable = PageRequests.of(page, size, sort, SORTABLE);
    return PageResponse.from(items.search(category, active, q, pageable), mapper::toResponse);
  }

  @PreAuthorize("@permissions.has('catalog:manage')")
  @PutMapping("/{id}")
  public CatalogItemResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateCatalogItemRequest request) {
    return mapper.toResponse(
        items.update(
            id,
            new UpdateCatalogItemCommand(
                request.name(),
                request.unit(),
                request.defaultPrice().toAmount(),
                request.defaultPrice().currency(),
                request.category(),
                request.defaultVendorId(),
                request.active())));
  }

  @PreAuthorize("@permissions.has('catalog:manage')")
  @PostMapping("/bulk-delete")
  public BulkOperationResponse bulkDelete(@Valid @RequestBody BulkIdsRequest request) {
    return new BulkOperationResponse(items.bulkDelete(request.ids()));
  }

  @PreAuthorize("@permissions.has('catalog:manage')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    items.delete(id);
    return ResponseEntity.noContent().build();
  }
}
