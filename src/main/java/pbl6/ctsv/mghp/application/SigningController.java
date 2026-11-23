package pbl6.ctsv.mghp.application;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;import java.net.URI;import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import pbl6.ctsv.auth.AuthAccess;
import static pbl6.ctsv.mghp.application.IntakeRules.id;

@RestController @RequestMapping("/api/ctsv")
@ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningController {
 final AuthAccess auth;final SigningDocuments documents;final SigningService signing;final SigningAuthorizations authorities;final TransactionTemplate tx;final ObjectMapper json;
 SigningController(AuthAccess auth,SigningDocuments documents,SigningService signing,SigningAuthorizations authorities,PlatformTransactionManager manager,ObjectMapper json){this.auth=auth;this.documents=documents;this.signing=signing;this.authorities=authorities;this.tx=new TransactionTemplate(manager);this.json=json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);}
 AuthAccess.Principal principal(HttpServletRequest r){var h=Collections.list(r.getHeaders("Authorization"));if(h.size()!=1||!h.getFirst().startsWith("Bearer "))throw new IntakeProblem(401,"UNAUTHENTICATED","request");return auth.verifyAccessToken(h.getFirst().substring(7));}
 void query(HttpServletRequest r,String...allowed){for(var e:r.getParameterMap().entrySet())if(!List.of(allowed).contains(e.getKey())||e.getValue().length!=1)throw IntakeProblem.invalid("query");if("GET".equals(r.getMethod())&&(r.getContentLengthLong()>0||r.getHeader("Transfer-Encoding")!=null))throw IntakeProblem.invalid("body");}
 JsonNode body(HttpServletRequest r)throws IOException{
  try{if(r.getContentType()==null||!MediaType.APPLICATION_JSON.includes(MediaType.parseMediaType(r.getContentType())))throw new IntakeProblem(415,"UNSUPPORTED_MEDIA_TYPE","body");}catch(InvalidMediaTypeException e){throw new IntakeProblem(415,"UNSUPPORTED_MEDIA_TYPE","body");}
  if(r.getHeader("Content-Encoding")!=null&&!"identity".equalsIgnoreCase(r.getHeader("Content-Encoding")))throw new IntakeProblem(415,"UNSUPPORTED_MEDIA_TYPE","body");
  var bytes=r.getInputStream().readNBytes(1048577);if(bytes.length>1048576)throw new IntakeProblem(413,"PAYLOAD_TOO_LARGE","body");
  try{return json.readTree(java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString());}catch(com.fasterxml.jackson.core.JsonProcessingException|java.nio.charset.CharacterCodingException e){throw IntakeProblem.invalid("body");}
 }
 ResponseEntity<?> reply(ApplicationReads.Detail view,int status){
  var response=ResponseEntity.status(status).cacheControl(CacheControl.noStore()).eTag(view.tag());
  if(status==201&&view.body().containsKey("documentId"))response.location(URI.create("/api/ctsv/phien-ban-van-ban/"+view.body().get("id")));
  if(status==201&&view.body().containsKey("documentVersionId"))response.location(URI.create("/api/ctsv/yeu-cau-ky/"+view.body().get("id")));
  return response.body(view.body());
 }
 @PostMapping("/mghp/bien-ban/{id}/van-ban") ResponseEntity<?> minutes(@PathVariable String id,HttpServletRequest r)throws IOException{query(r);var p=principal(r);var b=body(r);return reply(tx.execute(s->documents.bind(p,true,id(id,"id"),b,r.getHeader("If-Match"))),201);}
 @PostMapping("/mghp/phien-ban-quyet-dinh/{id}/van-ban") ResponseEntity<?> decision(@PathVariable String id,HttpServletRequest r)throws IOException{query(r);var p=principal(r);var b=body(r);return reply(tx.execute(s->documents.bind(p,false,id(id,"id"),b,r.getHeader("If-Match"))),201);}
 @GetMapping("/phien-ban-van-ban/{id}") ResponseEntity<?> read(@PathVariable String id,HttpServletRequest r){query(r);var p=principal(r);return reply(tx.execute(s->documents.read(p,id(id,"id"))),200);}
 @PostMapping("/phien-ban-van-ban/{id}/yeu-cau-ky") ResponseEntity<?> prepare(@PathVariable String id,HttpServletRequest r)throws IOException{query(r);var p=principal(r);var b=body(r);var result=tx.execute(s->signing.prepare(p,id(id,"id"),b));return reply(result.detail(),result.created()?201:200);}
 @GetMapping("/yeu-cau-ky/{id}") ResponseEntity<?> request(@PathVariable String id,HttpServletRequest r){query(r);var p=principal(r);return reply(tx.execute(s->signing.read(p,id(id,"id"))),200);}
 @PostMapping("/yeu-cau-ky/{id}/gui-ky") ResponseEntity<?> sign(@PathVariable String id,HttpServletRequest r)throws IOException{query(r);return reply(signing.enqueue(principal(r),id(id,"id"),body(r),r.getHeader("If-Match")),202);}
 @PostMapping("/yeu-cau-ky/{id}/doi-soat") ResponseEntity<?> reconcile(@PathVariable String id,HttpServletRequest r)throws IOException{query(r);var p=principal(r);var b=body(r);return reply(tx.execute(s->signing.reconcile(p,id(id,"id"),b,r.getHeader("If-Match"))),202);}
 @GetMapping("/tai-lieu-ky") ResponseEntity<?> list(HttpServletRequest r){query(r,"status","mine","page","size","view");var p=principal(r);String mine=r.getParameter("mine");if(mine!=null&&!Set.of("true","false").contains(mine))throw IntakeProblem.invalid("mine");String view=r.getParameter("view")==null?"all":r.getParameter("view");if(!Set.of("all","waiting","handled").contains(view))throw IntakeProblem.invalid("view");int page=IntakeRules.page(r.getParameter("page"),"page",0,0,Integer.MAX_VALUE),size=IntakeRules.page(r.getParameter("size"),"size",20,1,100);return reply(tx.execute(s->signing.list(p,r.getParameter("status"),Boolean.parseBoolean(mine),page,size,view)),200);}
 @GetMapping("/buoc-ky/{id}/can-cu-gui-ky") ResponseEntity<?> authorities(@PathVariable String id,HttpServletRequest r){query(r);var p=principal(r);return reply(tx.execute(s->authorities.list(p,id(id,"id"))),200);}
 @PostMapping("/buoc-ky/{id}/can-cu-gui-ky") ResponseEntity<?> authority(@PathVariable String id,HttpServletRequest r)throws IOException{query(r);var p=principal(r);var b=body(r);return reply(tx.execute(s->authorities.add(p,id(id,"id"),b,r.getHeader("If-Match"))),201);}
 @PostMapping("/can-cu-gui-ky/{id}/thu-hoi") ResponseEntity<?> revoke(@PathVariable String id,HttpServletRequest r)throws IOException{query(r);var p=principal(r);var b=body(r);return reply(tx.execute(s->authorities.revoke(p,id(id,"id"),b,r.getHeader("If-Match"))),200);}
}
