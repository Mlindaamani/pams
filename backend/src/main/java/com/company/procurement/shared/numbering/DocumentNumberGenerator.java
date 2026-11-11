package com.company.procurement.shared.numbering;

import java.util.UUID;

public interface DocumentNumberGenerator {

  String next(UUID organizationId, DocumentType type, int year);
}
