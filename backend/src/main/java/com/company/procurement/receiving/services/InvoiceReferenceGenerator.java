package com.company.procurement.receiving.services;

import java.util.UUID;

public interface InvoiceReferenceGenerator {

  String nextNumber(UUID organizationId, int fiscalYear);
}
