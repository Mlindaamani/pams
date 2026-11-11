package com.company.procurement.shared.numbering;

public enum DocumentType {
  PURCHASE_REQUEST("PR"),
  BUDGET("BUD"),
  GOODS_RECEIPT("GRN"),
  VENDOR_PAYMENT("PAY");

  private final String prefix;

  DocumentType(String prefix) {
    this.prefix = prefix;
  }

  public String prefix() {
    return prefix;
  }
}
