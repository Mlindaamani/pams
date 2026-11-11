package com.company.procurement.vendor.application;

public record UpdateVendorCommand(
    String name,
    String taxId,
    String contactName,
    String contactEmail,
    String contactPhone,
    String addressLine,
    String bankName,
    String bankAccountName,
    String bankAccountNumber) {}
