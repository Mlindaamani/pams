package com.company.procurement.receiving.domain;

import java.time.LocalDate;
import java.util.UUID;

public record VendorInvoiceFilter(
    VendorInvoiceStatus status,
    UUID purchaseOrderId,
    UUID vendorId,
    String invoiceNumber,
    LocalDate dateFrom,
    LocalDate dateTo) {}
