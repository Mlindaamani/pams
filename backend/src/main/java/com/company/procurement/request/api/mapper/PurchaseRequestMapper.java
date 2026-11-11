package com.company.procurement.request.api.mapper;

import com.company.procurement.request.api.dto.PurchaseRequestLineResponse;
import com.company.procurement.request.api.dto.PurchaseRequestResponse;
import com.company.procurement.request.domain.PurchaseRequest;
import com.company.procurement.request.domain.PurchaseRequestLine;
import com.company.procurement.shared.dto.MoneyDto;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PurchaseRequestMapper {

  @Mapping(
      target = "total",
      expression = "java(MoneyDto.of(request.getTotalAmount(), request.getCurrency()))")
  @Mapping(target = "lines", expression = "java(mapLines(request))")
  PurchaseRequestResponse toResponse(PurchaseRequest request);

  @Mapping(
      target = "total",
      expression = "java(MoneyDto.of(request.getTotalAmount(), request.getCurrency()))")
  @Mapping(target = "lines", ignore = true)
  PurchaseRequestResponse toSummary(PurchaseRequest request);

  default List<PurchaseRequestLineResponse> mapLines(PurchaseRequest request) {
    return request.getLines().stream().map(line -> mapLine(line, request.getCurrency())).toList();
  }

  default PurchaseRequestLineResponse mapLine(PurchaseRequestLine line, String currency) {
    return new PurchaseRequestLineResponse(
        line.getId(),
        line.getDescription(),
        line.getQuantity().toPlainString(),
        line.getUnit(),
        MoneyDto.of(line.getUnitPrice(), currency),
        MoneyDto.of(line.getLineTotal(), currency),
        line.getCatalogItemId(),
        line.getVendorId(),
        line.isNonCatalog(),
        line.getNotes(),
        line.getCatalogReviewStatus().name());
  }
}
