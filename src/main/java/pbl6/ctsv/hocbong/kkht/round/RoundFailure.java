package pbl6.ctsv.hocbong.kkht.round;

final class RoundFailure extends RuntimeException {
    final int status;
    final String code;
    final String field;

    RoundFailure(int status,String code) { this(status,code,"request"); }
    RoundFailure(int status,String code,String field) {
        super(code); this.status=status; this.code=code; this.field=field;
    }
    static RoundFailure invalid(String field) { return new RoundFailure(400,"VALIDATION_ERROR",field); }
}
