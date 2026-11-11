package com.company.procurement.reporting.application.dto;

import com.company.procurement.shared.dto.MoneyDto;
import java.util.UUID;

public record CommitmentRow(
    UUID budgetId,
    UUID departmentId,
    String departmentName,
    int fiscalYear,
    String currency,
    MoneyDto allocated,
    MoneyDto reserved,
    MoneyDto committed,
    MoneyDto actual,
    MoneyDto remaining)
    implements java.io.Serializable {}
