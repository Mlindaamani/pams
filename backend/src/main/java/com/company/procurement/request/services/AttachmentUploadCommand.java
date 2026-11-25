package com.company.procurement.request.services;

public record AttachmentUploadCommand(String fileName, byte[] content) {}
