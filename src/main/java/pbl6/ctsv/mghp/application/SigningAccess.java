package pbl6.ctsv.mghp.application;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.*;
import static pbl6.ctsv.mghp.application.SigningEntities.*;

@Component @ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningAccess implements pbl6.ctsv.shared.files.FileReadPolicy {
 final FoundationAccess access;final FoundationRoleAccess roles;final SigningRepository repo;
 SigningAccess(FoundationAccess access,FoundationRoleAccess roles,SigningRepository repo){this.access=access;this.roles=roles;this.repo=repo;}
 FoundationAccess.Identity actor(AuthAccess.Principal p){return access.identify(p,true);}
 boolean role(long user,String stepRole){return roles.findActiveGlobalRole(user,"MGHP","mghp_"+stepRole).isPresent();}
 void require(long user,String role){if(!role(user,role))throw new IntakeProblem(403,"FORBIDDEN","request");}
 FoundationAccess.Identity specialist(AuthAccess.Principal p){var i=actor(p);require(i.userId(),"chuyen_vien");return i;}
 boolean readable(long user,long documentVersion){
  if(role(user,"chuyen_vien"))return true;
  var request=repo.request(documentVersion);
  if(request!=null&&repo.steps(request.id).stream().anyMatch(s->Objects.equals(s.userId,user)&&role(user,s.role)))return true;
  var link=repo.link(documentVersion);
  if(link.minutesId==null||request==null||!"completed".equals(request.state)||!role(user,"pho_phong"))return false;
  return repo.ppReadsMinutes(user,link.minutesId);
 }
 void requireRead(long user,long version){if(!readable(user,version))throw IntakeProblem.missing();}
 @Override public boolean canRead(long file,FoundationAccess.Identity identity){return repo.documentsForFile(file).stream().anyMatch(version->readable(identity.userId(),version));}
}
