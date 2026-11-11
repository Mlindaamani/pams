package com.company.procurement.request.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(
    UUID id,
    String fileName,
    String contentType,
    long sizeBytes,
    String checksumSha256,
    String uploadedBy,
    Instant createdAt) {}
