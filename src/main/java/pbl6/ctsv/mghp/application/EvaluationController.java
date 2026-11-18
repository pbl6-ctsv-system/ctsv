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

@RestController @RequestMapping("/api/ctsv/mghp/danh-gia")
@ConditionalOnProperty(name="ctsv.mghp.applications.enabled",havingValue="true")
class EvaluationController {
 private final AuthAccess auth;private final FoundationAccess access;private final EvaluationService service;
 private final TransactionTemplate tx;private final ObjectMapper json;
 EvaluationController(AuthAccess auth,FoundationAccess access,EvaluationService service,PlatformTransactionManager manager,ObjectMapper json){
  this.auth=auth;this.access=access;this.service=service;this.tx=new TransactionTemplate(manager);
  this.json=json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 }
 private FoundationAccess.Identity actor(HttpServletRequest r){
  var headers=Collections.list(r.getHeaders("Authorization"));
  if(headers.size()!=1||!headers.getFirst().startsWith("Bearer "))throw new IntakeProblem(401,"UNAUTHENTICATED","request");
  return access.authorize(auth.verifyAccessToken(headers.getFirst().substring(7)),true,"mghp_chuyen_vien");
 }
 private JsonNode body(HttpServletRequest r)throws IOException {
  try{if(r.getContentType()==null||!MediaType.APPLICATION_JSON.includes(MediaType.parseMediaType(r.getContentType())))throw new IntakeProblem(415,"UNSUPPORTED_MEDIA_TYPE","body");}catch(InvalidMediaTypeException e){throw new IntakeProblem(415,"UNSUPPORTED_MEDIA_TYPE","body");}
  if(r.getHeader("Content-Encoding")!=null&&!"identity".equalsIgnoreCase(r.getHeader("Content-Encoding")))throw new IntakeProblem(415,"UNSUPPORTED_MEDIA_TYPE","body");
  byte[] bytes=r.getInputStream().readNBytes(1048577);if(bytes.length>1048576)throw new IntakeProblem(413,"PAYLOAD_TOO_LARGE","body");
  try{return json.readTree(java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString());}catch(com.fasterxml.jackson.core.JsonProcessingException|java.nio.charset.CharacterCodingException e){throw IntakeProblem.invalid("body");}
 }
 @GetMapping("/{id}") ResponseEntity<?> read(@PathVariable String id,HttpServletRequest r){
  query(r);if(r.getContentLengthLong()>0||r.getHeader("Transfer-Encoding")!=null)throw IntakeProblem.invalid("body");return reply(tx.execute(s->{actor(r);return service.read(id(id,"id"));}),200,false);
 }
 @PostMapping ResponseEntity<?> create(HttpServletRequest r)throws IOException {
  query(r);var b=body(r);return reply(tx.execute(s->service.create(actor(r),b)),201,true);
 }
 @PutMapping("/{id}") ResponseEntity<?> update(@PathVariable String id,HttpServletRequest r)throws IOException {
  query(r);var b=body(r);return reply(tx.execute(s->service.update(actor(r),id(id,"id"),b,r.getHeader("If-Match"))),200,false,false);
 }
 @PostMapping("/{id}/xac-nhan") ResponseEntity<?> confirm(@PathVariable String id,HttpServletRequest r)throws IOException {
  query(r);var b=shape(body(r),List.of("reason"));String reason=text(b.get("reason"),"reason",4000,true);
  return reply(tx.execute(s->service.confirm(actor(r),id(id,"id"),reason,r.getHeader("If-Match"))),200,false);
 }
 @PostMapping("/{id}/phien-ban") ResponseEntity<?> revision(@PathVariable String id,HttpServletRequest r)throws IOException {
  query(r);var b=shape(body(r),List.of("reason"));String reason=text(b.get("reason"),"reason",4000,true);
  return reply(tx.execute(s->service.revision(actor(r),id(id,"id"),reason,r.getHeader("If-Match"))),201,true);
 }
 private void query(HttpServletRequest r){if(!r.getParameterMap().isEmpty())throw IntakeProblem.invalid("query");}
 private ResponseEntity<?> reply(ApplicationReads.Detail d,int status,boolean location){return reply(d,status,location,true);}
 private ResponseEntity<?> reply(ApplicationReads.Detail d,int status,boolean location,boolean tag){
  var builder=ResponseEntity.status(status).cacheControl(CacheControl.noStore());
  if(tag)builder.eTag(d.tag());if(location)builder.location(URI.create("/api/ctsv/mghp/danh-gia/"+((Map<?,?>)d.body()).get("id")));
  return builder.body(d.body());
 }
}
