package pbl6.ctsv.shared.files;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.Semaphore;

final class PdfSpool {
    static final int UPLOAD_LIMIT = 10_000_000;
    // TEP02 still supports existing authenticated documents accepted under the earlier upload cap.
    static final int DOWNLOAD_LIMIT = 20_000_000;
    static final int OVERHEAD = 65_536;
    private final Semaphore slots = new Semaphore(4);

    Lease open() throws IOException {
        if (!slots.tryAcquire()) throw FileProblem.unavailable();
        try {
            return new Lease(Files.createTempFile("ctsv-pdf-", ".tmp"));
        } catch (IOException | RuntimeException failure) {
            slots.release();
            throw failure;
        }
    }

    final class Lease implements AutoCloseable {
        final Path path;
        private Lease(Path path) { this.path = path; }
        @Override public void close() throws IOException {
            try {
                Files.deleteIfExists(path);
            } finally {
                slots.release();
            }
        }
    }

    static long copy(InputStream source, Path target, long limit, FileProblem overflow) throws IOException {
        try (var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            for (int size; (size = source.read(buffer)) != -1;) {
                total += size;
                if (total > limit) throw overflow;
                output.write(buffer, 0, size);
            }
            return total;
        }
    }

    static String sha256(Path path) throws IOException {
        var digest = digest();
        try (var source = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int size; (size = source.read(buffer)) != -1;) digest.update(buffer, 0, size);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
