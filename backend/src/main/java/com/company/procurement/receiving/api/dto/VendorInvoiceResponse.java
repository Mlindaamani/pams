package com.company.procurement.receiving.api.dto;

import com.company.procurement.receiving.domain.VendorInvoiceStatus;
import com.company.procurement.shared.dto.MoneyDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record VendorInvoiceResponse(
    UUID id,
    UUID purchaseOrderId,
    String poNumber,
    UUID vendorId,
    String invoiceNumber,
    String internalReference,
    LocalDate invoiceDate,
    LocalDate dueDate,
    String currency,
    MoneyDto subtotal,
    MoneyDto tax,
    MoneyDto total,
    MoneyDto paidAmount,
    VendorInvoiceStatus status,
    BigDecimal tolerancePercent,
    String notes,
    Instant approvedAt,
    String approvedBy,
    String resolutionNote,
    Instant resolvedAt,
    String resolvedBy,
    Instant createdAt,
    Instant updatedAt,
    long version,
    List<VendorInvoiceLineResponse> lines) {}
