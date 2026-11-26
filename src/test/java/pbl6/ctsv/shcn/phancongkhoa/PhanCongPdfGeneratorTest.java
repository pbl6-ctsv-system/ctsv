package pbl6.ctsv.shcn.phancongkhoa;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import pbl6.ctsv.shared.ca.CaFailure;
import pbl6.ctsv.shared.ca.CaPdfPreflight;
import pbl6.ctsv.shcn.phancongkhoa.dto.PhanCongKhoaDtos.NoiDungHanhChinhDTO;
import pbl6.ctsv.shcn.phancongkhoa.pdf.PhanCongPdfGenerator;
import pbl6.ctsv.shcn.phancongkhoa.pdf.PhanCongPdfGenerator.PdfRow;
import pbl6.ctsv.shcn.phancongkhoa.pdf.PhanCongPdfGenerator.PdfSpec;
import static org.assertj.core.api.Assertions.*;

class PhanCongPdfGeneratorTest {
    private final PhanCongPdfGenerator generator = new PhanCongPdfGenerator();

    @Test
    void facultyHeadingNormalizesWithoutDuplicatingKhoaPrefix() {
        assertThat(PhanCongPdfGenerator.formatFacultyHeading("Khoa Công nghệ thông tin"))
                .isEqualTo("KHOA CÔNG NGHỆ THÔNG TIN");
        assertThat(PhanCongPdfGenerator.formatFacultyHeading("Công nghệ thông tin"))
                .isEqualTo("KHOA CÔNG NGHỆ THÔNG TIN");
    }

    @Test
    void rendersFourColumnsSingleHeadSignerFixedFacultyAndValidatesPreflight() throws Exception {
        NoiDungHanhChinhDTO meta = PhanCongPdfGenerator.defaultMetadata(
                "Khoa Công nghệ thông tin",
                "2026-2027",
                "PGS.TS. Nguyễn Văn Trưởng",
                Instant.parse("2026-11-26T02:00:00Z"));
        List<PdfRow> rows = List.of(
                new PdfRow(1, "TS. Nguyễn Thế Xuân Ly", "22T_DT1", "Phân công đầu năm"),
                new PdfRow(2, "ThS. Đỗ Thị Tuyết Hoa", "22T_DT2", null)
        );
        byte[] pdf = generator.render(new PdfSpec(
                "Khoa Công nghệ thông tin",
                "2026-2027",
                1,
                OffsetDateTime.parse("2026-09-01T00:00:00+07:00"),
                null,
                meta,
                rows));

        assertThat(pdf).isNotEmpty();
        try (var doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("KHOA CÔNG NGHỆ THÔNG TIN")
                    .doesNotContain("KHOA KHOA")
                    .contains("DANH SÁCH PHÂN CÔNG GIÁO VIÊN CHỦ NHIỆM")
                    .contains("Họ tên GVCN")
                    .doesNotContain("Mã GVCN")
                    .contains("TS. Nguyễn Thế Xuân Ly")
                    .contains("22T_DT1")
                    .contains("TRƯỞNG KHOA")
                    .contains("PGS.TS. Nguyễn Văn Trưởng");
        }

        // Valid signature box inside A4 page 1 passes CaPdfPreflight; out-of-bounds box fails.
        var validInput = CaPdfPreflight.inspect(pdf, new CaPdfPreflight.Box(1, 330, 90, 180, 65));
        assertThat(validInput.sha256()).hasSize(64);
        assertThatThrownBy(() -> CaPdfPreflight.inspect(pdf, new CaPdfPreflight.Box(1, 520, 90, 180, 65)))
                .isInstanceOf(CaFailure.class);
    }

    @Test
    void multiPageAssignmentListPaginatesAndRepeatsTableHeader() throws Exception {
        List<PdfRow> manyRows = new ArrayList<>();
        for (int i = 1; i <= 38; i++) {
            manyRows.add(new PdfRow(i, "Giảng viên chủ nhiệm mẫu số " + i, "23T_DT" + i,
                    i % 3 == 0 ? "Ghi chú dài kiểm tra tự động xuống dòng trong bảng PDF" : ""));
        }
        NoiDungHanhChinhDTO meta = new NoiDungHanhChinhDTO(
                "Phòng Công tác Sinh viên",
                "Căn cứ Kế hoạch số 12/KH-ĐHBK",
                "Khoa Điện lập danh sách phân công Giáo viên chủ nhiệm năm học 2026-2027:",
                "Đà Nẵng, ngày 26 tháng 11 năm 2026",
                "TS. Trần Văn Khoa",
                "Danh sách đã được rà soát toàn khoa."
        );
        byte[] pdf = generator.render(new PdfSpec(
                "Điện",
                "2026-2027",
                2,
                OffsetDateTime.parse("2026-09-01T00:00:00+07:00"),
                OffsetDateTime.parse("2027-06-30T00:00:00+07:00"),
                meta,
                manyRows));

        try (var doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(2);
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("KHOA ĐIỆN")
                    .contains("Căn cứ Kế hoạch số 12/KH-ĐHBK")
                    .contains("Tổng số lớp đã phân công: 38 lớp.")
                    .contains("Ghi chú: Danh sách đã được rà soát toàn khoa.");
        }
    }
}
