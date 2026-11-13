package com.company.procurement.receiving.application;

import com.company.procurement.receiving.domain.VendorInvoice;
import com.company.procurement.receiving.domain.VendorInvoiceLine;
import com.company.procurement.vendor.application.VendorService;
import com.company.procurement.vendor.domain.Vendor;
import com.lowagie.text.Document;
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

/** Renders vendor-invoice PDF documents. */
@Service
public class VendorInvoicePdfService {

  private static final Color PRIMARY = new Color(0x0B, 0x5C, 0xAD);
  private static final Color SECONDARY = new Color(0x00, 0x69, 0x5C);
  private static final Color BACKGROUND = new Color(0xF5, 0xF7, 0xFA);
  private static final Color BORDER = new Color(0xE0, 0xE4, 0xEA);
  private static final Color TEXT = new Color(0x1F, 0x29, 0x37);
  private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
  private static final Color WHITE = Color.WHITE;
  private static final Color SOFT_TEAL = new Color(0xE6, 0xF2, 0xF0);

  private final VendorService vendors;

  public VendorInvoicePdfService(VendorService vendors) {
    this.vendors = vendors;
  }

  public byte[] render(VendorInvoice invoice) {
    Vendor vendor = vendors.get(invoice.getVendorId());
    Document document = new Document(PageSize.A4, 36, 36, 128, 52);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      PdfWriter writer = PdfWriter.getInstance(document, output);
      document.open();
      drawHeader(writer, invoice);
      document.add(vendorAndInvoiceInfo(invoice, vendor));
      document.add(new Paragraph(" ", new Font(Font.HELVETICA, 8)));
      document.add(lineItemsTable(invoice));
      document.add(new Paragraph(" ", new Font(Font.HELVETICA, 10)));
      document.add(totalTable(invoice));
      drawFooter(writer);
      document.close();
      return output.toByteArray();
    } catch (Exception ex) {
      throw new IllegalStateException("Could not generate vendor invoice PDF", ex);
    }
  }

  private static void drawHeader(PdfWriter writer, VendorInvoice invoice) {
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
        new Phrase("VENDOR INVOICE", title),
        width - 40,
        height - 52,
        0);
    Font reference = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(0xC7, 0xDC, 0xF5));
    ColumnText.showTextAligned(
        canvas,
        Element.ALIGN_RIGHT,
        new Phrase(
            invoice.getInternalReference() == null ? "" : invoice.getInternalReference(),
            reference),
        width - 40,
        height - 72,
        0);
    canvas.setColorFill(SECONDARY);
    canvas.rectangle(0, height - 116, width, 4);
    canvas.fill();
  }

  private static PdfPTable vendorAndInvoiceInfo(VendorInvoice invoice, Vendor vendor) {
    PdfPTable table = new PdfPTable(2);
    table.setWidthPercentage(100);
    table.setWidths(new float[] {1.15f, 1f});
    Paragraph vendorContent = new Paragraph();
    vendorContent.add(new Paragraph("BILL FROM", labelFont(SECONDARY)));
    vendorContent.add(new Paragraph(vendor.getName(), bodyFont(TEXT, 11, Font.BOLD)));
    if (hasText(vendor.getAddressLine()))
      vendorContent.add(new Paragraph(vendor.getAddressLine(), bodyFont(MUTED, 9, Font.NORMAL)));
    if (hasText(vendor.getContactEmail()))
      vendorContent.add(new Paragraph(vendor.getContactEmail(), bodyFont(MUTED, 9, Font.NORMAL)));
    if (hasText(vendor.getContactPhone()))
      vendorContent.add(new Paragraph(vendor.getContactPhone(), bodyFont(MUTED, 9, Font.NORMAL)));
    PdfPCell vendorCell = new PdfPCell(vendorContent);
    vendorCell.setPadding(14);
    vendorCell.setBorderColor(BORDER);
    vendorCell.setBackgroundColor(BACKGROUND);
    table.addCell(vendorCell);
    Paragraph details = new Paragraph();
    details.add(new Paragraph("INVOICE DETAILS", labelFont(SECONDARY)));
    details.add(detailLine("Internal reference", invoice.getInternalReference()));
    details.add(detailLine("Vendor invoice no.", invoice.getInvoiceNumber()));
    details.add(detailLine("Purchase order", invoice.getPoNumber()));
    details.add(detailLine("Invoice date", invoice.getInvoiceDate().toString()));
    details.add(
        detailLine(
            "Due date", invoice.getDueDate() == null ? "—" : invoice.getDueDate().toString()));
    details.add(detailLine("Three-way match", invoice.getStatus().name()));
    PdfPCell detailsCell = new PdfPCell(details);
    detailsCell.setPadding(14);
    detailsCell.setBorderColor(BORDER);
    table.addCell(detailsCell);
    return table;
  }

  private static PdfPTable lineItemsTable(VendorInvoice invoice) {
    PdfPTable table = new PdfPTable(new float[] {4.2f, 1.2f, 1.2f, 2f, 2f});
    table.setWidthPercentage(100);
    table.setSpacingBefore(12);
    addHeaderCell(table, "Description");
    addHeaderCell(table, "Qty");
    addHeaderCell(table, "Unit");
    addHeaderCell(table, "Unit price");
    addHeaderCell(table, "Line total");
    boolean alternate = false;
    for (VendorInvoiceLine line : invoice.getLines()) {
      Color background = alternate ? BACKGROUND : WHITE;
      addBodyCell(table, line.getDescription(), Element.ALIGN_LEFT, background);
      addBodyCell(table, line.getQuantity().toPlainString(), Element.ALIGN_RIGHT, background);
      addBodyCell(table, line.getUnit(), Element.ALIGN_LEFT, background);
      addBodyCell(table, line.getUnitPrice().toPlainString(), Element.ALIGN_RIGHT, background);
      addBodyCell(table, line.getLineTotal().toPlainString(), Element.ALIGN_RIGHT, background);
      alternate = !alternate;
    }
    return table;
  }

  private static PdfPTable totalTable(VendorInvoice invoice) {
    PdfPTable table = new PdfPTable(new float[] {3f, 1.4f});
    table.setWidthPercentage(100);
    addTotalRow(table, "Subtotal", money(invoice.getSubtotal(), invoice), false);
    addTotalRow(table, "Tax", money(invoice.getTaxAmount(), invoice), false);
    addTotalRow(table, "TOTAL", money(invoice.getTotalAmount(), invoice), true);
    return table;
  }

  private static void addTotalRow(PdfPTable table, String label, String amount, boolean emphasis) {
    PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont(emphasis ? WHITE : MUTED)));
    labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    labelCell.setPadding(emphasis ? 12 : 7);
    labelCell.setBorder(Rectangle.NO_BORDER);
    if (emphasis) labelCell.setBackgroundColor(SECONDARY);
    table.addCell(labelCell);
    PdfPCell amountCell =
        new PdfPCell(
            new Phrase(
                amount, bodyFont(emphasis ? SECONDARY : TEXT, emphasis ? 13 : 10, Font.BOLD)));
    amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    amountCell.setPadding(emphasis ? 12 : 7);
    amountCell.setBorder(Rectangle.NO_BORDER);
    if (emphasis) amountCell.setBackgroundColor(SOFT_TEAL);
    table.addCell(amountCell);
  }

  private static String money(java.math.BigDecimal amount, VendorInvoice invoice) {
    return "%s %s".formatted(amount.toPlainString(), invoice.getCurrency());
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

  private static void addBodyCell(PdfPTable table, String text, int alignment, Color background) {
    PdfPCell cell = new PdfPCell(new Phrase(text, bodyFont(TEXT, 9, Font.NORMAL)));
    cell.setHorizontalAlignment(alignment);
    cell.setBackgroundColor(background);
    cell.setPadding(8);
    cell.setBorderColor(BORDER);
    table.addCell(cell);
  }

  private static Paragraph detailLine(String label, String value) {
    Paragraph paragraph = new Paragraph();
    paragraph.add(new Phrase(label + ": ", bodyFont(MUTED, 9, Font.NORMAL)));
    paragraph.add(new Phrase(value == null ? "—" : value, bodyFont(TEXT, 9, Font.BOLD)));
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
