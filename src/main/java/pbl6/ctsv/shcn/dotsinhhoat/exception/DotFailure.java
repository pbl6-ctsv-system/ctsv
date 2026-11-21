package pbl6.ctsv.shcn.dotsinhhoat.exception;

import java.util.List;

public final class DotFailure extends RuntimeException {
    public record FieldError(String field,String code,String message){}
    private final int status;private final String code;private final List<FieldError> errors;
    public DotFailure(int status,String code){this(status,code,List.of());}
    private DotFailure(int status,String code,List<FieldError> errors){super(code);this.status=status;this.code=code;this.errors=List.copyOf(errors);}
    public int status(){return status;}public String code(){return code;}public List<FieldError> errors(){return errors;}
    public static DotFailure invalid(String field){return new DotFailure(400,"VALIDATION_ERROR",List.of(new FieldError(field,"INVALID","Giá trị không hợp lệ.")));}
    public static DotFailure unavailable(){return new DotFailure(503,"DEPENDENCY_UNAVAILABLE");}
}
