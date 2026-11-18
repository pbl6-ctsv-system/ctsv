package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationAccess;
import static pbl6.ctsv.mghp.application.IntakeRules.*;

@RestController @RequestMapping("/api/ctsv/mghp/quyen-huong")
@ConditionalOnProperty(name="ctsv.mghp.applications.enabled",havingValue="true")
class BenefitController {
 private final AuthAccess auth;private final FoundationAccess access;private final BenefitService service;
 private final TransactionTemplate tx;private final ObjectMapper json;
 BenefitController(AuthAccess auth,FoundationAccess access,BenefitService service,PlatformTransactionManager manager,ObjectMapper json){this.auth=auth;this.access=access;this.service=service;this.tx=new TransactionTemplate(manager);this.json=json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);}
 private AuthAccess.Principal principal(HttpServletRequest r){var headers=Collections.list(r.getHeaders("Authorization"));if(headers.size()!=1||!headers.getFirst().startsWith("Bearer "))throw new IntakeProblem(401,"UNAUTHENTICATED","request");return auth.verifyAccessToken(headers.getFirst().substring(7));}
 @GetMapping("/{id}") ResponseEntity<?> read(@PathVariable String id,HttpServletRequest r){
  query(r);var p=principal(r);return reply(tx.execute(s->{var actor=access.authorize(p,false,null);boolean staff=false;
   try{access.authorize(p,false,"mghp_chuyen_vien");staff=true;}catch(FoundationAccess.Failure error){if(error.status()!=403)throw error;}
   if(staff)actor=access.authorize(p,true,"mghp_chuyen_vien");return service.read(actor,staff,id(id,"id"));}),200,true);
 }
 @GetMapping("/can-ra-soat") ResponseEntity<?> list(HttpServletRequest r){
  query(r,"semesterId","policyCode","page","size");var p=principal(r);
  var result=tx.execute(s->{access.authorize(p,true,"mghp_chuyen_vien");return service.review(id(r.getParameter("semesterId"),"semesterId"),r.getParameter("policyCode"),page(r.getParameter("page"),"page",0,0,Integer.MAX_VALUE),page(r.getParameter("size"),"size",20,1,100));});
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
 }
 @PostMapping("/{id}/ghi-nhan-ra-soat") ResponseEntity<?> observe(@PathVariable String id,HttpServletRequest r)throws IOException {
  var p=principal(r);var b=body(r);return reply(tx.execute(s->service.observe(access.authorize(p,true,"mghp_chuyen_vien"),id(id,"id"),b,r.getHeader("If-Match"))),201,true);
 }
 @PostMapping("/{id}/yeu-cau-cap-nhat") ResponseEntity<?> update(@PathVariable String id,HttpServletRequest r)throws IOException {
  var p=principal(r);var b=body(r);return reply(tx.execute(s->service.requestUpdate(access.authorize(p,true,"mghp_chuyen_vien"),id(id,"id"),b,r.getHeader("If-Match"))),201,false);
 }
 private void query(HttpServletRequest r,String... allowed){for(var entry:r.getParameterMap().entrySet())if(!Set.of(allowed).contains(entry.getKey())||entry.getValue().length!=1)throw IntakeProblem.invalid("query");if(r.getContentLengthLong()>0||r.getHeader("Transfer-Encoding")!=null)throw IntakeProblem.invalid("body");}
 private JsonNode body(HttpServletRequest r)throws IOException {
  if(r.getQueryString()!=null)throw IntakeProblem.invalid("query");
  try{if(r.getContentType()==null||!MediaType.APPLICATION_JSON.includes(MediaType.parseMediaType(r.getContentType())))throw new IntakeProblem(415,"UNSUPPORTED_MEDIA_TYPE","body");}catch(InvalidMediaTypeException e){throw new IntakeProblem(415,"UNSUPPORTED_MEDIA_TYPE","body");}
  byte[] bytes=r.getInputStream().readNBytes(1048577);if(bytes.length>1048576)throw new IntakeProblem(413,"PAYLOAD_TOO_LARGE","body");
  try{return json.readTree(java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString());}catch(com.fasterxml.jackson.core.JsonProcessingException|java.nio.charset.CharacterCodingException e){throw IntakeProblem.invalid("body");}
 }
 private ResponseEntity<?> reply(ApplicationReads.Detail d,int status,boolean tag){var response=ResponseEntity.status(status).cacheControl(CacheControl.noStore());if(tag&&d.tag()!=null)response.eTag(d.tag());return response.body(d.body());}
}
