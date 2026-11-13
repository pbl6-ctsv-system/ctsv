package pbl6.ctsv.foundation.web;
final class FoundationFailure extends pbl6.ctsv.foundation.api.FoundationAccess.Failure {
 final int status; final String code; final String field;
 FoundationFailure(int status,String code){this(status,code,"request");}
 FoundationFailure(int status,String code,String field){super(status,code,field);this.status=status;this.code=code;this.field=field;}
 static FoundationFailure invalid(String field){return new FoundationFailure(400,"VALIDATION_ERROR",field);}
}
