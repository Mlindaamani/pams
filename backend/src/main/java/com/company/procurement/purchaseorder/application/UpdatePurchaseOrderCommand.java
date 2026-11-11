package com.company.procurement.purchaseorder.application;

import java.time.LocalDate;

public record UpdatePurchaseOrderCommand(String paymentTerms, LocalDate deliveryDate) {}
