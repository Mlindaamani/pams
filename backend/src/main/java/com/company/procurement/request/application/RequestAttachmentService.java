package com.company.procurement.request.application;

import com.company.procurement.request.domain.PurchaseRequest;
import com.company.procurement.request.domain.RequestAttachment;
import com.company.procurement.request.domain.RequestAttachmentRepository;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.security.CurrentUser;
import com.company.procurement.shared.storage.FileStoragePort;
import com.company.procurement.shared.tenant.TenantContext;
import com.company.procurement.subscription.application.SubscriptionAccessService;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores and retrieves request attachments. */
@Service
public class RequestAttachmentService {

  public static final int MAX_ATTACHMENTS = 10;
  public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of(
          "application/pdf",
          "image/png",
          "image/jpeg",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final RequestAttachmentRepository attachments;
  private final PurchaseRequestService requests;
  private final FileStoragePort storage;
  private final CurrentUser currentUser;
  private final SubscriptionAccessService subscriptionAccess;
  private final Tika tika = new Tika();

  public RequestAttachmentService(
      RequestAttachmentRepository attachments,
      PurchaseRequestService requests,
      FileStoragePort storage,
      CurrentUser currentUser,
      SubscriptionAccessService subscriptionAccess) {
    this.attachments = attachments;
    this.requests = requests;
    this.storage = storage;
    this.currentUser = currentUser;
    this.subscriptionAccess = subscriptionAccess;
  }

  @Transactional
  public RequestAttachment upload(UUID requestId, AttachmentUploadCommand command) {
    PurchaseRequest request = requests.requireEditableByOwner(requestId);
    UUID organizationId = TenantContext.require();
    subscriptionAccess.assertCanStore(
        organizationId, command.content() == null ? 0 : command.content().length);

    if (command.content() == null || command.content().length == 0) {
      throw new BusinessRuleException("Attachment is empty");
    }
    if (command.content().length > MAX_SIZE_BYTES) {
      throw new BusinessRuleException("Attachment exceeds the 10 MB limit");
    }
    if (attachments.countByOrganizationIdAndRequestId(organizationId, request.getId())
        >= MAX_ATTACHMENTS) {
      throw new BusinessRuleException("A request can have at most 10 attachments");
    }

    String detectedType = tika.detect(command.content(), command.fileName());
    if (!ALLOWED_CONTENT_TYPES.contains(detectedType)) {
      throw new BusinessRuleException("Unsupported attachment type: " + detectedType);
    }

    String storageKey =
        "%s/%s/%s%s"
            .formatted(
                organizationId, request.getId(), UUID.randomUUID(), extension(command.fileName()));
    storage.put(storageKey, command.content(), detectedType);

    return attachments.save(
        new RequestAttachment(
            organizationId,
            request.getId(),
            sanitizeFileName(command.fileName()),
            detectedType,
            command.content().length,
            sha256(command.content()),
            storageKey,
            currentUser.subject()));
  }

  @Transactional(readOnly = true)
  public List<RequestAttachment> list(UUID requestId) {
    PurchaseRequest request = requests.requireVisible(requestId);
    return attachments.findAllByOrganizationIdAndRequestId(
        TenantContext.require(), request.getId());
  }

  @Transactional(readOnly = true)
  public StoredAttachment download(UUID requestId, UUID attachmentId) {
    PurchaseRequest request = requests.requireVisible(requestId);
    RequestAttachment attachment =
        attachments
            .findByIdAndOrganizationIdAndRequestId(
                attachmentId, TenantContext.require(), request.getId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Attachment %s not found".formatted(attachmentId)));
    return new StoredAttachment(
        storage.get(attachment.getStorageKey()),
        attachment.getFileName(),
        attachment.getContentType());
  }

  @Transactional
  public void delete(UUID requestId, UUID attachmentId) {
    PurchaseRequest request = requests.requireEditableByOwner(requestId);
    RequestAttachment attachment =
        attachments
            .findByIdAndOrganizationIdAndRequestId(
                attachmentId, TenantContext.require(), request.getId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Attachment %s not found".formatted(attachmentId)));
    storage.delete(attachment.getStorageKey());
    attachments.delete(attachment);
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  private static String sanitizeFileName(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return "attachment";
    }
    String base = fileName.replace('\\', '/');
    base = base.substring(base.lastIndexOf('/') + 1);
    String cleaned = base.replaceAll("[^A-Za-z0-9._-]", "_");
    return cleaned.length() > 255 ? cleaned.substring(cleaned.length() - 255) : cleaned;
  }

  private static String extension(String fileName) {
    if (fileName == null) {
      return "";
    }
    int dot = fileName.lastIndexOf('.');
    if (dot < 0 || dot == fileName.length() - 1) {
      return "";
    }
    return fileName.substring(dot).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "");
  }

  public record StoredAttachment(InputStream stream, String fileName, String contentType) {}
}
