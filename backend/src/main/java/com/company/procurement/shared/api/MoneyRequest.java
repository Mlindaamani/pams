package com.company.procurement.shared.api;

import com.company.procurement.shared.dto.MoneyDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/** Incoming money value; amounts are strings to avoid binary floating point. */
public record MoneyRequest(
    @NotBlank(message = "amount is required")
        @Pattern(
            regexp = "^\\d{1,15}(\\.\\d{1,4})?$",
            message = "amount must be a non-negative decimal with at most 4 fraction digits")
        String amount,
    @NotBlank(message = "currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter uppercase code")
        String currency) {

  public BigDecimal toAmount() {
    return new BigDecimal(amount);
  }

  public MoneyDto toDto() {
    return MoneyDto.of(toAmount(), currency);
  }
}
