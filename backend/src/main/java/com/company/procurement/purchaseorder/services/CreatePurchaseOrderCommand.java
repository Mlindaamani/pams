package com.company.procurement.purchaseorder.services;

import java.time.LocalDate;
import java.util.UUID;

public record CreatePurchaseOrderCommand(
    UUID vendorId, String paymentTerms, LocalDate deliveryDate) {}
