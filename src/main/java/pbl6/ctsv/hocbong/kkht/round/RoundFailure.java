package pbl6.ctsv.hocbong.kkht.round;

public final class RoundFailure extends RuntimeException {
    final int status;
    final String code;
    final String field;

    public RoundFailure(int status,String code) { this(status,code,"request"); }
    public RoundFailure(int status,String code,String field) {
        super(code); this.status=status; this.code=code; this.field=field;
    }
    public static RoundFailure invalid(String field) { return new RoundFailure(400,"VALIDATION_ERROR",field); }
}
