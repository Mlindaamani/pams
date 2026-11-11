package com.company.procurement.request.api.dto;

import com.company.procurement.request.domain.RequestStatus;
import com.company.procurement.shared.dto.MoneyDto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseRequestResponse(
    UUID id,
    String requesterSubject,
    String requesterName,
    UUID departmentId,
    String title,
    String justification,
    LocalDate neededBy,
    String currency,
    RequestStatus status,
    MoneyDto total,
    UUID budgetId,
    UUID approvalPolicyId,
    Integer approvalPolicyVersion,
    boolean budgetReserved,
    boolean budgetExceptionRequested,
    boolean budgetException,
    Instant submittedAt,
    Instant createdAt,
    Instant updatedAt,
    long version,
    List<PurchaseRequestLineResponse> lines) {}
