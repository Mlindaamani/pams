package com.company.procurement.receiving.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RecordReceiptRequest(
    LocalDate receivedDate,
    @Size(max = 100) String deliveryNoteReference,
    @Size(max = 1000) String notes,
    @NotEmpty @Valid List<ReceiptLine> lines) {

  public record ReceiptLine(
      @NotNull UUID purchaseOrderLineId,
      @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity) {}
}
