package com.company.procurement.vendor.domain;

import com.company.procurement.shared.domain.SoftDeletableEntity;
import com.company.procurement.shared.domain.TenantFilter;
import com.company.procurement.shared.error.IllegalStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "vendor")
@SQLRestriction("deleted_at IS NULL")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
/** Vendor master record and lifecycle status. */
public class Vendor extends SoftDeletableEntity {

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "tax_id", nullable = false, length = 50)
  private String taxId;

  @Column(name = "contact_name", length = 200)
  private String contactName;

  @Column(name = "contact_email", length = 320)
  private String contactEmail;

  @Column(name = "contact_phone", length = 50)
  private String contactPhone;

  @Column(name = "address_line", length = 500)
  private String addressLine;

  @Column(name = "bank_name", length = 200)
  private String bankName;

  @Column(name = "bank_account_name", length = 200)
  private String bankAccountName;

  @Column(name = "bank_account_number", length = 100)
  private String bankAccountNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private VendorStatus status;

  protected Vendor() {
    // for JPA
  }

  public Vendor(UUID organizationId, String name, String taxId) {
    super(organizationId);
    this.name = requireText(name, "name");
    this.taxId = requireText(taxId, "taxId");
    this.status = VendorStatus.ACTIVE;
  }

  public void updateDetails(
      String name,
      String taxId,
      String contactName,
      String contactEmail,
      String contactPhone,
      String addressLine,
      String bankName,
      String bankAccountName,
      String bankAccountNumber) {
    this.name = requireText(name, "name");
    this.taxId = requireText(taxId, "taxId");
    this.contactName = trimToNull(contactName);
    this.contactEmail = trimToNull(contactEmail);
    this.contactPhone = trimToNull(contactPhone);
    this.addressLine = trimToNull(addressLine);
    this.bankName = trimToNull(bankName);
    this.bankAccountName = trimToNull(bankAccountName);
    this.bankAccountNumber = trimToNull(bankAccountNumber);
  }

  public void activate() {
    if (status == VendorStatus.ACTIVE) {
      throw new IllegalStateTransitionException("Vendor is already active");
    }
    if (status == VendorStatus.BLACKLISTED) {
      throw new IllegalStateTransitionException("A blacklisted vendor cannot be reactivated");
    }
    this.status = VendorStatus.ACTIVE;
  }

  public void suspend() {
    if (status != VendorStatus.ACTIVE) {
      throw new IllegalStateTransitionException("Only an active vendor can be suspended");
    }
    this.status = VendorStatus.SUSPENDED;
  }

  public void blacklist() {
    if (status == VendorStatus.BLACKLISTED) {
      throw new IllegalStateTransitionException("Vendor is already blacklisted");
    }
    this.status = VendorStatus.BLACKLISTED;
  }

  public String getName() {
    return name;
  }

  public String getTaxId() {
    return taxId;
  }

  public String getContactName() {
    return contactName;
  }

  public String getContactEmail() {
    return contactEmail;
  }

  public String getContactPhone() {
    return contactPhone;
  }

  public String getAddressLine() {
    return addressLine;
  }

  public String getBankName() {
    return bankName;
  }

  public String getBankAccountName() {
    return bankAccountName;
  }

  public String getBankAccountNumber() {
    return bankAccountNumber;
  }

  public VendorStatus getStatus() {
    return status;
  }

  private static String requireText(String value, String field) {
    Objects.requireNonNull(value, field);
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return trimmed;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
