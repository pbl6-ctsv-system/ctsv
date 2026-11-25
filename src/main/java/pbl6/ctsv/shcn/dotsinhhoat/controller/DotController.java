package pbl6.ctsv.shcn.dotsinhhoat.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Collections;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
import pbl6.ctsv.shcn.dotsinhhoat.service.DotService;
import pbl6.ctsv.shcn.dotsinhhoat.service.DotService.*;
import pbl6.ctsv.shcn.dotsinhhoat.validation.DotRequests;

@RestController
@ConditionalOnProperty(name="ctsv.shcn.persistence.enabled",havingValue="true")
public class DotController {
    public static final String PREPARE_HEADER="X-SHCN-Can-Prepare";
    private final AuthAccess auth;private final DotService service;private final DotRequests parser;
    public DotController(AuthAccess auth,DotService service,DotRequests parser){this.auth=auth;this.service=service;this.parser=parser;}
    private AuthAccess.Principal principal(HttpServletRequest r){
        var h=Collections.list(r.getHeaders(HttpHeaders.AUTHORIZATION));if(h.size()!=1||!h.getFirst().startsWith("Bearer ")||h.getFirst().length()==7)throw new DotFailure(401,"UNAUTHENTICATED");return auth.verifyAccessToken(h.getFirst().substring(7));
    }
    private ResponseEntity<?> response(Result<?> result,int status,String location){
        var builder=ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL,"private, no-store").header(PREPARE_HEADER,Boolean.toString(result.canPrepare()));
        if(location!=null)builder.location(URI.create(location));return builder.body(result.value());
    }
    @GetMapping("/api/shcn/dot-sinh-hoat") public ResponseEntity<?> list(HttpServletRequest r){
        var result=service.execute(principal(r),Action.READ,a->{parser.get(r,"q","page","size","sort","maHocKy","maKhoa","trangThai");return service.list(query(r));});return response(result,200,null);
    }
    @GetMapping("/api/shcn/dot-sinh-hoat/{dotId}") public ResponseEntity<?> detail(@PathVariable String dotId,HttpServletRequest r){
        return response(service.execute(principal(r),Action.READ,a->{parser.get(r);return service.detail(DotRequests.id(dotId));}),200,null);
    }
    @GetMapping("/api/shcn/dot-sinh-hoat/{dotId}/doi-tuong") public ResponseEntity<?> audiences(@PathVariable String dotId,HttpServletRequest r){
        return response(service.execute(principal(r),Action.READ,a->{parser.get(r,"q","page","size","sort");return service.audiences(DotRequests.id(dotId),query(r));}),200,null);
    }
    @PostMapping("/api/shcn/dot-sinh-hoat") public ResponseEntity<?> create(HttpServletRequest r){
        Result<DotDTO> result=service.execute(principal(r),Action.PREPARE,a->service.create(a,parser.create(parser.body(r))));
        return response(result,201,"/api/shcn/dot-sinh-hoat/"+result.value().id());
    }
    @PatchMapping("/api/shcn/dot-sinh-hoat/{dotId}") public ResponseEntity<?> update(@PathVariable String dotId,HttpServletRequest r){
        return response(service.execute(principal(r),Action.PREPARE,a->service.update(a,DotRequests.id(dotId),parser.update(parser.body(r)))),200,null);
    }
    @GetMapping("/api/shcn/tra-cuu/hoc-ky") public ResponseEntity<?> semesters(HttpServletRequest r){
        return response(service.execute(principal(r),Action.REFERENCE,a->{parser.get(r,"q","page","size","sort");return service.semesters(query(r));}),200,null);
    }
    @GetMapping("/api/shcn/tra-cuu/nhom-lop") public ResponseEntity<?> groups(HttpServletRequest r){
        return response(service.execute(principal(r),Action.REFERENCE,a->{parser.get(r,"maHocKy");String code=r.getParameter("maHocKy");if(code==null)throw DotFailure.invalid("maHocKy");return service.groups(DotRequests.code(code,"maHocKy"));}),200,null);
    }
    @GetMapping("/api/shcn/tra-cuu/lop-theo-ky") public ResponseEntity<?> periodClasses(HttpServletRequest r){
        return response(service.execute(principal(r),Action.REFERENCE,a->{parser.get(r,"maHocKy","loaiDoiTuong","maDoiTuong","q","page","size","sort");String type=required(r,"loaiDoiTuong");if(!java.util.Set.of("KHOA_HOC","LOP").contains(type))throw DotFailure.invalid("loaiDoiTuong");return service.classes(required(r,"maHocKy"),type,required(r,"maDoiTuong"),query(r));}),200,null);
    }
    @GetMapping("/api/shcn/tra-cuu/sinh-vien-theo-ky") public ResponseEntity<?> periodStudents(HttpServletRequest r){
        return response(service.execute(principal(r),Action.REFERENCE,a->{parser.get(r,"maHocKy","maLop","q","page","size","sort");return service.students(required(r,"maHocKy"),required(r,"maLop"),query(r));}),200,null);
    }
    private static String required(HttpServletRequest r,String name){String value=r.getParameter(name);if(value==null)throw DotFailure.invalid(name);return DotRequests.code(value,name);}
    private Query query(HttpServletRequest r){return DotRequests.query(r.getParameter("page"),r.getParameter("size"),r.getParameter("sort"),r.getParameter("q"),r.getParameter("maHocKy"),r.getParameter("maKhoa"),r.getParameter("trangThai"));}
}
