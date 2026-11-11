package com.company.procurement.reporting.application.dto;

import java.time.Instant;
import java.util.List;

public record CycleTimeReport(
    String groupBy, Instant from, Instant to, Instant generatedAt, List<CycleTimeRow> rows)
    implements java.io.Serializable {}
