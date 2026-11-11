package com.company.procurement.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.procurement.shared.error.BadRequestException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageRequestsTest {

  private static final Set<String> SORTABLE = Set.of("createdAt", "name");

  @Test
  void capsSizeAtMaximum() {
    assertThat(PageRequests.of(0, 1000, "name,asc", SORTABLE))
        .extracting(Pageable::getPageSize)
        .isEqualTo(PageRequests.MAX_SIZE);
  }

  @Test
  void defaultsSizeAndSort() {
    Pageable pageable = PageRequests.of(0, 0, null, SORTABLE);

    assertThat(pageable.getPageSize()).isEqualTo(PageRequests.DEFAULT_SIZE);
    assertThat(pageable.getSort().getOrderFor("createdAt"))
        .isNotNull()
        .extracting(Sort.Order::getDirection)
        .isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void rejectsNegativePage() {
    assertThatThrownBy(() -> PageRequests.of(-1, 20, "name", SORTABLE))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("page");
  }

  @Test
  void rejectsUnlistedSortField() {
    assertThatThrownBy(() -> PageRequests.of(0, 20, "password,desc", SORTABLE))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("not allowed");
  }

  @Test
  void rejectsUnknownDirection() {
    assertThatThrownBy(() -> PageRequests.of(0, 20, "name,sideways", SORTABLE))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("direction");
  }
}
