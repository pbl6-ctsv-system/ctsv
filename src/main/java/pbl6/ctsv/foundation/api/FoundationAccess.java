package pbl6.ctsv.foundation.api;
import pbl6.ctsv.auth.AuthAccess;
/** Shared module authorization boundary. Must run within the caller's local transaction. */
public interface FoundationAccess {
 /** Typed failure at the public module boundary; callers must preserve security status/code. */
 class Failure extends RuntimeException {
  private final int status;private final String code,field;
  public Failure(int status,String code,String field){super(code);this.status=status;this.code=code;this.field=field;}
  public int status(){return status;}public String code(){return code;}public String field(){return field;}
 }
 record Identity(long userId,Long studentId,AuthAccess.Principal principal){}
 Identity authorize(AuthAccess.Principal principal,boolean online,String capability);
 /** Resolve an active local recipient without imposing a module-specific role. */
 default Identity identify(AuthAccess.Principal principal,boolean online){return authorize(principal,online,null);}
}
