package com.company.procurement.reporting.application.dto;

public record CycleTimeRow(
    String key,
    String label,
    long count,
    double averageMinutes,
    double p50Minutes,
    double p90Minutes)
    implements java.io.Serializable {}
