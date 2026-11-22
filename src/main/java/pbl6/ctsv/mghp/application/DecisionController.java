package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationAccess;
import static pbl6.ctsv.mghp.application.IntakeRules.*;
import static pbl6.ctsv.mghp.application.IntakeProblem.invalid;

@RestController @RequestMapping("/api/ctsv/mghp")
@ConditionalOnProperty(name="ctsv.mghp.applications.enabled",havingValue="true")
class DecisionController {
 final AuthAccess auth;final FoundationAccess access;final DecisionService service;final DecisionReads reads;
 final TransactionTemplate tx;final ObjectMapper json;
 DecisionController(AuthAccess auth,FoundationAccess access,DecisionService service,DecisionReads reads,PlatformTransactionManager manager,ObjectMapper json){
  this.auth=auth;this.access=access;this.service=service;this.reads=reads;this.tx=new TransactionTemplate(manager);
  this.json=json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 }
 FoundationAccess.Identity actor(HttpServletRequest r){
  var h=Collections.list(r.getHeaders("Authorization"));
  if(h.size()!=1||!h.getFirst().startsWith("Bearer "))throw new IntakeProblem(401,"UNAUTHENTICATED","request");
  return access.authorize(auth.verifyAccessToken(h.getFirst().substring(7)),true,"mghp_chuyen_vien");
 }
 JsonNode body(HttpServletRequest r)throws IOException{
  try{if(r.getContentType()==null||!MediaType.APPLICATION_JSON.includes(MediaType.parseMediaType(r.getContentType())))throw new IntakeProblem(415,"UNSUPPORTED_MEDIA_TYPE","body");}
  catch(InvalidMediaTypeException e){throw new IntakeProblem(415,"UNSUPPORTED_MEDIA_TYPE","body");}
  if(r.getHeader("Content-Encoding")!=null&&!"identity".equalsIgnoreCase(r.getHeader("Content-Encoding")))throw new IntakeProblem(415,"UNSUPPORTED_MEDIA_TYPE","body");
  byte[] bytes=r.getInputStream().readNBytes(1048577);if(bytes.length>1048576)throw new IntakeProblem(413,"PAYLOAD_TOO_LARGE","body");
  try{return json.readTree(java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString());}
  catch(com.fasterxml.jackson.core.JsonProcessingException|java.nio.charset.CharacterCodingException e){throw invalid("body");}
 }
 void query(HttpServletRequest r,String...allowed){
  for(var e:r.getParameterMap().entrySet())if(!List.of(allowed).contains(e.getKey())||e.getValue().length!=1)throw invalid("query");
  if("GET".equals(r.getMethod())&&(r.getContentLengthLong()>0||r.getHeader("Transfer-Encoding")!=null))throw invalid("body");
 }
 int page(HttpServletRequest r){return IntakeRules.page(r.getParameter("page"),"page",0,0,Integer.MAX_VALUE);}
 int size(HttpServletRequest r){return IntakeRules.page(r.getParameter("size"),"size",20,1,100);}
 ResponseEntity<?> reply(ApplicationReads.Detail d,int status,boolean location){var b=ResponseEntity.status(status).cacheControl(CacheControl.noStore()).eTag(d.tag());if(location)b.location(URI.create("/api/ctsv/mghp/phien-ban-quyet-dinh/"+d.body().get("id")));return b.body(d.body());}
 @PostMapping("/quyet-dinh") ResponseEntity<?> create(HttpServletRequest r)throws IOException{
  query(r);var body=body(r);var result=tx.execute(s->service.create(actor(r),body));return reply(result.detail(),result.created()?201:200,true);
 }
 @GetMapping("/quyet-dinh") ResponseEntity<?> list(HttpServletRequest r){
  query(r,"roundId","page","size");return reply(tx.execute(s->{actor(r);return reads.list(r.getParameter("roundId")==null?null:id(r.getParameter("roundId"),"roundId"),page(r),size(r));}),200,false);
 }
 @GetMapping("/quyet-dinh/{id}") ResponseEntity<?> root(@PathVariable String id,HttpServletRequest r){
  query(r);return reply(tx.execute(s->{actor(r);return reads.root(id(id,"id"));}),200,false);
 }
 @GetMapping("/quyet-dinh/{id}/lich-su") ResponseEntity<?> history(@PathVariable String id,HttpServletRequest r){
  query(r,"page","size");return reply(tx.execute(s->{actor(r);return reads.history(id(id,"id"),page(r),size(r));}),200,false);
 }
 @GetMapping("/phien-ban-quyet-dinh/{id}") ResponseEntity<?> read(@PathVariable String id,HttpServletRequest r){
  query(r);return reply(tx.execute(s->{actor(r);return reads.version(id(id,"id"));}),200,false);
 }
 @GetMapping("/phien-ban-quyet-dinh/{id}/{part:ung-vien|dong}") ResponseEntity<?> children(@PathVariable String id,@PathVariable String part,HttpServletRequest r){
  query(r,"page","size");return reply(tx.execute(s->{actor(r);return reads.children(id(id,"id"),part.equals("ung-vien"),page(r),size(r));}),200,false);
 }
 @PostMapping("/phien-ban-quyet-dinh/{id}/ket-luan-hop") ResponseEntity<?> meeting(@PathVariable String id,HttpServletRequest r)throws IOException{
  query(r);var body=body(r);var result=tx.execute(s->service.meeting(actor(r),id(id,"id"),body,r.getHeader("If-Match")));return reply(result.detail(),result.created()?201:200,true);
 }
 @PostMapping("/phien-ban-quyet-dinh/{id}/xac-nhan") ResponseEntity<?> confirm(@PathVariable String id,HttpServletRequest r)throws IOException{
  query(r);var body=body(r);return reply(tx.execute(s->service.confirm(actor(r),id(id,"id"),body,r.getHeader("If-Match"))),200,false);
 }
}

