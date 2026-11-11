package com.company.procurement.purchaseorder.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreatePurchaseOrderRequest(
    @NotNull(message = "vendorId is required") UUID vendorId,
    @Size(max = 500, message = "paymentTerms must be at most 500 characters") String paymentTerms,
    LocalDate deliveryDate) {}
