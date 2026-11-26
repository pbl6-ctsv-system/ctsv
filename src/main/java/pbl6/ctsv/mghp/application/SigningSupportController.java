package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pbl6.ctsv.auth.AuthAccess;
import static pbl6.ctsv.mghp.application.IntakeRules.*;

@RestController @RequestMapping("/api/ctsv/mghp/quan-tri-ky")
@ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningSupportController {
 final SigningSupportService support;final SigningSupportHttp http;
 SigningSupportController(SigningSupportService support,SigningSupportHttp http){this.support=support;this.http=http;}
 AuthAccess.Principal principal(HttpServletRequest r){return http.principal(r);}
 void query(HttpServletRequest r,String...allowed){http.query(r,allowed);}
 JsonNode body(HttpServletRequest r)throws IOException{return http.body(r);}
 ResponseEntity<?> reply(ApplicationReads.Detail view){return http.reply(view);}
 ResponseEntity<?> reply(Map<String,Object> view){return http.reply(view);}
 @GetMapping("/su-co") ResponseEntity<?> list(HttpServletRequest r){query(r,"status","q","page","size");return reply(support.list(principal(r),r.getParameter("status")==null?"waiting":r.getParameter("status"),r.getParameter("q")==null?"":r.getParameter("q"),page(r.getParameter("page"),"page",0,0,Integer.MAX_VALUE),page(r.getParameter("size"),"size",20,1,100)));}
 @GetMapping("/su-co/{id}") ResponseEntity<?> read(@PathVariable String id,HttpServletRequest r){query(r);return reply(support.read(principal(r),id(id,"id")));}
 @PostMapping("/su-co/{id}/bat-dau") ResponseEntity<?> start(@PathVariable String id,HttpServletRequest r)throws IOException{query(r);return reply(support.change(principal(r),id(id,"id"),"start",body(r),r.getHeader("If-Match")));}
 @PostMapping("/su-co/{id}/ket-thuc") ResponseEntity<?> finish(@PathVariable String id,HttpServletRequest r)throws IOException{query(r);return reply(support.change(principal(r),id(id,"id"),"finish",body(r),r.getHeader("If-Match")));}
 @PostMapping("/su-co/{id}/ghi-chu") ResponseEntity<?> note(@PathVariable String id,HttpServletRequest r)throws IOException{query(r);return reply(support.note(principal(r),id(id,"id"),body(r),r.getHeader("If-Match")));}
 @GetMapping("/tep-luu-cho") ResponseEntity<?> files(HttpServletRequest r){query(r,"filter");return reply(support.files(principal(r),r.getParameter("filter")==null?"all":r.getParameter("filter")));}
 @PostMapping("/tep-luu-cho/{operation}/xoa") ResponseEntity<?> delete(@PathVariable String operation,HttpServletRequest r)throws IOException{query(r);UUID id;try{id=UUID.fromString(operation);if(!id.toString().equals(operation))throw new IllegalArgumentException();}catch(IllegalArgumentException e){throw IntakeProblem.invalid("operationId");}return reply(support.deleteFile(principal(r),id,body(r)));}
}
