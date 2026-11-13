package pbl6.ctsv.auth;

public final class AuthFailure extends RuntimeException {
    final int status;
    final String code;
    final long retryAfter;
    AuthFailure(int status, String code) { this(status, code, 0); }
    AuthFailure(int status, String code, long retryAfter) {
        super(code);
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
    }
    static AuthFailure invalid() { return new AuthFailure(400, "INVALID_REQUEST"); }
    static AuthFailure unavailable() { return new AuthFailure(503, "DEPENDENCY_UNAVAILABLE"); }
    public int status(){return status;}
    public String code(){return code;}
}
