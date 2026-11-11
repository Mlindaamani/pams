package com.company.procurement.receiving.api.mapper;

import com.company.procurement.receiving.api.dto.VendorInvoiceLineResponse;
import com.company.procurement.receiving.api.dto.VendorInvoiceResponse;
import com.company.procurement.receiving.domain.VendorInvoice;
import com.company.procurement.receiving.domain.VendorInvoiceLine;
import com.company.procurement.shared.dto.MoneyDto;
import org.springframework.stereotype.Component;

@Component
public class VendorInvoiceMapper {

  public VendorInvoiceResponse toResponse(VendorInvoice invoice) {
    return map(invoice, true);
  }

  public VendorInvoiceResponse toSummary(VendorInvoice invoice) {
    return map(invoice, false);
  }

  private VendorInvoiceResponse map(VendorInvoice invoice, boolean includeLines) {
    String currency = invoice.getCurrency();
    return new VendorInvoiceResponse(
        invoice.getId(),
        invoice.getPurchaseOrderId(),
        invoice.getPoNumber(),
        invoice.getVendorId(),
        invoice.getInvoiceNumber(),
        invoice.getInternalReference(),
        invoice.getInvoiceDate(),
        invoice.getDueDate(),
        currency,
        MoneyDto.of(invoice.getSubtotal(), currency),
        MoneyDto.of(invoice.getTaxAmount(), currency),
        MoneyDto.of(invoice.getTotalAmount(), currency),
        MoneyDto.of(invoice.getPaidAmount(), currency),
        invoice.getStatus(),
        invoice.getTolerancePercent(),
        invoice.getNotes(),
        invoice.getApprovedAt(),
        invoice.getApprovedBy(),
        invoice.getResolutionNote(),
        invoice.getResolvedAt(),
        invoice.getResolvedBy(),
        invoice.getCreatedAt(),
        invoice.getUpdatedAt(),
        invoice.getVersion(),
        includeLines
            ? invoice.getLines().stream().map(line -> toLine(line, currency)).toList()
            : null);
  }

  private static VendorInvoiceLineResponse toLine(VendorInvoiceLine line, String currency) {
    return new VendorInvoiceLineResponse(
        line.getPurchaseOrderLineId(),
        line.getDescription(),
        line.getUnit(),
        line.getQuantity().toPlainString(),
        MoneyDto.of(line.getUnitPrice(), currency),
        MoneyDto.of(line.getLineTotal(), currency),
        line.isMatched(),
        line.getPriceVariancePercent(),
        line.getMatchMessage());
  }
}
