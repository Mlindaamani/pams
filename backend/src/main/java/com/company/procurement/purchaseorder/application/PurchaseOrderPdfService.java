package com.company.procurement.purchaseorder.application;

import com.company.procurement.purchaseorder.domain.PurchaseOrder;
import com.company.procurement.purchaseorder.domain.PurchaseOrderLine;
import com.company.procurement.vendor.domain.Vendor;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import org.springframework.stereotype.Service;

@Service
public class PurchaseOrderPdfService {

  private static final Color PRIMARY = new Color(0x0B, 0x5C, 0xAD);
  private static final Color SECONDARY = new Color(0x00, 0x69, 0x5C);
  private static final Color BACKGROUND = new Color(0xF5, 0xF7, 0xFA);
  private static final Color BORDER = new Color(0xE0, 0xE4, 0xEA);
  private static final Color TEXT = new Color(0x1F, 0x29, 0x37);
  private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
  private static final Color WHITE = Color.WHITE;
  private static final Color SOFT_TEAL = new Color(0xE6, 0xF2, 0xF0);

  public byte[] render(PurchaseOrder order, Vendor vendor) {
    Document document = new Document(PageSize.A4, 36, 36, 128, 52);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      PdfWriter writer = PdfWriter.getInstance(document, output);
      document.open();
      drawHeader(writer, order);

      document.add(vendorAndOrderInfo(order, vendor));
      document.add(new Paragraph(" ", new Font(Font.HELVETICA, 8)));
      document.add(lineItemsTable(order));
      document.add(new Paragraph(" ", new Font(Font.HELVETICA, 10)));
      document.add(totalTable(order));
      drawFooter(writer);

      document.close();
      return output.toByteArray();
    } catch (DocumentException ex) {
      throw new IllegalStateException("Could not generate purchase order PDF", ex);
    }
  }

  private static void drawHeader(PdfWriter writer, PurchaseOrder order) {
    PdfContentByte canvas = writer.getDirectContent();
    float width = PageSize.A4.getWidth();
    float height = PageSize.A4.getHeight();
    canvas.setColorFill(PRIMARY);
    canvas.rectangle(0, height - 112, width, 112);
    canvas.fill();

    Font logo = new Font(Font.HELVETICA, 26, Font.BOLD, WHITE);
    ColumnText.showTextAligned(
        canvas, Element.ALIGN_LEFT, new Phrase("PAMS", logo), 40, height - 58, 0);
    Font tagline = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(0xC7, 0xDC, 0xF5));
    ColumnText.showTextAligned(
        canvas,
        Element.ALIGN_LEFT,
        new Phrase("PROCUREMENT & APPROVAL MANAGEMENT", tagline),
        40,
        height - 76,
        0);

    Font title = new Font(Font.HELVETICA, 15, Font.BOLD, WHITE);
    ColumnText.showTextAligned(
        canvas,
        Element.ALIGN_RIGHT,
        new Phrase("PURCHASE ORDER", title),
        width - 40,
        height - 52,
        0);
    Font number = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(0xC7, 0xDC, 0xF5));
    ColumnText.showTextAligned(
        canvas,
        Element.ALIGN_RIGHT,
        new Phrase(order.getPoNumber(), number),
        width - 40,
        height - 72,
        0);

    canvas.setColorFill(SECONDARY);
    canvas.rectangle(0, height - 116, width, 4);
    canvas.fill();
  }

  private static PdfPTable vendorAndOrderInfo(PurchaseOrder order, Vendor vendor) {
    PdfPTable table = new PdfPTable(2);
    table.setWidthPercentage(100);
    table.setWidths(new float[] {1.15f, 1f});
    table.setSpacingBefore(6);

    Paragraph vendorContent = new Paragraph();
    vendorContent.add(new Paragraph("VENDOR", labelFont(SECONDARY)));
    vendorContent.add(new Paragraph(vendor.getName(), bodyFont(TEXT, 11, Font.BOLD)));
    if (hasText(vendor.getAddressLine())) {
      vendorContent.add(new Paragraph(vendor.getAddressLine(), bodyFont(MUTED, 9, Font.NORMAL)));
    }
    if (hasText(vendor.getContactEmail())) {
      vendorContent.add(new Paragraph(vendor.getContactEmail(), bodyFont(MUTED, 9, Font.NORMAL)));
    }
    if (hasText(vendor.getContactPhone())) {
      vendorContent.add(new Paragraph(vendor.getContactPhone(), bodyFont(MUTED, 9, Font.NORMAL)));
    }
    PdfPCell vendorCell = new PdfPCell(vendorContent);
    vendorCell.setPadding(14);
    vendorCell.setBorderColor(BORDER);
    vendorCell.setBackgroundColor(BACKGROUND);
    table.addCell(vendorCell);

    Paragraph details = new Paragraph();
    details.add(new Paragraph("ORDER DETAILS", labelFont(SECONDARY)));
    details.add(detailLine("PO number", order.getPoNumber()));
    details.add(
        detailLine(
            "Issue date", order.getIssuedAt() == null ? "Draft" : order.getIssuedAt().toString()));
    details.add(
        detailLine(
            "Delivery date",
            order.getDeliveryDate() == null ? "—" : order.getDeliveryDate().toString()));
    details.add(
        detailLine(
            "Payment terms", hasText(order.getPaymentTerms()) ? order.getPaymentTerms() : "—"));
    PdfPCell detailsCell = new PdfPCell(details);
    detailsCell.setPadding(14);
    detailsCell.setBorderColor(BORDER);
    table.addCell(detailsCell);

    return table;
  }

  private static PdfPTable lineItemsTable(PurchaseOrder order) {
    PdfPTable table = new PdfPTable(new float[] {4.2f, 1.2f, 1.2f, 2f, 2f});
    table.setWidthPercentage(100);
    table.setSpacingBefore(12);

    addHeaderCell(table, "Description");
    addHeaderCell(table, "Qty");
    addHeaderCell(table, "Unit");
    addHeaderCell(table, "Unit price");
    addHeaderCell(table, "Line total");

    boolean alternate = false;
    for (PurchaseOrderLine line : order.getLines()) {
      Color background = alternate ? BACKGROUND : WHITE;
      addBodyCell(table, line.getDescription(), Element.ALIGN_LEFT, background, TEXT);
      addBodyCell(table, line.getQuantity().toPlainString(), Element.ALIGN_RIGHT, background, TEXT);
      addBodyCell(table, line.getUnit(), Element.ALIGN_LEFT, background, TEXT);
      addBodyCell(
          table, line.getUnitPrice().toPlainString(), Element.ALIGN_RIGHT, background, TEXT);
      addBodyCell(
          table, line.getLineTotal().toPlainString(), Element.ALIGN_RIGHT, background, TEXT);
      alternate = !alternate;
    }
    return table;
  }

  private static PdfPTable totalTable(PurchaseOrder order) {
    PdfPTable table = new PdfPTable(new float[] {3f, 1.4f});
    table.setWidthPercentage(100);

    PdfPCell label = new PdfPCell(new Phrase("TOTAL", labelFont(WHITE)));
    label.setBackgroundColor(SECONDARY);
    label.setHorizontalAlignment(Element.ALIGN_RIGHT);
    label.setPadding(12);
    label.setBorder(Rectangle.NO_BORDER);
    table.addCell(label);

    PdfPCell amount =
        new PdfPCell(
            new Phrase(
                "%s %s".formatted(order.getTotalAmount().toPlainString(), order.getCurrency()),
                bodyFont(SECONDARY, 13, Font.BOLD)));
    amount.setBackgroundColor(SOFT_TEAL);
    amount.setHorizontalAlignment(Element.ALIGN_RIGHT);
    amount.setPadding(12);
    amount.setBorder(Rectangle.NO_BORDER);
    table.addCell(amount);
    return table;
  }

  private static void drawFooter(PdfWriter writer) {
    PdfContentByte canvas = writer.getDirectContent();
    float width = PageSize.A4.getWidth();
    canvas.setColorStroke(BORDER);
    canvas.moveTo(40, 42);
    canvas.lineTo(width - 40, 42);
    canvas.stroke();
    Font footer = bodyFont(MUTED, 8, Font.NORMAL);
    ColumnText.showTextAligned(
        canvas,
        Element.ALIGN_LEFT,
        new Phrase("PAMS · Procurement & Approval Management System", footer),
        40,
        28,
        0);
    ColumnText.showTextAligned(
        canvas, Element.ALIGN_RIGHT, new Phrase("Generated by PAMS", footer), width - 40, 28, 0);
  }

  private static void addHeaderCell(PdfPTable table, String text) {
    PdfPCell cell = new PdfPCell(new Phrase(text.toUpperCase(), labelFont(WHITE)));
    cell.setBackgroundColor(PRIMARY);
    cell.setPadding(9);
    cell.setBorderColor(PRIMARY);
    table.addCell(cell);
  }

  private static void addBodyCell(
      PdfPTable table, String text, int alignment, Color background, Color color) {
    PdfPCell cell = new PdfPCell(new Phrase(text, bodyFont(color, 9, Font.NORMAL)));
    cell.setHorizontalAlignment(alignment);
    cell.setBackgroundColor(background);
    cell.setPadding(8);
    cell.setBorderColor(BORDER);
    table.addCell(cell);
  }

  private static Paragraph detailLine(String label, String value) {
    Paragraph paragraph = new Paragraph();
    paragraph.add(new Phrase(label + ": ", bodyFont(MUTED, 9, Font.NORMAL)));
    paragraph.add(new Phrase(value, bodyFont(TEXT, 9, Font.BOLD)));
    return paragraph;
  }

  private static Font labelFont(Color color) {
    return bodyFont(color, 8, Font.BOLD);
  }

  private static Font bodyFont(Color color, float size, int style) {
    return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
