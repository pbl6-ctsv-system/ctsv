package pbl6.ctsv.foundation.api;
import pbl6.ctsv.auth.AuthAccess;
/** Shared module authorization boundary. Must run within the caller's local transaction. */
public interface FoundationAccess {
 record Identity(long userId,Long studentId,AuthAccess.Principal principal){}
 Identity authorize(AuthAccess.Principal principal,boolean online,String capability);
}
