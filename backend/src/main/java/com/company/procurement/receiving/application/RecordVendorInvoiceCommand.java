package com.company.procurement.receiving.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RecordVendorInvoiceCommand(
    String invoiceNumber,
    LocalDate invoiceDate,
    LocalDate dueDate,
    String currency,
    BigDecimal taxAmount,
    String notes,
    List<Line> lines) {

  public record Line(UUID purchaseOrderLineId, BigDecimal quantity, BigDecimal unitPrice) {}
}
