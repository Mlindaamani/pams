package com.company.procurement.audit.application.dto;

import java.util.List;

public record AuditPageResponse(List<AuditEventResponse> content, String nextCursor) {}
