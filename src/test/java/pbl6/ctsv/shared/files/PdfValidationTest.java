package pbl6.ctsv.shared.files;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class PdfValidationTest {
    @TempDir Path spool;

    @Test void validatesActualPdfAndReturnsPageCount() throws Exception {
        Path file = pdf(false, true);
        assertThat(PdfValidation.pages(file)).isEqualTo(1);
    }

    @Test void rejectsPasswordProtectedPdf() throws Exception {
        assertThatThrownBy(() -> PdfValidation.pages(pdf(true, true)))
            .isInstanceOf(FileProblem.class).hasMessage("PDF_ENCRYPTED");
    }

    @Test void rejectsEmptyPageTreeTextEmptyBytesAndTruncation() throws Exception {
        Path fake = Files.writeString(spool.resolve("fake.pdf"), "%PDF-1.7\nNot a PDF\n%%EOF");
        Path good = pdf(false, true);
        byte[] bytes = Files.readAllBytes(good);
        Path truncated = Files.write(spool.resolve("truncated.pdf"), Arrays.copyOf(bytes, bytes.length - 30));
        Path empty = Files.createFile(spool.resolve("empty.pdf"));
        for (Path invalid : new Path[]{fake, truncated, empty, pdf(false, false)}) {
            assertThatThrownBy(() -> PdfValidation.pages(invalid))
                .isInstanceOf(FileProblem.class).hasMessage("PDF_INVALID");
        }
    }

    private Path pdf(boolean encrypted, boolean page) throws Exception {
        Path file = Files.createTempFile(spool, "fixture-", ".pdf");
        try (PDDocument document = new PDDocument()) {
            if (page) document.addPage(new PDPage());
            if (encrypted) document.protect(new StandardProtectionPolicy("synthetic-owner", "synthetic-user", new AccessPermission()));
            document.save(file.toFile());
        }
        return file;
    }
}
