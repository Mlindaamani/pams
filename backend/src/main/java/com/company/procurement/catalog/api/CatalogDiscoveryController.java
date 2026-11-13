package com.company.procurement.catalog.api;

import com.company.procurement.catalog.api.dto.CatalogItemResponse;
import com.company.procurement.catalog.api.mapper.CatalogItemMapper;
import com.company.procurement.catalog.application.CatalogDiscoveryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST API for catalog items used by purchase requests. */
@RestController
@RequestMapping("/api/v1/catalog-items")
@Tag(name = "Catalog", description = "Catalog items used by purchase requests")
public class CatalogDiscoveryController {

  private final CatalogDiscoveryService discovery;
  private final CatalogItemMapper mapper;

  public CatalogDiscoveryController(CatalogDiscoveryService discovery, CatalogItemMapper mapper) {
    this.discovery = discovery;
    this.mapper = mapper;
  }

  @GetMapping("/favorites")
  @PreAuthorize("@permissions.has('catalog:read')")
  public List<CatalogItemResponse> favorites() {
    return discovery.favorites().stream().map(mapper::toResponse).toList();
  }

  @GetMapping("/popular")
  @PreAuthorize("@permissions.has('catalog:read')")
  public List<CatalogItemResponse> popular(
      @RequestParam(required = false) UUID departmentId,
      @RequestParam(defaultValue = "10") int limit) {
    return discovery.popular(departmentId, limit).stream().map(mapper::toResponse).toList();
  }

  @PutMapping("/{id}/favorite")
  @PreAuthorize("@permissions.has('catalog:read')")
  public void favorite(@PathVariable UUID id) {
    discovery.favorite(id);
  }

  @DeleteMapping("/{id}/favorite")
  @PreAuthorize("@permissions.has('catalog:read')")
  public void unfavorite(@PathVariable UUID id) {
    discovery.unfavorite(id);
  }
}
