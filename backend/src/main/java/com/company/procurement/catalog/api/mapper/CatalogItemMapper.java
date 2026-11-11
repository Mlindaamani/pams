package com.company.procurement.catalog.api.mapper;

import com.company.procurement.catalog.api.dto.CatalogItemResponse;
import com.company.procurement.catalog.domain.CatalogItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CatalogItemMapper {

  @Mapping(
      target = "defaultPrice",
      expression = "java(MoneyDto.of(catalogItem.getDefaultPrice(), catalogItem.getCurrency()))")
  CatalogItemResponse toResponse(CatalogItem catalogItem);
}
