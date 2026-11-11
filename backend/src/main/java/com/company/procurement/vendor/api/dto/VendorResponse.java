package com.company.procurement.vendor.api.dto;

import com.company.procurement.vendor.domain.VendorStatus;
import java.time.Instant;
import java.util.UUID;

public record VendorResponse(
    UUID id,
    String name,
    String taxId,
    String contactName,
    String contactEmail,
    String contactPhone,
    String addressLine,
    String bankName,
    String bankAccountName,
    String bankAccountNumber,
    VendorStatus status,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
