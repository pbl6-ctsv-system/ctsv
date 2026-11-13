package pbl6.ctsv.mghp.configuration;
final class ConfigFailure extends RuntimeException {
 final int status;final String code,field;
 ConfigFailure(int status,String code){this(status,code,"request");}
 ConfigFailure(int status,String code,String field){super(code);this.status=status;this.code=code;this.field=field;}
 static ConfigFailure invalid(String field){return new ConfigFailure(400,"VALIDATION_ERROR",field);}
}
