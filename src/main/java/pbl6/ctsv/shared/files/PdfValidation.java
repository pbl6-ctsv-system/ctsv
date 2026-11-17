package pbl6.ctsv.shared.files;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

final class PdfValidation {
    private PdfValidation() {}

    static int pages(Path file) {
        try (var source = new RandomAccessReadBufferedFile(file);
             var document = new PDFParser(source).parse(false)) {
            if (document.isEncrypted()) throw new FileProblem(400, "PDF_ENCRYPTED");
            int count = 0;
            long decodedBytes = 0;
            byte[] buffer = new byte[8192];
            for (var page : document.getPages()) {
                // Consume the decoded page streams so truncated/invalid content isn't deferred to download.
                var streams = page.getContentStreams();
                while (streams.hasNext()) {
                    try (var decoded = streams.next().createInputStream()) {
                        for (int size; (size = decoded.read(buffer)) != -1;) {
                            decodedBytes += size;
                            if (decodedBytes > 64_000_000) throw new FileProblem(400, "PDF_INVALID");
                        }
                    }
                }
                count++;
            }
            if (count < 1 || count != document.getNumberOfPages()) {
                throw new FileProblem(400, "PDF_INVALID");
            }
            return count;
        } catch (InvalidPasswordException failure) {
            throw new FileProblem(400, "PDF_ENCRYPTED");
        } catch (IOException | IllegalArgumentException failure) {
            throw new FileProblem(400, "PDF_INVALID");
        }
    }
}
