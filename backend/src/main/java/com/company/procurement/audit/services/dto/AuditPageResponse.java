package com.company.procurement.audit.services.dto;

import java.util.List;

public record AuditPageResponse(List<AuditEventResponse> content, String nextCursor) {}
