package com.company.procurement.reporting.services.dto;

import java.math.BigDecimal;

public record SpendRow(String key, String label, String currency, BigDecimal total, long orderCount)
    implements java.io.Serializable {}
