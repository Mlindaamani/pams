package com.company.procurement.receiving.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RecordVendorInvoiceRequest(
    @NotBlank @Size(max = 100) String invoiceNumber,
    @NotNull LocalDate invoiceDate,
    LocalDate dueDate,
    @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "currency must be a 3-letter code")
        String currency,
    @DecimalMin(value = "0.00") BigDecimal taxAmount,
    @Size(max = 1000) String notes,
    @NotEmpty @Valid List<InvoiceLine> lines) {

  public record InvoiceLine(
      @NotNull UUID purchaseOrderLineId,
      @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity,
      @NotNull @DecimalMin(value = "0.00") BigDecimal unitPrice) {}
}
