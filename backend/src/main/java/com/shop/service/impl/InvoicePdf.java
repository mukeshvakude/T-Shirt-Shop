package com.shop.service.impl;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.shop.config.AppProperties;
import com.shop.dto.order.OrderResponse;
import com.shop.dto.pricing.PriceLine;

import lombok.RequiredArgsConstructor;

/**
 * A4 invoice for an order, built from the order snapshot (never from current prices). Amounts are
 * printed as "INR 1,047.00" because the standard PDF fonts have no rupee sign.
 */
@Component
@RequiredArgsConstructor
public class InvoicePdf {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final Color LINE = new Color(225, 225, 225);
    private static final Color MUTED = new Color(110, 110, 110);

    private final AppProperties properties;

    public static String invoiceNumber(String orderNumber) {
        return "INV-" + orderNumber.substring(3); // TS-20261103-123456 -> INV-20261103-123456
    }

    public byte[] render(OrderResponse o, Instant invoiceDate) {
        AppProperties.Business seller = properties.business() != null ? properties.business()
                : new AppProperties.Business(null, null, null, null, null, null);
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9.5f);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8f, MUTED);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(doc, out);
        doc.addTitle("Invoice " + invoiceNumber(o.orderNumber()));
        doc.addCreator(seller.displayName());
        doc.open();

        // Header: seller and invoice details
        PdfPTable head = table(new float[] {60, 40});
        PdfPCell sellerCell = cell(null, Rectangle.NO_BORDER);
        sellerCell.addElement(new Paragraph(seller.displayName(), title));
        addLine(sellerCell, seller.legalName(), normal);
        addLine(sellerCell, seller.address(), normal);
        addLine(sellerCell, seller.email(), normal);
        addLine(sellerCell, seller.phone(), normal);
        if (seller.hasGstin()) addLine(sellerCell, "GSTIN: " + seller.gstin(), bold);
        head.addCell(sellerCell);
        PdfPCell meta = cell(null, Rectangle.NO_BORDER);
        Paragraph kind = new Paragraph(seller.hasGstin() ? "TAX INVOICE" : "INVOICE", title);
        kind.setAlignment(Element.ALIGN_RIGHT);
        meta.addElement(kind);
        for (String line : new String[] {"Invoice no: " + invoiceNumber(o.orderNumber()), "Invoice date: " + date(invoiceDate),
                "Order no: " + o.orderNumber(), "Order date: " + date(o.placedAt()),
                "Payment: " + ("COD".equals(o.paymentMethod()) ? "Cash on Delivery" : "Online (Razorpay)")
                        + ("SUCCESS".equals(o.paymentStatus()) ? ", paid" : ", due on delivery")}) {
            Paragraph p = new Paragraph(line, normal);
            p.setAlignment(Element.ALIGN_RIGHT);
            meta.addElement(p);
        }
        head.addCell(meta);
        doc.add(head);

        // Bill / ship to
        OrderResponse.Address a = o.shippingAddress();
        PdfPTable parties = table(new float[] {50, 50});
        parties.setSpacingBefore(18);
        PdfPCell bill = cell(null, Rectangle.NO_BORDER);
        bill.addElement(new Paragraph("Billed to", bold));
        addLine(bill, o.contact().name(), normal);
        addLine(bill, o.contact().email(), normal);
        addLine(bill, o.contact().phone(), normal);
        parties.addCell(bill);
        PdfPCell ship = cell(null, Rectangle.NO_BORDER);
        ship.addElement(new Paragraph("Shipped to", bold));
        addLine(ship, a.fullName(), normal);
        addLine(ship, a.line1() + (a.line2() == null ? "" : ", " + a.line2()), normal);
        addLine(ship, a.landmark(), normal);
        addLine(ship, a.city() + ", " + a.state() + " " + a.pincode(), normal);
        addLine(ship, "Phone " + a.phone(), normal);
        parties.addCell(ship);
        doc.add(parties);

        // Items
        PdfPTable items = table(new float[] {46, 14, 8, 16, 16});
        items.setSpacingBefore(18);
        for (String h : new String[] {"Item", "Size / colour", "Qty", "Unit price", "Amount"}) {
            PdfPCell c = cell(new Phrase(h, bold), Rectangle.BOTTOM);
            c.setPaddingBottom(6);
            if (!"Item".equals(h) && !"Size / colour".equals(h)) c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            items.addCell(c);
        }
        for (OrderResponse.Item i : o.items()) {
            PdfPCell name = cell(null, Rectangle.BOTTOM);
            name.addElement(new Paragraph(i.name(), normal));
            if (i.designCode() != null) {
                StringBuilder sb = new StringBuilder("Custom design " + i.designCode() + ": T-Shirt " + money(i.unitBasePrice()));
                for (PriceLine c : i.charges()) sb.append(" + ").append(c.label()).append(" ").append(money(c.amount()));
                name.addElement(new Paragraph(sb.toString(), small));
            }
            name.addElement(new Paragraph("SKU " + i.sku(), small));
            items.addCell(name);
            items.addCell(cell(new Phrase(i.sizeCode() + " / " + i.colorName(), normal), Rectangle.BOTTOM));
            items.addCell(right(String.valueOf(i.quantity()), normal));
            items.addCell(right(money(i.unitBasePrice().add(i.unitCustomizationCharge())), normal));
            items.addCell(right(money(i.lineTotal()), normal));
        }
        doc.add(items);

        // Totals
        PdfPTable totals = table(new float[] {70, 30});
        totals.setSpacingBefore(10);
        total(totals, "Items", o.itemsTotal(), normal);
        if (o.customizationTotal().signum() > 0) total(totals, "Customization charges", o.customizationTotal(), normal);
        total(totals, "Shipping", o.shipping(), normal);
        if (o.discount().signum() > 0) total(totals, "Discount" + (o.couponCode() == null ? "" : " (" + o.couponCode() + ")"),
                o.discount().negate(), normal);
        total(totals, "Total", o.total(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11));
        doc.add(totals);

        Paragraph note = new Paragraph("All prices are inclusive of GST. This is a computer-generated invoice and needs no signature.",
                small);
        note.setSpacingBefore(24);
        doc.add(note);
        doc.close();
        return out.toByteArray();
    }

    private static PdfPTable table(float[] widths) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        return t;
    }

    private static PdfPCell cell(Phrase phrase, int border) {
        PdfPCell c = phrase == null ? new PdfPCell() : new PdfPCell(phrase);
        c.setBorder(border);
        c.setBorderColor(LINE);
        c.setPadding(4);
        c.setPaddingBottom(7);
        return c;
    }

    private static PdfPCell right(String text, Font font) {
        PdfPCell c = cell(new Phrase(text, font), Rectangle.BOTTOM);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private static void total(PdfPTable t, String label, BigDecimal amount, Font font) {
        PdfPCell l = cell(new Phrase(label, font), Rectangle.NO_BORDER);
        l.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(l);
        PdfPCell v = cell(new Phrase(money(amount), font), Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(v);
    }

    private static void addLine(PdfPCell cell, String text, Font font) {
        if (text != null && !text.isBlank()) cell.addElement(new Paragraph(text, font));
    }

    static String money(BigDecimal amount) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return (amount.signum() < 0 ? "- INR " : "INR ") + f.format(amount.abs());
    }

    private static String date(Instant instant) {
        return instant == null ? "" : DATE.format(instant.atZone(INDIA));
    }
}
