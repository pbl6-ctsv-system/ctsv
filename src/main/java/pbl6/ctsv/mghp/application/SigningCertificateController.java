package pbl6.ctsv.mghp.application;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import static pbl6.ctsv.mghp.application.IntakeRules.*;

@RestController @RequestMapping("/api/ctsv/mghp/quan-tri-ky")
@ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningCertificateController {
 final SigningSupportHttp http;final SigningCertificateService certificates;
 SigningCertificateController(SigningSupportHttp http,SigningCertificateService certificates){this.http=http;this.certificates=certificates;}
 @GetMapping("/chung-thu") ResponseEntity<?> list(HttpServletRequest r){http.query(r,"filter");return http.reply(certificates.list(http.principal(r),r.getParameter("filter")==null?"all":r.getParameter("filter")));}
 @GetMapping("/chung-thu/{id}") ResponseEntity<?> read(@PathVariable String id,HttpServletRequest r){http.query(r);return http.reply(certificates.read(http.principal(r),id(id,"id")));}
 @PostMapping("/chung-thu/kiem-tra") ResponseEntity<?> all(HttpServletRequest r)throws IOException{http.query(r);var p=http.principal(r);shape(http.body(r),java.util.List.of());return http.reply(certificates.checkAll(p));}
 @PostMapping("/chung-thu/{id}/kiem-tra") ResponseEntity<?> check(@PathVariable String id,HttpServletRequest r)throws IOException{http.query(r);var p=http.principal(r);shape(http.body(r),java.util.List.of());return http.reply(certificates.check(p,id(id,"id")));}
 @PostMapping("/chung-thu/{id}/ghi-chu") ResponseEntity<?> note(@PathVariable String id,HttpServletRequest r)throws IOException{http.query(r);return http.reply(certificates.note(http.principal(r),id(id,"id"),http.body(r),r.getHeader("If-Match")));}
 @PostMapping("/chung-thu/{id}/gia-han") ResponseEntity<?> renew(@PathVariable String id,HttpServletRequest r)throws IOException{http.query(r);var result=certificates.renew(http.principal(r),id(id,"id"),http.body(r),r.getHeader("If-Match"));return ResponseEntity.status(result.created()?201:200).cacheControl(CacheControl.noStore()).eTag(result.detail().tag()).body(result.detail().body());}
 @PostMapping("/chung-thu/{id}/kiem-tra-gia-han") ResponseEntity<?> renewal(@PathVariable String id,HttpServletRequest r)throws IOException{http.query(r);var p=http.principal(r);shape(http.body(r),java.util.List.of());return http.reply(certificates.checkRenewal(p,id(id,"id")));}
}
