package com.company.procurement.vendor.services;

public record CreateVendorCommand(
    String name,
    String taxId,
    String contactName,
    String contactEmail,
    String contactPhone,
    String addressLine,
    String bankName,
    String bankAccountName,
    String bankAccountNumber) {}
