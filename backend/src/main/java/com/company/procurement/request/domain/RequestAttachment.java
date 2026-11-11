package com.company.procurement.request.domain;

import com.company.procurement.shared.domain.TenantEntity;
import com.company.procurement.shared.domain.TenantFilter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "request_attachment")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
public class RequestAttachment extends TenantEntity {

  @Column(name = "purchase_request_id", nullable = false, updatable = false)
  private UUID requestId;

  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "checksum_sha256", nullable = false, length = 64)
  private String checksumSha256;

  @Column(name = "storage_key", nullable = false, length = 500)
  private String storageKey;

  @Column(name = "uploaded_by", nullable = false, length = 200)
  private String uploadedBy;

  protected RequestAttachment() {
    // for JPA
  }

  public RequestAttachment(
      UUID organizationId,
      UUID requestId,
      String fileName,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      String storageKey,
      String uploadedBy) {
    super(organizationId);
    this.requestId = Objects.requireNonNull(requestId, "requestId");
    this.fileName = Objects.requireNonNull(fileName, "fileName");
    this.contentType = Objects.requireNonNull(contentType, "contentType");
    this.sizeBytes = sizeBytes;
    this.checksumSha256 = Objects.requireNonNull(checksumSha256, "checksumSha256");
    this.storageKey = Objects.requireNonNull(storageKey, "storageKey");
    this.uploadedBy = Objects.requireNonNull(uploadedBy, "uploadedBy");
  }

  public UUID getRequestId() {
    return requestId;
  }

  public String getFileName() {
    return fileName;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public String getChecksumSha256() {
    return checksumSha256;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public String getUploadedBy() {
    return uploadedBy;
  }
}
