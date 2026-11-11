package com.company.procurement.request.application;

import java.util.List;
import java.util.UUID;

public record PurchaseRequestActionsResponse(List<String> actions, UUID approvalStepId) {}
