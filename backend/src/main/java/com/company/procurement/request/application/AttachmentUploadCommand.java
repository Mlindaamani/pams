package com.company.procurement.request.application;

public record AttachmentUploadCommand(String fileName, byte[] content) {}
