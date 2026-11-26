package com.company.procurement.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Inbound public demo request. */
@Entity
@Table(name = "demo_request")
public class DemoRequest {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false, length = 320)
  private String email;

  @Column(nullable = false, length = 200)
  private String company;

  @Column(length = 50)
  private String phone;

  @Column(length = 2000)
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private DemoRequestStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected DemoRequest() {
    // for JPA
  }

  public DemoRequest(
      String name, String email, String company, String phone, String message, Instant createdAt) {
    this.name = Objects.requireNonNull(name, "name");
    this.email = Objects.requireNonNull(email, "email");
    this.company = Objects.requireNonNull(company, "company");
    this.phone = phone;
    this.message = message;
    this.status = DemoRequestStatus.NEW;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getCompany() {
    return company;
  }

  public String getPhone() {
    return phone;
  }

  public String getMessage() {
    return message;
  }

  public DemoRequestStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
