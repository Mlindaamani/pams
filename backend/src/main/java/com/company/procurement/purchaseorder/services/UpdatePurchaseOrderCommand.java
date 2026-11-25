package com.company.procurement.purchaseorder.services;

import java.time.LocalDate;

public record UpdatePurchaseOrderCommand(String paymentTerms, LocalDate deliveryDate) {}
