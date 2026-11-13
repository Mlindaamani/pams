package com.company.procurement.budget.api;

import com.company.procurement.budget.api.dto.BudgetResponse;
import com.company.procurement.budget.api.dto.CreateBudgetRequest;
import com.company.procurement.budget.api.dto.UpdateBudgetRequest;
import com.company.procurement.budget.api.mapper.BudgetMapper;
import com.company.procurement.budget.application.BudgetService;
import com.company.procurement.budget.application.CreateBudgetCommand;
import com.company.procurement.budget.application.UpdateBudgetCommand;
import com.company.procurement.budget.domain.Budget;
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

/** REST API for departmental budgets and reservations. */
@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "Budgets", description = "Departmental budgets and reservations")
public class BudgetController {

  private static final Set<String> SORTABLE =
      Set.of("createdAt", "budgetNumber", "fiscalYear", "allocatedAmount");

  private final BudgetService budgets;
  private final BudgetMapper mapper;

  public BudgetController(BudgetService budgets, BudgetMapper mapper) {
    this.budgets = budgets;
    this.mapper = mapper;
  }

  @PostMapping
  @PreAuthorize("@permissions.has('budget:manage')")
  public ResponseEntity<BudgetResponse> create(@Valid @RequestBody CreateBudgetRequest request) {
    Budget created =
        budgets.create(
            new CreateBudgetCommand(
                request.departmentId(),
                request.fiscalYear(),
                request.category(),
                request.allocated().currency(),
                request.allocated().toAmount()));
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
    return ResponseEntity.created(location).body(mapper.toResponse(created));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@permissions.has('budget:read')")
  public BudgetResponse get(@PathVariable UUID id) {
    return mapper.toResponse(budgets.get(id));
  }

  @GetMapping
  @PreAuthorize("@permissions.has('budget:read')")
  public PageResponse<BudgetResponse> list(
      @RequestParam(required = false) UUID departmentId,
      @RequestParam(required = false) Integer fiscalYear,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt,desc") String sort) {
    Pageable pageable = PageRequests.of(page, size, sort, SORTABLE);
    return PageResponse.from(
        budgets.search(departmentId, fiscalYear, pageable), mapper::toResponse);
  }

  @PutMapping("/{id}")
  @PreAuthorize("@permissions.has('budget:manage')")
  public BudgetResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateBudgetRequest request) {
    return mapper.toResponse(
        budgets.update(id, new UpdateBudgetCommand(request.allocated().toAmount())));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@permissions.has('budget:manage')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    budgets.delete(id);
    return ResponseEntity.noContent().build();
  }
}
