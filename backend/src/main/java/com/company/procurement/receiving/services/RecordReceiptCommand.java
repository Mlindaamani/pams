package com.company.procurement.receiving.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RecordReceiptCommand(
    LocalDate receivedDate, String deliveryNoteReference, String notes, List<Line> lines) {

  public record Line(UUID purchaseOrderLineId, BigDecimal quantity) {}
}
