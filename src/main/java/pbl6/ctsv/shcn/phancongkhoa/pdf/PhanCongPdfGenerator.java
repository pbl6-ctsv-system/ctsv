package pbl6.ctsv.shcn.phancongkhoa.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import pbl6.ctsv.shared.ca.CaPdfPreflight;
import pbl6.ctsv.shcn.phancongkhoa.dto.PhanCongKhoaDtos.NoiDungHanhChinhDTO;
import pbl6.ctsv.shcn.phancongkhoa.exception.PhanCongKhoaFailure;

/**
 * Renders the 4-column faculty GVCN assignment PDF via Apache PDFBox 3.0.8.
 * Faculty heading is always fixed from server-side Foundation unit name; teacher code is never printed on PDF.
 */
public final class PhanCongPdfGenerator {
    private static final Locale VI = Locale.forLanguageTag("vi");
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final float PAGE_W = PDRectangle.A4.getWidth();   // 595.28
    private static final float PAGE_H = PDRectangle.A4.getHeight();  // 841.89
    private static final float MARGIN_L = 54f;
    private static final float MARGIN_R = 54f;
    private static final float MARGIN_T = 48f;
    private static final float MARGIN_B = 48f;
    private static final float CONTENT_W = PAGE_W - MARGIN_L - MARGIN_R;

    public record PdfRow(int stt, String hoTenGvcn, String maLop, String ghiChu) {}

    public record PdfSpec(
            String tenKhoaFixed,
            String namHoc,
            int phienBan,
            OffsetDateTime hieuLucTu,
            OffsetDateTime hieuLucDen,
            NoiDungHanhChinhDTO noiDungHanhChinh,
            List<PdfRow> rows
    ) {
        public PdfSpec {
            Objects.requireNonNull(tenKhoaFixed, "tenKhoaFixed");
            Objects.requireNonNull(namHoc, "namHoc");
            Objects.requireNonNull(hieuLucTu, "hieuLucTu");
            Objects.requireNonNull(noiDungHanhChinh, "noiDungHanhChinh");
            rows = List.copyOf(rows);
        }
    }

    public static String formatFacultyHeading(String tenKhoa) {
        String clean = tenKhoa == null ? "" : tenKhoa.strip().replaceAll("\\s+", " ");
        if (clean.isEmpty()) throw PhanCongKhoaFailure.unavailable();
        String upper = clean.toUpperCase(VI);
        return (upper.startsWith("KHOA ") || upper.equals("KHOA")) ? upper : "KHOA " + upper;
    }

    public static NoiDungHanhChinhDTO defaultMetadata(String tenKhoa, String namHoc, String hoTenNguoiKy, Instant now) {
        String cleanFaculty = tenKhoa == null ? "Khoa" : tenKhoa.strip();
        String facultyPhrase = cleanFaculty.toLowerCase(VI).startsWith("khoa ") ? cleanFaculty : "Khoa " + cleanFaculty;
        var date = now.atZone(VN_ZONE).toLocalDate();
        String diaDanh = String.format("Đà Nẵng, ngày %02d tháng %02d năm %04d",
                date.getDayOfMonth(), date.getMonthValue(), date.getYear());
        String loiDan = String.format(
                "Căn cứ kế hoạch tổ chức Sinh hoạt chủ nhiệm và công tác quản lý lớp sinh viên năm học %s, %s lập danh sách phân công Giáo viên chủ nhiệm (GVCN) cho các lớp thuộc Khoa quản lý như sau:",
                namHoc, facultyPhrase);
        return new NoiDungHanhChinhDTO(
                "Phòng Công tác Sinh viên",
                "",
                loiDan,
                diaDanh,
                hoTenNguoiKy == null ? "" : hoTenNguoiKy.strip(),
                ""
        );
    }

    public byte[] render(PdfSpec spec) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream(65536)) {
            Fonts fonts = loadFonts(doc);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            float y = PAGE_H - MARGIN_T;

            float leftCenterX = MARGIN_L + 105f;
            float rightCenterX = PAGE_W - MARGIN_R - 140f;

            drawCentered(cs, fonts.regular, 11f, "TRƯỜNG ĐẠI HỌC BÁCH KHOA", leftCenterX, y);
            drawCentered(cs, fonts.bold, 11f, "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM", rightCenterX, y);
            y -= 15f;

            String facultyHeader = formatFacultyHeading(spec.tenKhoaFixed());
            List<String> facultyLines = wrap(fonts.bold, 11f, facultyHeader, 210f, 0f);
            for (int i = 0; i < facultyLines.size(); i++) {
                drawCentered(cs, fonts.bold, 11f, facultyLines.get(i), leftCenterX, y - i * 13f);
            }
            drawCentered(cs, fonts.bold, 11.5f, "Độc lập - Tự do - Hạnh phúc", rightCenterX, y);

            float leftUnderlineY = y - (facultyLines.size() - 1) * 13f - 3.5f;
            cs.setLineWidth(0.75f);
            cs.setStrokingColor(Color.BLACK);
            cs.moveTo(leftCenterX - 42f, leftUnderlineY);
            cs.lineTo(leftCenterX + 42f, leftUnderlineY);
            cs.stroke();

            cs.moveTo(rightCenterX - 66f, y - 3.5f);
            cs.lineTo(rightCenterX + 66f, y - 3.5f);
            cs.stroke();

            y = Math.min(leftUnderlineY, y - 3.5f) - 14f;
            drawCentered(cs, fonts.italic, 11f, sanitizeText(spec.noiDungHanhChinh().diaDanhNgayThang()), rightCenterX, y);

            y -= 28f;
            drawCentered(cs, fonts.bold, 13.5f, "DANH SÁCH PHÂN CÔNG GIÁO VIÊN CHỦ NHIỆM", PAGE_W / 2f, y);
            y -= 17f;
            drawCentered(cs, fonts.bold, 11.5f, "Năm học: " + sanitizeText(spec.namHoc()) + " (Phiên bản " + spec.phienBan() + ")", PAGE_W / 2f, y);

            String canCu = sanitizeText(spec.noiDungHanhChinh().canCu());
            if (!canCu.isEmpty()) {
                y -= 16f;
                for (String line : wrap(fonts.italic, 11f, canCu, CONTENT_W - 24f, 0f)) {
                    drawCentered(cs, fonts.italic, 11f, line, PAGE_W / 2f, y);
                    y -= 14f;
                }
            }

            y -= 22f;
            float indentX = MARGIN_L + 22f;
            drawText(cs, fonts.bold, 11.5f, "Kính gửi:", indentX, y);
            float salW = fonts.bold.getStringWidth("Kính gửi:  ") / 1000f * 11.5f;
            String kinhGui = sanitizeText(spec.noiDungHanhChinh().kinhGui());
            if (!kinhGui.endsWith(".")) kinhGui = kinhGui + ".";
            List<String> salLines = wrap(fonts.boldItalic, 11.5f, kinhGui, CONTENT_W - 22f - salW, 0f);
            for (int i = 0; i < salLines.size(); i++) {
                drawText(cs, fonts.boldItalic, 11.5f, salLines.get(i), indentX + salW, y);
                if (i < salLines.size() - 1) y -= 15f;
            }

            y -= 20f;
            List<String> introLines = wrap(fonts.regular, 11.5f, sanitizeText(spec.noiDungHanhChinh().loiDan()), CONTENT_W, 22f);
            for (int i = 0; i < introLines.size(); i++) {
                float xStart = (i == 0) ? MARGIN_L + 22f : MARGIN_L;
                drawText(cs, fonts.regular, 11.5f, introLines.get(i), xStart, y);
                y -= 16f;
            }

            drawText(cs, fonts.italic, 11f, formatEffectivePeriod(spec.hieuLucTu(), spec.hieuLucDen()), MARGIN_L, y);
            y -= 14f;

            // 4-column table: STT | Họ tên GVCN | Lớp | Ghi chú (NO Mã GVCN on PDF)
            float[] colWidths = {40f, 185f, 105f, CONTENT_W - 40f - 185f - 105f};
            String[] headers = {"STT", "Họ tên GVCN", "Lớp", "Ghi chú"};
            float headerH = 26f;

            y = drawTableHeader(cs, fonts.bold, 11f, MARGIN_L, y, colWidths, headers, headerH);

            for (PdfRow row : spec.rows()) {
                String teacher = sanitizeText(row.hoTenGvcn());
                String cls = sanitizeText(row.maLop());
                String note = sanitizeText(row.ghiChu());
                List<String> nameLines = wrap(fonts.regular, 11f, teacher.isEmpty() ? "—" : teacher, colWidths[1] - 10f, 0f);
                List<String> classLines = wrap(fonts.regular, 11f, cls, colWidths[2] - 10f, 0f);
                List<String> noteLines = note.isEmpty() ? List.of("") : wrap(fonts.regular, 10.5f, note, colWidths[3] - 10f, 0f);
                int maxLines = Math.max(1, Math.max(nameLines.size(), Math.max(classLines.size(), noteLines.size())));
                float rowH = Math.max(22f, maxLines * 14f + 8f);

                if (y - rowH < MARGIN_B) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = PAGE_H - MARGIN_T;
                    y = drawTableHeader(cs, fonts.bold, 11f, MARGIN_L, y, colWidths, headers, headerH);
                }

                float cellX = MARGIN_L;
                float rowBottom = y - rowH;
                drawCellBox(cs, cellX, rowBottom, CONTENT_W, rowH);
                float curX = cellX;
                for (int c = 0; c < colWidths.length - 1; c++) {
                    curX += colWidths[c];
                    cs.moveTo(curX, rowBottom);
                    cs.lineTo(curX, y);
                    cs.stroke();
                }

                float firstBaseline = y - 15f;
                drawCentered(cs, fonts.regular, 11f, String.valueOf(row.stt()), cellX + colWidths[0] / 2f, firstBaseline);
                for (int i = 0; i < nameLines.size(); i++) {
                    drawText(cs, fonts.regular, 11f, nameLines.get(i), cellX + colWidths[0] + 5f, firstBaseline - i * 14f);
                }
                for (int i = 0; i < classLines.size(); i++) {
                    drawCentered(cs, fonts.regular, 11f, classLines.get(i), cellX + colWidths[0] + colWidths[1] + colWidths[2] / 2f, firstBaseline - i * 14f);
                }
                for (int i = 0; i < noteLines.size(); i++) {
                    if (!noteLines.get(i).isEmpty()) {
                        drawText(cs, fonts.regular, 10.5f, noteLines.get(i), cellX + colWidths[0] + colWidths[1] + colWidths[2] + 5f, firstBaseline - i * 14f);
                    }
                }
                y = rowBottom;
            }

            String footerNote = sanitizeText(spec.noiDungHanhChinh().ghiChuChanTrang());
            List<String> footerLines = footerNote.isEmpty() ? List.of() : wrap(fonts.italic, 10.5f, "Ghi chú: " + footerNote, CONTENT_W, 0f);
            float neededAfterTable = 165f + footerLines.size() * 14f;
            if (y - neededAfterTable < MARGIN_B) {
                cs.close();
                page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                cs = new PDPageContentStream(doc, page);
                y = PAGE_H - MARGIN_T;
            }

            y -= 18f;
            drawText(cs, fonts.italic, 11f, "Tổng số lớp đã phân công: " + spec.rows().size() + " lớp.", MARGIN_L, y);
            for (String fLine : footerLines) {
                y -= 14f;
                drawText(cs, fonts.italic, 10.5f, fLine, MARGIN_L, y);
            }

            // Single fixed signer block: TRƯỞNG KHOA
            y -= 28f;
            float signCenterX = PAGE_W - MARGIN_R - 105f;
            drawCentered(cs, fonts.bold, 11.5f, "TRƯỞNG KHOA", signCenterX, y);
            y -= 15f;
            drawCentered(cs, fonts.italic, 10.5f, "(Ký số và ghi rõ họ tên)", signCenterX, y);

            // 78pt vertical blank space reserved for CA DUT signature box
            y -= 78f;
            String signerName = sanitizeText(spec.noiDungHanhChinh().hoTenNguoiKy());
            if (!signerName.isEmpty()) {
                drawCentered(cs, fonts.bold, 11.5f, signerName, signCenterX, y);
            }

            cs.close();
            doc.save(out);
            byte[] bytes = out.toByteArray();
            if (bytes.length == 0 || bytes.length > CaPdfPreflight.MAX_BYTES) {
                throw PhanCongKhoaFailure.conflict("SHCN_PDF_VUOT_GIOI_HAN");
            }
            return bytes;
        } catch (IOException e) {
            throw PhanCongKhoaFailure.unavailable();
        }
    }

    private static String formatEffectivePeriod(OffsetDateTime tu, OffsetDateTime den) {
        String start = tu.atZoneSameInstant(VN_ZONE).toLocalDate().format(VN_DATE);
        if (den == null) {
            return "Thời gian đề nghị hiệu lực: từ ngày " + start + " (chưa đặt mốc kết thúc).";
        }
        String end = den.atZoneSameInstant(VN_ZONE).toLocalDate().format(VN_DATE);
        return "Thời gian đề nghị hiệu lực: từ ngày " + start + " đến ngày " + end + ".";
    }

    private static String sanitizeText(String value) {
        if (value == null) return "";
        return value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').replaceAll("\\s+", " ").strip();
    }

    private record Fonts(PDType0Font regular, PDType0Font bold, PDType0Font italic, PDType0Font boldItalic) {}

    private static Fonts loadFonts(PDDocument doc) throws IOException {
        String[][] candidates = {
            {"C:/Windows/Fonts/times.ttf", "C:/Windows/Fonts/timesbd.ttf", "C:/Windows/Fonts/timesi.ttf", "C:/Windows/Fonts/timesbi.ttf"},
            {"/usr/share/fonts/truetype/liberation/LiberationSerif-Regular.ttf", "/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf",
             "/usr/share/fonts/truetype/liberation/LiberationSerif-Italic.ttf", "/usr/share/fonts/truetype/liberation/LiberationSerif-BoldItalic.ttf"},
            {"/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf",
             "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Italic.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSerif-BoldItalic.ttf"},
            {"/System/Library/Fonts/Supplemental/Times New Roman.ttf", "/System/Library/Fonts/Supplemental/Times New Roman Bold.ttf",
             "/System/Library/Fonts/Supplemental/Times New Roman Italic.ttf", "/System/Library/Fonts/Supplemental/Times New Roman Bold Italic.ttf"}
        };
        for (String[] set : candidates) {
            File r = new File(set[0]);
            if (r.isFile()) {
                File b = new File(set[1]).isFile() ? new File(set[1]) : r;
                File i = new File(set[2]).isFile() ? new File(set[2]) : r;
                File bi = new File(set[3]).isFile() ? new File(set[3]) : b;
                return new Fonts(
                        PDType0Font.load(doc, r),
                        PDType0Font.load(doc, b),
                        PDType0Font.load(doc, i),
                        PDType0Font.load(doc, bi)
                );
            }
        }
        throw new IOException("No Unicode serif TrueType font found on host");
    }

    private static float drawTableHeader(PDPageContentStream cs, PDType0Font fontBold, float size,
                                         float x, float topY, float[] widths, String[] headers, float h) throws IOException {
        float bottomY = topY - h;
        drawCellBox(cs, x, bottomY, CONTENT_W, h);
        float curX = x;
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) {
                cs.moveTo(curX, bottomY);
                cs.lineTo(curX, topY);
                cs.stroke();
            }
            drawCentered(cs, fontBold, size, headers[i], curX + widths[i] / 2f, topY - 17f);
            curX += widths[i];
        }
        return bottomY;
    }

    private static void drawCellBox(PDPageContentStream cs, float x, float y, float w, float h) throws IOException {
        cs.setLineWidth(0.75f);
        cs.setStrokingColor(Color.BLACK);
        cs.addRect(x, y, w, h);
        cs.stroke();
    }

    private static void drawText(PDPageContentStream cs, PDType0Font font, float size, String text, float x, float y) throws IOException {
        if (text == null || text.isEmpty()) return;
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private static void drawCentered(PDPageContentStream cs, PDType0Font font, float size, String text, float centerX, float y) throws IOException {
        if (text == null || text.isEmpty()) return;
        float w = font.getStringWidth(text) / 1000f * size;
        drawText(cs, font, size, text, centerX - w / 2f, y);
    }

    private static List<String> wrap(PDType0Font font, float size, String text, float maxWidth, float firstLineIndent) throws IOException {
        if (text == null || text.isBlank()) return List.of("");
        List<String> lines = new ArrayList<>();
        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder();
        boolean first = true;
        for (String word : words) {
            float limit = first ? (maxWidth - firstLineIndent) : maxWidth;
            if (font.getStringWidth(word) / 1000f * size > limit) {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                    first = false;
                    limit = maxWidth;
                }
                StringBuilder chunk = new StringBuilder();
                for (int idx = 0; idx < word.length(); idx++) {
                    String next = chunk.toString() + word.charAt(idx);
                    if (font.getStringWidth(next) / 1000f * size > limit && !chunk.isEmpty()) {
                        lines.add(chunk.toString());
                        chunk = new StringBuilder().append(word.charAt(idx));
                        limit = maxWidth;
                    } else {
                        chunk.append(word.charAt(idx));
                    }
                }
                current = chunk;
                continue;
            }
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.getStringWidth(candidate) / 1000f * size > limit && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
                first = false;
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }
}
