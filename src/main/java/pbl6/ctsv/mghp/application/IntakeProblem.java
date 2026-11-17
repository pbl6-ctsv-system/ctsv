package pbl6.ctsv.mghp.application;

final class IntakeProblem extends RuntimeException {
    final int status;
    final String field;

    IntakeProblem(int status,String code,String field) {
        super(code);
        this.status=status;
        this.field=field;
    }

    static IntakeProblem invalid(String field) { return new IntakeProblem(400,"VALIDATION_ERROR",field); }
    static IntakeProblem missing() { return new IntakeProblem(404,"NOT_FOUND","request"); }
    static IntakeProblem conflict(String code) { return new IntakeProblem(409,code,"request"); }
}
