package com.company.procurement.receiving.application;

import java.util.UUID;

public interface InvoiceReferenceGenerator {

  String nextNumber(UUID organizationId, int fiscalYear);
}
