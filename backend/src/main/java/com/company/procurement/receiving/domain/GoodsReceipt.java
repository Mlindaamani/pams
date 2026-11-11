package com.company.procurement.receiving.domain;

import com.company.procurement.shared.domain.TenantEntity;
import com.company.procurement.shared.domain.TenantFilter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "goods_receipt")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
public class GoodsReceipt extends TenantEntity {

  @Column(name = "receipt_number", nullable = false, updatable = false, length = 40)
  private String receiptNumber;

  @Column(name = "purchase_order_id", nullable = false, updatable = false)
  private UUID purchaseOrderId;

  @Column(name = "received_date", nullable = false)
  private LocalDate receivedDate;

  @Column(name = "delivery_note_reference", length = 100)
  private String deliveryNoteReference;

  @Column(name = "notes", length = 1000)
  private String notes;

  @Column(name = "recorded_by_name", length = 200)
  private String recordedByName;

  @OneToMany(
      mappedBy = "receipt",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<GoodsReceiptLine> lines = new ArrayList<>();

  protected GoodsReceipt() {
    // for JPA
  }

  public GoodsReceipt(
      UUID organizationId,
      String receiptNumber,
      UUID purchaseOrderId,
      LocalDate receivedDate,
      String deliveryNoteReference,
      String notes,
      String recordedByName) {
    super(organizationId);
    this.receiptNumber = Objects.requireNonNull(receiptNumber, "receiptNumber");
    this.purchaseOrderId = Objects.requireNonNull(purchaseOrderId, "purchaseOrderId");
    this.receivedDate = Objects.requireNonNull(receivedDate, "receivedDate");
    this.deliveryNoteReference = deliveryNoteReference;
    this.notes = notes;
    this.recordedByName = recordedByName;
  }

  public void addLine(
      UUID purchaseOrderLineId, String description, String unit, BigDecimal quantity) {
    lines.add(
        new GoodsReceiptLine(
            getOrganizationId(), this, purchaseOrderLineId, description, unit, quantity));
  }

  public String getReceiptNumber() {
    return receiptNumber;
  }

  public UUID getPurchaseOrderId() {
    return purchaseOrderId;
  }

  public LocalDate getReceivedDate() {
    return receivedDate;
  }

  public String getDeliveryNoteReference() {
    return deliveryNoteReference;
  }

  public String getNotes() {
    return notes;
  }

  public String getRecordedByName() {
    return recordedByName;
  }

  public List<GoodsReceiptLine> getLines() {
    return lines;
  }
}
