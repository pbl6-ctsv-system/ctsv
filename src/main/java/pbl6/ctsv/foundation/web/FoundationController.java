package pbl6.ctsv.foundation.web;
import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.core.JsonParser;
import jakarta.servlet.http.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;import org.springframework.web.bind.annotation.*;
import pbl6.ctsv.auth.AuthAccess;
@RestController
@RequestMapping("/api/ctsv/nen-tang")
@ConditionalOnProperty(name="ctsv.foundation.enabled",havingValue="true")
final class FoundationController {
 private final FoundationService service;private final AuthAccess auth;private final ObjectMapper json;
 FoundationController(FoundationService service,AuthAccess auth,ObjectMapper json){this.service=service;this.auth=auth;this.json=json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);}
 AuthAccess.Principal principal(HttpServletRequest r){
  var headers=Collections.list(r.getHeaders("Authorization"));if(headers.size()!=1||!headers.getFirst().startsWith("Bearer "))throw new FoundationFailure(401,"UNAUTHENTICATED");
  return auth.verifyAccessToken(headers.getFirst().substring(7));
 }
 @GetMapping("/toi/quyen-giao-dien") ResponseEntity<?> menuAccess(HttpServletRequest r)throws IOException{
  var p=principal(r);query(r);
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.menuAccess(p));
 }
 @GetMapping("/sinh-vien") Object students(HttpServletRequest r)throws IOException{
  var p=principal(r);query(r,"q","page","size");return service.execute(p,true,"mghp_chuyen_vien",u->service.students(q(r),paging(r,20)));
 }
 @GetMapping("/sinh-vien/{idSinhVien}")Object student(@PathVariable String idSinhVien,HttpServletRequest r)throws IOException{
  var p=principal(r);query(r);long id=FoundationRules.id(idSinhVien);return service.execute(p,true,"mghp_chuyen_vien",u->service.student(id));
 }
 @GetMapping("/danh-muc")Object lookup(HttpServletRequest r)throws IOException{
  var p=principal(r);query(r,"kind","q","page","size");String kind=r.getParameter("kind");
  if(kind==null)throw FoundationFailure.invalid("kind");return service.execute(p,false,null,u->service.lookup(kind,q(r),paging(r,50)));
 }
 @GetMapping("/hoc-ky/{idHocKy}")ResponseEntity<?> semester(@PathVariable String idHocKy,HttpServletRequest r)throws IOException{
  var p=principal(r);query(r);return response(service.execute(p,true,"mghp_chuyen_vien",u->service.semester(FoundationRules.id(idHocKy))),200,true);
 }
 @PutMapping("/hoc-ky/{idHocKy}/xac-minh")ResponseEntity<?> verifySemester(@PathVariable String idHocKy,HttpServletRequest r)throws IOException{
  var p=principal(r);var b=body(r,"order","startDate","endDate","evidence");return response(service.execute(p,true,"mghp_chuyen_vien",u->service.verifySemester(u,FoundationRules.id(idHocKy),r.getHeader("If-Match"),b)),200,false);
 }
 @GetMapping("/qua-trinh-hoc/{idQuaTrinhHoc}/lich-su")ResponseEntity<?> process(@PathVariable String idQuaTrinhHoc,HttpServletRequest r)throws IOException{
  var p=principal(r);query(r);return response(service.execute(p,true,"mghp_chuyen_vien",u->service.process(FoundationRules.id(idQuaTrinhHoc))),200,true);
 }
 @PutMapping("/qua-trinh-hoc/{idQuaTrinhHoc}/xac-minh")ResponseEntity<?> verifyProcess(@PathVariable String idQuaTrinhHoc,HttpServletRequest r)throws IOException{
  var p=principal(r);var b=body(r,"programId","cohortId","startSemesterId","endSemesterId","actualStart","expectedEnd","actualEnd","evidence","evidenceFileId");
  return response(service.execute(p,true,"mghp_chuyen_vien",u->service.verifyProcess(u,FoundationRules.id(idQuaTrinhHoc),r.getHeader("If-Match"),b)),200,false);
 }
 @PostMapping("/qua-trinh-hoc/{idQuaTrinhHoc}/lich-su-trang-thai")ResponseEntity<?> statusHistory(@PathVariable String idQuaTrinhHoc,HttpServletRequest r)throws IOException{return history(idQuaTrinhHoc,r,true);}
 @PostMapping("/qua-trinh-hoc/{idQuaTrinhHoc}/lich-su-lop")ResponseEntity<?> classHistory(@PathVariable String idQuaTrinhHoc,HttpServletRequest r)throws IOException{return history(idQuaTrinhHoc,r,false);}
 private ResponseEntity<?> history(String id,HttpServletRequest r,boolean status)throws IOException{
  var p=principal(r);var b=status?body(r,"semesterId","status","from","to","decisionNumber","decisionDate","evidence"):body(r,"classId","from","until","evidence");
  return response(service.execute(p,true,"mghp_chuyen_vien",u->service.history(u,FoundationRules.id(id),r.getHeader("If-Match"),b,status)),201,true);
 }
 @GetMapping("/nguoi-dung/{idNguoiDung}/vai-tro-mghp")ResponseEntity<?> roles(@PathVariable String idNguoiDung,HttpServletRequest r)throws IOException{
  var p=principal(r);query(r);return response(service.execute(p,true,"ctsv.roles.manage",u->service.roles(FoundationRules.id(idNguoiDung))),200,true);
 }
 @PutMapping("/nguoi-dung/{idNguoiDung}/vai-tro-mghp/{idVaiTro}")ResponseEntity<?> grant(@PathVariable String idNguoiDung,@PathVariable String idVaiTro,HttpServletRequest r)throws IOException{return role(idNguoiDung,idVaiTro,r,true);}
 @DeleteMapping("/nguoi-dung/{idNguoiDung}/vai-tro-mghp/{idVaiTro}")ResponseEntity<?> revoke(@PathVariable String idNguoiDung,@PathVariable String idVaiTro,HttpServletRequest r)throws IOException{return role(idNguoiDung,idVaiTro,r,false);}
 private ResponseEntity<?> role(String id,String role,HttpServletRequest r,boolean grant)throws IOException{
  var p=principal(r);query(r);return response(service.execute(p,true,"ctsv.roles.manage",u->service.role(u,FoundationRules.id(id),FoundationRules.id(role),r.getHeader("If-Match"),grant)),200,!grant);
 }
 private ResponseEntity<?> response(FoundationService.View view,int status,boolean tag){var b=ResponseEntity.status(status).cacheControl(CacheControl.noStore());if(tag)b.eTag(view.tag());return b.body(view.body());}
 void query(HttpServletRequest r,String...allowed)throws IOException{
  if(r.getInputStream().read()!=-1)throw FoundationFailure.invalid("body");var keys=Set.of(allowed);
  for(var key:r.getParameterMap().keySet())if(!keys.contains(key)||r.getParameterValues(key).length!=1)throw FoundationFailure.invalid(key);
 }
 String q(HttpServletRequest r){String q=r.getParameter("q");if(q!=null&&q.length()>128)throw FoundationFailure.invalid("q");return q;}
 FoundationRules.PageInput paging(HttpServletRequest r,int size){return FoundationRules.page(r.getParameter("page"),r.getParameter("size"),size);}
 JsonNode body(HttpServletRequest r,String...fields)throws IOException{
  if(r.getQueryString()!=null)throw FoundationFailure.invalid("query");
  try{if(r.getContentType()==null||!MediaType.APPLICATION_JSON.includes(MediaType.parseMediaType(r.getContentType())))throw new FoundationFailure(415,"UNSUPPORTED_MEDIA_TYPE");}
  catch(InvalidMediaTypeException e){throw new FoundationFailure(415,"UNSUPPORTED_MEDIA_TYPE");}
  if(r.getHeader("Content-Encoding")!=null&&!"identity".equals(r.getHeader("Content-Encoding")))throw new FoundationFailure(415,"UNSUPPORTED_MEDIA_TYPE");
  byte[] bytes=r.getInputStream().readNBytes(1024*1024+1);if(bytes.length>1024*1024)throw new FoundationFailure(413,"PAYLOAD_TOO_LARGE");
  JsonNode node;try{node=json.readTree(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString());}catch(Exception e){throw FoundationFailure.invalid("body");}
  if(node==null||!node.isObject()||node.size()!=fields.length)throw FoundationFailure.invalid("body");
  for(String field:fields)if(!node.has(field))throw FoundationFailure.invalid(field);return node;
 }
}
