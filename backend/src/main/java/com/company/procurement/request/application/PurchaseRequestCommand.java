package com.company.procurement.request.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseRequestCommand(
    String title,
    String justification,
    UUID departmentId,
    LocalDate neededBy,
    String currency,
    boolean budgetExceptionRequested,
    List<PurchaseRequestLineCommand> lines) {}
