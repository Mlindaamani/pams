package com.company.procurement.reporting.services.dto;

import java.time.Instant;
import java.util.List;

public record SpendReport(
    String groupBy, Instant from, Instant to, Instant generatedAt, List<SpendRow> rows)
    implements java.io.Serializable {}
