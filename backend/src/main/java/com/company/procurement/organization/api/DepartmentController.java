package com.company.procurement.organization.api;

import com.company.procurement.organization.api.dto.DepartmentRequest;
import com.company.procurement.organization.api.dto.DepartmentResponse;
import com.company.procurement.organization.api.mapper.DepartmentMapper;
import com.company.procurement.organization.application.DepartmentCommand;
import com.company.procurement.organization.application.DepartmentService;
import com.company.procurement.organization.domain.Department;
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

/** REST API for departments within the current organization. */
@RestController
@Tag(name = "Departments", description = "Departments within the current organization")
@RequestMapping("/api/v1/departments")
public class DepartmentController {

  private static final Set<String> SORTABLE = Set.of("createdAt", "name");

  private final DepartmentService departments;
  private final DepartmentMapper mapper;

  public DepartmentController(DepartmentService departments, DepartmentMapper mapper) {
    this.departments = departments;
    this.mapper = mapper;
  }

  @PreAuthorize("@permissions.has('department:manage')")
  @PostMapping
  public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
    Department created = departments.create(new DepartmentCommand(request.name()));
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
    return ResponseEntity.created(location).body(mapper.toResponse(created));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@permissions.has('department:read')")
  public DepartmentResponse get(@PathVariable UUID id) {
    return mapper.toResponse(departments.get(id));
  }

  @GetMapping
  @PreAuthorize("@permissions.has('department:read')")
  public PageResponse<DepartmentResponse> list(
      @RequestParam(required = false) String name,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt,desc") String sort) {
    Pageable pageable = PageRequests.of(page, size, sort, SORTABLE);
    return PageResponse.from(departments.search(name, pageable), mapper::toResponse);
  }

  @PreAuthorize("@permissions.has('department:manage')")
  @PutMapping("/{id}")
  public DepartmentResponse update(
      @PathVariable UUID id, @Valid @RequestBody DepartmentRequest request) {
    return mapper.toResponse(departments.update(id, new DepartmentCommand(request.name())));
  }

  @PreAuthorize("@permissions.has('department:manage')")
  @PostMapping("/bulk-delete")
  public BulkOperationResponse bulkDelete(@Valid @RequestBody BulkIdsRequest request) {
    return new BulkOperationResponse(departments.bulkDelete(request.ids()));
  }

  @PreAuthorize("@permissions.has('department:manage')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    departments.delete(id);
    return ResponseEntity.noContent().build();
  }
}
