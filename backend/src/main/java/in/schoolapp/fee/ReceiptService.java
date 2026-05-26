package in.schoolapp.fee;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import in.schoolapp.fee.entity.FeePayment;
import in.schoolapp.school.entity.School;
import in.schoolapp.storage.FileStorageService;
import in.schoolapp.storage.dto.StoredFile;
import in.schoolapp.student.entity.Student;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Generates fee receipt numbers and PDFs.
 * <ul>
 *   <li><b>Numbering:</b> {@code REC-{year}-{6-digit-seq}}, per-tenant sequence stored in
 *       {@code schools.settings.receiptSequence} JSONB. Atomic increment via native
 *       UPDATE…RETURNING — no race between concurrent collections.</li>
 *   <li><b>PDF:</b> A4, single page, OpenPDF (LGPL). Slice 4 persists to local filesystem;
 *       Slice 5 replaces this with Cloudflare R2/S3 upload returning a CDN URL.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @PersistenceContext
    private EntityManager em;

    private final FileStorageService fileStorage;

    /**
     * Atomically increments the per-tenant receipt sequence stored in {@code schools.settings}
     * and returns the new value. Safe under concurrent collections.
     */
    @Transactional
    public long nextSequence(UUID tenantId) {
        Object result = em.createNativeQuery("""
            UPDATE schools
            SET settings = jsonb_set(
                settings,
                '{receiptSequence}',
                (COALESCE((settings->>'receiptSequence')::bigint, 0) + 1)::text::jsonb)
            WHERE id = :tenantId
            RETURNING (settings->>'receiptSequence')::bigint
            """)
            .setParameter("tenantId", tenantId)
            .getSingleResult();
        return ((Number) result).longValue();
    }

    /** Composes a human-readable receipt number: {@code REC-2026-001847}. Pure function. */
    public static String formatReceiptNumber(long sequence, int year) {
        return "REC-" + year + "-" + String.format("%06d", sequence);
    }

    /** Generates the receipt PDF bytes — suitable for storage and/or inline delivery. */
    public byte[] generatePdf(FeePayment payment, Student student, School school) {
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(30, 64, 175));
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.DARK_GRAY);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);

            // Header: school name + receipt title
            Paragraph schoolName = new Paragraph(school.getName(), titleFont);
            schoolName.setAlignment(Element.ALIGN_CENTER);
            doc.add(schoolName);

            if (school.getCity() != null || school.getState() != null) {
                String loc = (school.getCity() != null ? school.getCity() + ", " : "") + school.getState();
                Paragraph address = new Paragraph(loc, smallFont);
                address.setAlignment(Element.ALIGN_CENTER);
                address.setSpacingAfter(4);
                doc.add(address);
            }

            Paragraph title = new Paragraph("FEE RECEIPT", headerFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(8);
            title.setSpacingAfter(16);
            doc.add(title);

            // Receipt metadata table (2 columns)
            PdfPTable meta = new PdfPTable(2);
            meta.setWidthPercentage(100);
            meta.setWidths(new float[]{1, 2});
            addCell(meta, "Receipt #", labelFont);
            addCell(meta, payment.getReceiptNumber(), bodyFont);
            addCell(meta, "Date", labelFont);
            addCell(meta, payment.getPaymentDate().format(DATE_FMT), bodyFont);
            addCell(meta, "Student", labelFont);
            addCell(meta, student.displayName()
                + (student.getAdmissionNumber() != null ? "  (Adm # " + student.getAdmissionNumber() + ")" : ""),
                bodyFont);
            addCell(meta, "Payment Mode", labelFont);
            addCell(meta, payment.getPaymentMode().name(), bodyFont);
            if (payment.getNotes() != null && !payment.getNotes().isBlank()) {
                addCell(meta, "Notes", labelFont);
                addCell(meta, payment.getNotes(), bodyFont);
            }
            meta.setSpacingAfter(20);
            doc.add(meta);

            // Amount (prominent)
            Paragraph amountLabel = new Paragraph("AMOUNT PAID", labelFont);
            amountLabel.setAlignment(Element.ALIGN_CENTER);
            amountLabel.setSpacingAfter(2);
            doc.add(amountLabel);

            Paragraph amount = new Paragraph("₹ " + formatAmount(payment.getAmountPaise()),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 28, new Color(22, 163, 74)));
            amount.setAlignment(Element.ALIGN_CENTER);
            amount.setSpacingAfter(30);
            doc.add(amount);

            // Footer
            Paragraph footer = new Paragraph(
                "This is a system-generated receipt. Retain for your records.", smallFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate receipt PDF", e);
        }
    }

    /**
     * Persists the PDF via {@link FileStorageService} and returns the usable URL (HTTP served
     * by {@code FileController} on LOCAL; presigned S3 URL on S3). Swapping providers requires
     * no change here.
     */
    public String storeReceipt(UUID tenantId, UUID paymentId, byte[] pdfBytes) {
        String key = "receipts/" + tenantId + "/" + paymentId + ".pdf";
        StoredFile stored = fileStorage.store(key, pdfBytes, "application/pdf");
        log.debug("Stored receipt tenantId={} paymentId={} bytes={} at {}",
            tenantId, paymentId, stored.sizeBytes(), stored.url());
        return stored.url();
    }

    private static void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(0);
        cell.setPadding(6);
        table.addCell(cell);
    }

    /** Converts paise (integer) to ₹ with 2 decimal places — never use double for money. */
    public static String formatAmount(long paise) {
        long rupees = paise / 100;
        long remainder = Math.abs(paise % 100);
        return String.format("%,d.%02d", rupees, remainder);
    }
}
