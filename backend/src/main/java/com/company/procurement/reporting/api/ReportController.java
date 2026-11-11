package com.company.procurement.reporting.api;

import com.company.procurement.reporting.application.ReportService;
import com.company.procurement.reporting.application.dto.CommitmentsReport;
import com.company.procurement.reporting.application.dto.CycleTimeReport;
import com.company.procurement.reporting.application.dto.SpendReport;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Spend, approval cycle time and budget commitments")
@PreAuthorize("@permissions.has('report:read')")
@com.company.procurement.subscription.application.RequiresFeature("core_reports")
public class ReportController {

  private final ReportService reports;

  public ReportController(ReportService reports) {
    this.reports = reports;
  }

  @GetMapping("/spend")
  public SpendReport spend(
      @RequestParam(defaultValue = "department") String groupBy,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    return reports.spend(groupBy, from, to);
  }

  @GetMapping("/spend.csv")
  @com.company.procurement.subscription.application.RequiresFeature("advanced_reports")
  @PreAuthorize("@permissions.has('report:export')")
  public void spendCsv(
      @RequestParam(defaultValue = "department") String groupBy,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      HttpServletResponse response)
      throws IOException {
    response.setContentType("text/csv");
    response.setHeader(
        HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment().filename("spend-" + groupBy + ".csv").build().toString());
    reports.streamSpendCsv(groupBy, from, to, response.getOutputStream());
    response.flushBuffer();
  }

  @GetMapping("/approval-cycle-time")
  public CycleTimeReport cycleTime(
      @RequestParam(defaultValue = "policy") String groupBy,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    return reports.cycleTime(groupBy, from, to);
  }

  @GetMapping("/commitments")
  public CommitmentsReport commitments(
      @RequestParam(required = false) Integer fiscalYear,
      @RequestParam(required = false) UUID departmentId) {
    return reports.commitments(fiscalYear, departmentId);
  }
}
