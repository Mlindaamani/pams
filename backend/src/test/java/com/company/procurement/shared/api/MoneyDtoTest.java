package com.company.procurement.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.procurement.shared.dto.MoneyDto;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyDtoTest {

  @Test
  void normalizesToFourDecimalPlaces() {
    assertThat(MoneyDto.of(new BigDecimal("12.5"), "TZS"))
        .isEqualTo(new MoneyDto("12.5000", "TZS"));
  }

  @Test
  void returnsNullForMissingAmount() {
    assertThat(MoneyDto.of(null, "TZS")).isNull();
  }
}
