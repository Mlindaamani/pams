package com.company.procurement.reporting.application.dto;

import java.time.Instant;
import java.util.List;

public record CommitmentsReport(
    Integer fiscalYear, java.util.UUID departmentId, Instant generatedAt, List<CommitmentRow> rows)
    implements java.io.Serializable {}
