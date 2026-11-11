package com.company.procurement.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord extends TenantEntity {

  @Column(name = "idempotency_key", nullable = false, length = 200)
  private String idempotencyKey;

  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  @Column(name = "response_status", nullable = false)
  private int responseStatus;

  @Column(name = "response_body", nullable = false, columnDefinition = "text")
  private String responseBody;

  @Column(name = "response_headers", nullable = false, columnDefinition = "text")
  private String responseHeaders;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  protected IdempotencyRecord() {
    // for JPA
  }

  public IdempotencyRecord(
      java.util.UUID organizationId,
      String idempotencyKey,
      String requestHash,
      int responseStatus,
      String responseBody,
      String responseHeaders,
      Instant expiresAt) {
    super(organizationId);
    this.idempotencyKey = idempotencyKey;
    this.requestHash = requestHash;
    this.responseStatus = responseStatus;
    this.responseBody = responseBody;
    this.responseHeaders = responseHeaders;
    this.expiresAt = expiresAt;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public int getResponseStatus() {
    return responseStatus;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public String getResponseHeaders() {
    return responseHeaders;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
