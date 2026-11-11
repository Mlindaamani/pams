package com.company.procurement.approval.application;

import java.math.BigDecimal;
import java.util.UUID;

public interface ApprovalChainResolver {

  ResolvedChain resolve(
      UUID organizationId,
      String entityType,
      BigDecimal amount,
      UUID requesterUserId,
      UUID requesterManagerUserId,
      boolean budgetException,
      UUID departmentId);

  default ResolvedChain resolve(
      UUID organizationId,
      BigDecimal amount,
      UUID requesterUserId,
      UUID requesterManagerUserId,
      boolean budgetException,
      UUID departmentId) {
    return resolve(
        organizationId,
        "PURCHASE_REQUEST",
        amount,
        requesterUserId,
        requesterManagerUserId,
        budgetException,
        departmentId);
  }
}
