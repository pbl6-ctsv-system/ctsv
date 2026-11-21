package pbl6.ctsv.shared.ca;

/** Safe boundary exception: never includes provider bodies, credentials or delivery URLs. */
public final class CaFailure extends RuntimeException {
    private final String code;
    private final int httpStatus;
    private final boolean uncertain;
    private final java.util.UUID document;

    public CaFailure(String code) { this(code, 0, false); }
    public CaFailure(String code, int httpStatus, boolean uncertain) {
        this(code,httpStatus,uncertain,null);
    }
    public CaFailure(String code, int httpStatus, boolean uncertain,java.util.UUID document) {
        super(code + (httpStatus == 0 ? "" : " (HTTP " + httpStatus + ")"));
        this.code = code; this.httpStatus = httpStatus; this.uncertain = uncertain;
        this.document=document;
    }
    public String code() { return code; }
    public int httpStatus() { return httpStatus; }
    public boolean uncertain() { return uncertain; }
    public java.util.UUID document() { return document; }
}
