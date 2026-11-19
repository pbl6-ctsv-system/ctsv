package pbl6.ctsv.shcn.phancongkhoa.exception;

import java.util.List;

public final class PhanCongKhoaFailure extends RuntimeException {
    public record FieldError(String field,String code,String message) {}
    private final int status;
    private final String code;
    private final List<FieldError> errors;
    public PhanCongKhoaFailure(int status,String code){this(status,code,List.of());}
    private PhanCongKhoaFailure(int status,String code,List<FieldError> errors){super(code);this.status=status;this.code=code;this.errors=List.copyOf(errors);}
    public int status(){return status;}
    public String code(){return code;}
    public List<FieldError> errors(){return errors;}
    public static PhanCongKhoaFailure invalid(String field){return new PhanCongKhoaFailure(400,"VALIDATION_ERROR",List.of(new FieldError(field,"INVALID_VALUE","Invalid request value")));}
    public static PhanCongKhoaFailure unavailable(){return new PhanCongKhoaFailure(503,"DEPENDENCY_UNAVAILABLE");}
    public static PhanCongKhoaFailure notFound(){return new PhanCongKhoaFailure(404,"NOT_FOUND");}
    public static PhanCongKhoaFailure conflict(String code){return new PhanCongKhoaFailure(409,code);}
}
