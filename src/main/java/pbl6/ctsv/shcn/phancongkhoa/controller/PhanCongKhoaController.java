package pbl6.ctsv.shcn.phancongkhoa.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.shcn.phancongkhoa.dto.PhanCongKhoaDtos.DanhSachDTO;
import pbl6.ctsv.shcn.phancongkhoa.dto.PhanCongKhoaDtos.HoSoKyKhoaDTO;
import pbl6.ctsv.shcn.phancongkhoa.exception.PhanCongKhoaFailure;
import pbl6.ctsv.shcn.phancongkhoa.service.PhanCongKhoaService;
import pbl6.ctsv.shcn.phancongkhoa.signing.PhanCongSigningService;
import pbl6.ctsv.shcn.phancongkhoa.validation.PhanCongKhoaRequests;
import static pbl6.ctsv.shcn.phancongkhoa.validation.PhanCongKhoaRequests.*;

@RestController
@RequestMapping("/api/shcn/khoa")
@ConditionalOnProperty(name="ctsv.shcn.persistence.enabled",havingValue="true")
public class PhanCongKhoaController {
    private final AuthAccess auth;
    private final PhanCongKhoaService service;
    private final PhanCongSigningService signingService;
    private final PhanCongKhoaRequests requests;

    public PhanCongKhoaController(AuthAccess auth,PhanCongKhoaService service,PhanCongKhoaRequests requests){
        this(auth,service,null,requests);
    }

    @Autowired
    public PhanCongKhoaController(AuthAccess auth,PhanCongKhoaService service,
            PhanCongSigningService signingService,PhanCongKhoaRequests requests){
        this.auth=auth;this.service=service;this.signingService=signingService;this.requests=requests;
    }

    private PhanCongSigningService signing(){
        if(signingService==null)throw PhanCongKhoaFailure.unavailable();
        return signingService;
    }

    private AuthAccess.Principal principal(HttpServletRequest request){
        var headers=Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if(headers.size()!=1||!headers.getFirst().startsWith("Bearer ")||headers.getFirst().length()==7)throw new PhanCongKhoaFailure(401,"UNAUTHENTICATED");
        return auth.verifyAccessToken(headers.getFirst().substring(7));
    }
    private static ResponseEntity<?> response(Object value,int status,String location){
        var response=ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL,"private, no-store");
        if(location!=null)response.location(URI.create(location));
        if(value instanceof HoSoKyKhoaDTO dossier&&dossier.version()!=null){
            response.eTag("\""+dossier.version()+"\"");
        }
        return response.body(value);
    }
    private static String khoa(HttpServletRequest r){return requiredCode(r.getParameter("maKhoa"),"maKhoa");}
    private static PageQuery page(HttpServletRequest r){return PhanCongKhoaRequests.page(r.getParameter("page"),r.getParameter("size"),r.getParameter("q"));}

    @GetMapping("/pham-vi") public ResponseEntity<?> phamVi(HttpServletRequest r){
        var p=principal(r);return response(service.thucHien(p,false,a->{requests.query(r);return service.phamVi(a);}),200,null);
    }
    @GetMapping("/chuong-trinh") public ResponseEntity<?> chuongTrinh(HttpServletRequest r){
        var p=principal(r);return response(service.thucHien(p,false,a->{requests.query(r,"maKhoa","q","page","size");return service.chuongTrinh(a,khoa(r),page(r));}),200,null);
    }
    @GetMapping("/lop") public ResponseEntity<?> lop(HttpServletRequest r){
        var p=principal(r);return response(service.thucHien(p,false,a->{requests.query(r,"maKhoa","chuongTrinhId","q","page","size");Long program=r.getParameter("chuongTrinhId")==null?null:id(r.getParameter("chuongTrinhId"),"chuongTrinhId");return service.lop(a,khoa(r),program,page(r));}),200,null);
    }
    @GetMapping("/giang-vien") public ResponseEntity<?> giangVien(HttpServletRequest r){
        var p=principal(r);return response(service.thucHien(p,false,a->{requests.query(r,"maKhoa","q","page","size");return service.giangVien(a,khoa(r),page(r));}),200,null);
    }
    @GetMapping("/phan-cong") public ResponseEntity<?> danhSach(HttpServletRequest r){
        var p=principal(r);return response(service.thucHien(p,false,a->{requests.query(r,"maKhoa","namHoc","phienBan","page","size");return service.danhSach(a,khoa(r),requiredYear(r.getParameter("namHoc")),optionalVersion(r.getParameter("phienBan")),page(r));}),200,null);
    }
    @PostMapping("/phan-cong") public ResponseEntity<?> tao(HttpServletRequest r){
        var p=principal(r);DanhSachDTO result=service.thucHien(p,true,a->service.tao(a,requests.tao(requests.body(r))));
        return response(result,201,"/api/shcn/khoa/phan-cong/"+result.id());
    }
    @GetMapping("/phan-cong/{danhSachId}") public ResponseEntity<?> chiTiet(@PathVariable String danhSachId,HttpServletRequest r){
        var p=principal(r);return response(service.thucHien(p,false,a->{requests.query(r);return service.chiTiet(a,id(danhSachId,"danhSachId"));}),200,null);
    }
    @DeleteMapping("/phan-cong/{danhSachId}") public ResponseEntity<Void> xoa(@PathVariable String danhSachId,HttpServletRequest r){
        var p=principal(r);service.thucHien(p,true,a->{long key=id(danhSachId,"danhSachId");service.xoa(a,key,requests.xoa(requests.body(r)));return null;});
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL,"private, no-store").build();
    }
    @PutMapping("/phan-cong/{danhSachId}") public ResponseEntity<?> luu(@PathVariable String danhSachId,HttpServletRequest r){
        var p=principal(r);return response(service.thucHien(p,true,a->{long key=id(danhSachId,"danhSachId");return service.luu(a,key,requests.luu(requests.body(r)));}),200,null);
    }

    // --- TASK-TK-02: PDF export, metadata edit, CA signing, reconcile, reopen, specialist submission ---

    @PostMapping("/phan-cong/{danhSachId}/xuat-pdf")
    public ResponseEntity<?> xuatPdf(@PathVariable String danhSachId,HttpServletRequest r){
        var p=principal(r);long key=id(danhSachId,"danhSachId");
        var out=signing().xuatPdf(p,key,requests.xuatPdf(requests.body(r)));
        return response(out.dossier(),out.created()?201:200,"/api/shcn/khoa/phan-cong/"+key+"/ho-so-ky");
    }

    @GetMapping("/phan-cong/{danhSachId}/ho-so-ky")
    public ResponseEntity<?> hoSoKy(@PathVariable String danhSachId,HttpServletRequest r){
        var p=principal(r);requests.query(r);long key=id(danhSachId,"danhSachId");
        return response(signing().hoSoKy(p,key),200,null);
    }

    @PutMapping("/phan-cong/{danhSachId}/noi-dung-pdf")
    public ResponseEntity<?> capNhatNoiDungPdf(@PathVariable String danhSachId,HttpServletRequest r){
        var p=principal(r);long key=id(danhSachId,"danhSachId");
        return response(signing().capNhatNoiDungPdf(p,key,requests.capNhatNoiDungPdf(requests.body(r))),200,null);
    }

    @PostMapping("/phan-cong/{danhSachId}/ky-so")
    public ResponseEntity<?> kySo(@PathVariable String danhSachId,HttpServletRequest r){
        var p=principal(r);long key=id(danhSachId,"danhSachId");
        return response(signing().kySo(p,key,requests.kySo(requests.body(r))),202,null);
    }

    @PostMapping("/phan-cong/{danhSachId}/ky-so/doi-soat")
    public ResponseEntity<?> doiSoat(@PathVariable String danhSachId,HttpServletRequest r){
        var p=principal(r);long key=id(danhSachId,"danhSachId");
        return response(signing().doiSoat(p,key,requests.doiSoatKy(requests.body(r))),202,null);
    }

    @PostMapping("/phan-cong/{danhSachId}/gui-chuyen-vien")
    public ResponseEntity<?> guiChuyenVien(@PathVariable String danhSachId,HttpServletRequest r){
        var p=principal(r);long key=id(danhSachId,"danhSachId");
        return response(signing().guiChuyenVien(p,key,requests.guiChuyenVien(requests.body(r))),200,null);
    }

    @PostMapping("/phan-cong/{danhSachId}/huy-de-sua")
    public ResponseEntity<?> huyDeSua(@PathVariable String danhSachId,HttpServletRequest r){
        var p=principal(r);long key=id(danhSachId,"danhSachId");
        return response(signing().huyDeSua(p,key,requests.huyDeSua(requests.body(r))),200,null);
    }
}
