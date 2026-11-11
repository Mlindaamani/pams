package com.company.procurement.shared.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Canonical JSON representation of money: amount as a string, ISO-4217 currency code. */
public record MoneyDto(String amount, String currency) implements java.io.Serializable {

  public static final int SCALE = 4;

  public static MoneyDto of(BigDecimal amount, String currency) {
    if (amount == null) {
      return null;
    }
    return new MoneyDto(amount.setScale(SCALE, RoundingMode.HALF_UP).toPlainString(), currency);
  }
}
