package in.schoolapp.academics;

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
import in.schoolapp.academics.entity.Exam;
import in.schoolapp.academics.entity.ExamMark;
import in.schoolapp.academics.entity.Subject;
import in.schoolapp.school.entity.School;
import in.schoolapp.student.entity.Student;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * OpenPDF-based report card layout: school header, student info, per-subject marks table,
 * summary row (total / percentage / grade / rank). A4, single page, portrait.
 */
@Component
public class ReportCardPdfGenerator {

    public byte[] generate(
        School school, Exam exam, Student student,
        List<ExamMark> marks, Map<java.util.UUID, Subject> subjectsById,
        BigDecimal totalMax, BigDecimal totalObtained, BigDecimal percentage,
        String grade, Integer rankInClass, String remarks
    ) {
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, new Color(30, 64, 175));
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font summaryFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(22, 163, 74));
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

            Paragraph schoolName = new Paragraph(school.getName(), titleFont);
            schoolName.setAlignment(Element.ALIGN_CENTER);
            doc.add(schoolName);

            Paragraph title = new Paragraph("REPORT CARD — " + exam.getName(), subtitleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(6);
            title.setSpacingAfter(20);
            doc.add(title);

            // Student info — 4-column compact table
            PdfPTable info = new PdfPTable(4);
            info.setWidthPercentage(100);
            info.setWidths(new float[]{1, 2, 1, 2});
            addCell(info, "Name", labelFont);
            addCell(info, student.displayName(), bodyFont);
            addCell(info, "Admission #", labelFont);
            addCell(info, student.getAdmissionNumber() != null ? student.getAdmissionNumber() : "—", bodyFont);
            if (student.getDateOfBirth() != null) {
                addCell(info, "DOB", labelFont);
                addCell(info, student.getDateOfBirth().toString(), bodyFont);
                addCell(info, "", labelFont);
                addCell(info, "", bodyFont);
            }
            info.setSpacingAfter(18);
            doc.add(info);

            // Marks table
            PdfPTable marksTable = new PdfPTable(4);
            marksTable.setWidthPercentage(100);
            marksTable.setWidths(new float[]{4, 2, 2, 1});
            addHeaderCell(marksTable, "Subject", labelFont);
            addHeaderCell(marksTable, "Max", labelFont);
            addHeaderCell(marksTable, "Obtained", labelFont);
            addHeaderCell(marksTable, "Grade", labelFont);

            marks.stream()
                .sorted(Comparator.comparing(m -> {
                    Subject s = subjectsById.get(m.getSubjectId());
                    return s == null ? "" : s.getName();
                }))
                .forEach(m -> {
                    Subject subj = subjectsById.get(m.getSubjectId());
                    addCell(marksTable, subj != null ? subj.getName() : "(deleted subject)", bodyFont);
                    addCell(marksTable, m.getMaxMarks().stripTrailingZeros().toPlainString(), bodyFont);
                    String obtained = m.isAbsent() ? "AB"
                        : (m.getObtainedMarks() == null ? "—"
                           : m.getObtainedMarks().stripTrailingZeros().toPlainString());
                    addCell(marksTable, obtained, bodyFont);
                    addCell(marksTable, m.getGrade() != null ? m.getGrade() : "—", bodyFont);
                });
            marksTable.setSpacingAfter(20);
            doc.add(marksTable);

            // Summary block
            PdfPTable summary = new PdfPTable(4);
            summary.setWidthPercentage(100);
            addCell(summary, "Total",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY));
            addCell(summary,
                totalObtained.stripTrailingZeros().toPlainString() + " / "
                + totalMax.stripTrailingZeros().toPlainString(), summaryFont);
            addCell(summary, "Percentage",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY));
            addCell(summary, percentage.stripTrailingZeros().toPlainString() + "%", summaryFont);
            addCell(summary, "Grade",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY));
            addCell(summary, grade != null ? grade : "—", summaryFont);
            addCell(summary, "Class Rank",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY));
            addCell(summary, rankInClass != null ? String.valueOf(rankInClass) : "—", summaryFont);
            summary.setSpacingAfter(20);
            doc.add(summary);

            if (remarks != null && !remarks.isBlank()) {
                Paragraph remarksLabel = new Paragraph("Class Teacher's Remarks", labelFont);
                remarksLabel.setSpacingAfter(4);
                doc.add(remarksLabel);
                Paragraph remarksPara = new Paragraph(remarks, bodyFont);
                remarksPara.setSpacingAfter(30);
                doc.add(remarksPara);
            }

            Paragraph footer = new Paragraph(
                "This is a system-generated report card. — " + school.getName(), footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate report card PDF", e);
        }
    }

    private static void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(0);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private static void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new Color(241, 245, 249));
        cell.setPadding(6);
        table.addCell(cell);
    }
}
