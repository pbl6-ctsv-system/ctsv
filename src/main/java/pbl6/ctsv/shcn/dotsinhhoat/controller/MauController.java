package pbl6.ctsv.shcn.dotsinhhoat.controller;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
import pbl6.ctsv.shcn.dotsinhhoat.service.*;
import pbl6.ctsv.shcn.dotsinhhoat.validation.*;
@RestController @ConditionalOnProperty(name="ctsv.shcn.persistence.enabled",havingValue="true")
public class MauController {
 private final AuthAccess auth;private final DotService access;private final MauService service;private final DotRequests body;private final MauRequests validator;
 public MauController(AuthAccess auth,DotService access,MauService service,DotRequests body,MauRequests validator){this.auth=auth;this.access=access;this.service=service;this.body=body;this.validator=validator;}
 private AuthAccess.Principal principal(HttpServletRequest r){var h=Collections.list(r.getHeaders(HttpHeaders.AUTHORIZATION));if(h.size()!=1||!h.getFirst().startsWith("Bearer ")||h.getFirst().length()==7)throw new DotFailure(401,"UNAUTHENTICATED");return auth.verifyAccessToken(h.getFirst().substring(7));}
 private ResponseEntity<?> response(DotService.Result<?> r){return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").header(DotController.PREPARE_HEADER,Boolean.toString(r.canPrepare())).body(r.value());}
 @GetMapping("/api/shcn/tra-cuu/cau-truc-mau-bien-ban") public ResponseEntity<?> catalog(HttpServletRequest r){return response(access.execute(principal(r),DotService.Action.READ,a->{body.get(r);return TemplateDictionary.CATALOG;}));}
 @GetMapping("/api/shcn/dot-sinh-hoat/{dotId}/mau-bien-ban") public ResponseEntity<?> read(@PathVariable String dotId,HttpServletRequest r){return response(access.execute(principal(r),DotService.Action.READ,a->{body.get(r);return service.read(DotRequests.id(dotId),a);}));}
 @PutMapping("/api/shcn/dot-sinh-hoat/{dotId}/mau-bien-ban") public ResponseEntity<?> save(@PathVariable String dotId,HttpServletRequest r){return response(access.execute(principal(r),DotService.Action.PREPARE,a->service.save(DotRequests.id(dotId),validator.parse(body.body(r)),a)));}
}
