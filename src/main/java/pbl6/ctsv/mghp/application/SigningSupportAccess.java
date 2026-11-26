package pbl6.ctsv.mghp.application;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.*;

@Component @ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningSupportAccess {
 final FoundationAccess foundation;final FoundationRoleAccess roles;final AuthAccess auth;
 SigningSupportAccess(FoundationAccess foundation,FoundationRoleAccess roles,AuthAccess auth){this.foundation=foundation;this.roles=roles;this.auth=auth;}
 FoundationAccess.Identity require(AuthAccess.Principal principal){
  var i=foundation.identify(principal,true);
  if(roles.findActiveGlobalRole(i.userId(),"MGHP","mghp_quan_tri_ky").isEmpty())throw new IntakeProblem(403,"FORBIDDEN","request");
  return i;
 }
 /** Only after an Admin confirmation has been durably reserved. Logout does not retract it. */
 FoundationAccess.Identity accepted(AuthAccess.Principal principal){
  auth.requireAcceptedWorkAccess(principal,principal.accountId());var i=foundation.identify(principal,false);
  if(roles.findActiveGlobalRole(i.userId(),"MGHP","mghp_quan_tri_ky").isEmpty())throw new IntakeProblem(403,"FORBIDDEN","request");return i;
 }
}
