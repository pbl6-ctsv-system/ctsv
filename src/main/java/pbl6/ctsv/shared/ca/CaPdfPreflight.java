package pbl6.ctsv.shared.ca;

import java.security.MessageDigest;
import java.util.HexFormat;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

public final class CaPdfPreflight {
    public static final int MAX_BYTES = 10_000_000;
    private CaPdfPreflight() {}

    /** PDF default user space points, lower-left origin, before page display rotation. */
    public record Box(int page, double x, double y, double width, double height) {}
    public record Input(byte[] bytes, String sha256, int pages, int existingSignatures,
                        int rotation, double cropX, double cropY, double cropWidth, double cropHeight) {
        public Input { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }

    public static Input inspect(byte[] bytes, Box box) {
        if (bytes == null || bytes.length == 0) throw new CaFailure("PDF_INVALID");
        if (bytes.length > MAX_BYTES) throw new CaFailure("PDF_TOO_LARGE");
        if (box == null || box.page() < 1 || !Double.isFinite(box.x()) || !Double.isFinite(box.y())
                || !Double.isFinite(box.width()) || !Double.isFinite(box.height())
                || box.x() < 0 || box.y() < 0 || box.width() <= 0 || box.height() <= 0)
            throw new CaFailure("PDF_GEOMETRY_INVALID");
        try (var source = new RandomAccessReadBuffer(bytes); var doc = new PDFParser(source).parse(false)) {
            if (doc.isEncrypted()) throw new CaFailure("PDF_ENCRYPTED");
            if (doc.getNumberOfPages() < box.page()) throw new CaFailure("PDF_GEOMETRY_INVALID");
            long decoded = 0;
            for (var page : doc.getPages()) {
                var streams = page.getContentStreams();
                while (streams.hasNext()) try (var stream = streams.next().createInputStream()) {
                    byte[] buffer = new byte[8192];
                    for (int n; (n = stream.read(buffer)) != -1;) {
                        decoded += n;
                        if (decoded > 64_000_000) throw new CaFailure("PDF_RESOURCE_LIMIT");
                    }
                }
            }
            var page = doc.getPage(box.page()-1); var crop = page.getCropBox();
            if (box.x() < crop.getLowerLeftX() || box.y() < crop.getLowerLeftY()
                    || box.x()+box.width() > crop.getUpperRightX() || box.y()+box.height() > crop.getUpperRightY())
                throw new CaFailure("PDF_GEOMETRY_INVALID");
            return new Input(bytes,sha256(bytes),doc.getNumberOfPages(),doc.getSignatureDictionaries().size(),
                    page.getRotation(),crop.getLowerLeftX(),crop.getLowerLeftY(),crop.getWidth(),crop.getHeight());
        } catch (InvalidPasswordException e) { throw new CaFailure("PDF_ENCRYPTED"); }
        catch (java.io.IOException | IllegalArgumentException e) { throw new CaFailure("PDF_INVALID"); }
    }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
