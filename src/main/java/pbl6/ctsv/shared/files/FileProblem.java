package pbl6.ctsv.shared.files;

public final class FileProblem extends RuntimeException {
    final int status;
    FileProblem(int status, String code) {
        super(code);
        this.status = status;
    }
    public static FileProblem unavailable() { return new FileProblem(503, "STORAGE_UNAVAILABLE"); }
    public int status() { return status; }
    static FileProblem invalid() { return new FileProblem(400, "VALIDATION_ERROR"); }
    static FileProblem tooLarge() { return new FileProblem(413, "FILE_TOO_LARGE"); }
    static FileProblem notFound() { return new FileProblem(404, "NOT_FOUND"); }
    static FileProblem integrity() { return new FileProblem(502, "FILE_INTEGRITY_FAILED"); }
}
