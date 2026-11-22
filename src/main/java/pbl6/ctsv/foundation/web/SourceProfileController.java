package pbl6.ctsv.foundation.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pbl6.ctsv.auth.AuthAccess;
import java.util.*;

@RestController
@RequestMapping("/api/ctsv/nen-tang/ho-so-nguon")
@ConditionalOnProperty(name="ctsv.foundation.enabled",havingValue="true")
final class SourceProfileController {
 private final AuthAccess auth;private final SourceProfileService service;
 SourceProfileController(AuthAccess auth,SourceProfileService service){this.auth=auth;this.service=service;}
 @GetMapping ResponseEntity<?> list(HttpServletRequest r){
  var headers=Collections.list(r.getHeaders("Authorization"));if(headers.size()!=1||!headers.getFirst().startsWith("Bearer "))throw new FoundationFailure(401,"UNAUTHENTICATED");
  var actor=auth.verifyAccessToken(headers.getFirst().substring(7));
  for(var entry:r.getParameterMap().entrySet())if(!Set.of("profileType","q","page","size").contains(entry.getKey())||entry.getValue().length!=1)throw FoundationFailure.invalid("query");
  if(r.getContentLengthLong()>0||r.getHeader("Transfer-Encoding")!=null)throw FoundationFailure.invalid("body");
  String type=r.getParameter("profileType"),q=r.getParameter("q");if(type==null||!Set.of("student","staff").contains(type))throw FoundationFailure.invalid("profileType");
  if(q!=null){q=q.strip();if(q.length()>128)throw FoundationFailure.invalid("q");if(q.isEmpty())q=null;}
  var p=FoundationRules.page(r.getParameter("page"),r.getParameter("size"),20);
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(actor,type,q,p));
 }
}
