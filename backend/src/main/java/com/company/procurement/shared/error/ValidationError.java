package com.company.procurement.shared.error;

/** Field-level validation failure included in RFC 7807 {@code errors[]}. */
public record ValidationError(String field, String message) {}
