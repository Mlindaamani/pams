package com.company.procurement.purchaseorder.domain;

import java.util.UUID;

public interface PurchaseOrderNumberGenerator {

  String nextNumber(UUID organizationId, int fiscalYear);
}
