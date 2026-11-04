package com.college.result.service;

import com.college.result.config.AppProperties;
import com.college.result.dto.ResultDtos.ResultDetail;
import com.college.result.dto.ResultDtos.SubjectResult;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

/** Renders a printable A4 mark sheet for a published result. */
@Service
public class MarksheetPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneOffset.UTC);
    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.DARK_GRAY);

    private final String collegeName;

    public MarksheetPdfService(AppProperties props) {
        this.collegeName = props.collegeName();
    }

    public byte[] render(ResultDetail r) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph college = new Paragraph(collegeName, TITLE);
            college.setAlignment(Element.ALIGN_CENTER);
            doc.add(college);
            Paragraph title = new Paragraph("STATEMENT OF MARKS", SUBTITLE);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(12);
            doc.add(title);

            PdfPTable info = new PdfPTable(4);
            info.setWidthPercentage(100);
            info.setWidths(new float[] {1.2f, 2.3f, 1.2f, 2.3f});
            infoRow(info, "Name", r.studentName(), "Roll number", r.rollNumber());
            infoRow(info, "Department", r.departmentName(), "Batch", String.valueOf(r.batchYear()));
            infoRow(info, "Examination", r.sessionName(), "Academic year", r.academicYear());
            infoRow(info, "Semester", String.valueOf(r.semesterNumber()), "Result", r.resultStatus());
            info.setSpacingAfter(12);
            doc.add(info);

            PdfPTable table = new PdfPTable(9);
            table.setWidthPercentage(100);
            table.setWidths(new float[] {1.3f, 3.2f, 0.8f, 0.9f, 0.9f, 0.9f, 0.8f, 0.8f, 0.9f});
            for (String h : new String[] {"Code", "Subject", "Credits", "Internal", "External", "Total", "Grade",
                    "GP", "Result"}) {
                PdfPCell c = new PdfPCell(new Phrase(h, BOLD));
                c.setBackgroundColor(new Color(230, 233, 240));
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                c.setPadding(4);
                table.addCell(c);
            }
            for (SubjectResult s : r.subjects()) {
                cell(table, s.subjectCode(), Element.ALIGN_LEFT);
                cell(table, s.subjectName(), Element.ALIGN_LEFT);
                cell(table, plain(s.credits()), Element.ALIGN_CENTER);
                cell(table, plain(s.internalMarks()), Element.ALIGN_CENTER);
                cell(table, plain(s.externalMarks()), Element.ALIGN_CENTER);
                cell(table, plain(s.totalMarks()) + "/" + plain(s.maxMarks()), Element.ALIGN_CENTER);
                cell(table, s.grade(), Element.ALIGN_CENTER);
                cell(table, s.gradePoint().toPlainString(), Element.ALIGN_CENTER);
                cell(table, s.passed() ? "Pass" : "Fail", Element.ALIGN_CENTER);
            }
            table.setSpacingAfter(12);
            doc.add(table);

            PdfPTable summary = new PdfPTable(4);
            summary.setWidthPercentage(100);
            summary.setWidths(new float[] {1.2f, 1f, 1.2f, 1f});
            infoRow(summary, "Total credits", plain(r.totalCredits()), "Earned credits", plain(r.earnedCredits()));
            infoRow(summary, "SGPA", r.sgpa().toPlainString(), "CGPA", r.cgpa().toPlainString());
            summary.setSpacingAfter(12);
            doc.add(summary);

            if (r.gradingRuleIsSample()) {
                doc.add(new Paragraph("Note: the grading rule \"" + r.gradingRule() + "\" is a sample placeholder and "
                        + "does not represent an official regulation.", SMALL));
            }
            doc.add(new Paragraph("Published on " + DATE.format(r.publishedAt()) + (r.revision() > 1
                    ? " (revision " + r.revision() + ")" : "") + ". Computer generated statement; verify with the "
                    + "examination office.", SMALL));
            doc.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not render mark sheet", e);
        }
    }

    private void infoRow(PdfPTable t, String l1, String v1, String l2, String v2) {
        t.addCell(borderless(l1, BOLD));
        t.addCell(borderless(v1, NORMAL));
        t.addCell(borderless(l2, BOLD));
        t.addCell(borderless(v2, NORMAL));
    }

    private PdfPCell borderless(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setBorder(0);
        c.setPadding(3);
        return c;
    }

    private void cell(PdfPTable t, String text, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, NORMAL));
        c.setHorizontalAlignment(align);
        c.setPadding(4);
        t.addCell(c);
    }

    private static String plain(java.math.BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
