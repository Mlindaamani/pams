package com.company.procurement.purchaseorder.api.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdatePurchaseOrderRequest(
    @Size(max = 500, message = "paymentTerms must be at most 500 characters") String paymentTerms,
    LocalDate deliveryDate) {}
