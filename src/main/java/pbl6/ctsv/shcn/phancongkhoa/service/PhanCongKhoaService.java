package pbl6.ctsv.shcn.phancongkhoa.service;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shcn.foundation.PhanCongKhoaFoundationPort;
import pbl6.ctsv.shcn.foundation.PhanCongKhoaFoundationPort.*;
import pbl6.ctsv.shcn.phancongkhoa.dto.PhanCongKhoaDtos.*;
import pbl6.ctsv.shcn.phancongkhoa.entity.*;
import pbl6.ctsv.shcn.phancongkhoa.repository.*;
import pbl6.ctsv.shcn.phancongkhoa.validation.PhanCongKhoaRequests.*;
import pbl6.ctsv.shcn.phancongkhoa.exception.PhanCongKhoaFailure;
import static pbl6.ctsv.shcn.phancongkhoa.exception.PhanCongKhoaFailure.*;

public final class PhanCongKhoaService {
    private final TransactionTemplate tx;
    private final FoundationAccess identities;
    private final PhanCongKhoaFoundationPort reference;
    private final PhanCongKhoaRepository repository;
    private final PhanCongKhoaAudit audit;
    private final Clock clock;
    public PhanCongKhoaService(PlatformTransactionManager manager,FoundationAccess identities,PhanCongKhoaFoundationPort reference,
            PhanCongKhoaRepository repository,PhanCongKhoaAudit audit,Clock clock){
        this.tx=new TransactionTemplate(manager);this.identities=identities;this.reference=reference;this.repository=repository;this.audit=audit;this.clock=clock;
    }
    /** Parse request content inside action, after trusted identity and capability checks. */
    public <T>T thucHien(AuthAccess.Principal principal,boolean write,Function<Actor,T> action){
        return tx.execute(status->{
            var actor=reference.quyen(identities.identify(principal,true));
            if(write?!actor.lap():!actor.doc())throw new PhanCongKhoaFailure(403,"FORBIDDEN");
            return action.apply(actor);
        });
    }
    public PhamViDTO phamVi(Actor actor){return reference.phamVi(actor);}
    public Khoa khoa(Actor actor,String code,boolean write){
        var matches=actor.khoa().stream().filter(k->k.ma().equals(code)&&(write?k.lap():k.doc())).toList();
        if(matches.isEmpty())throw notFound();if(matches.size()!=1)throw unavailable();return matches.getFirst();
    }
    public Page<ChuongTrinhDTO> chuongTrinh(Actor actor,String code,PageQuery page){return reference.chuongTrinh(khoa(actor,code,false),page);}
    public Page<LopDTO> lop(Actor actor,String code,Long program,PageQuery page){return reference.lop(khoa(actor,code,false),program,page);}
    public Page<GiangVienDTO> giangVien(Actor actor,String code,PageQuery page){return reference.giangVien(khoa(actor,code,false),page);}
    public Page<DanhSachTomTatDTO> danhSach(Actor actor,String code,String year,Integer version,PageQuery page){
        khoa(actor,code,false);var rows=repository.trangDanhSach(code,year,version,page.offset(),page.size());
        var counts=repository.demCacPhanCong(rows.items().stream().map(DanhSachPhanCongKhoa::getId).toList());
        return new Page<>(rows.items().stream().map(r->tomTat(r,counts.getOrDefault(r.getId(),0))).toList(),page.page(),page.size(),rows.total());
    }
    public DanhSachDTO tao(Actor actor,TaoNhap input){
        var faculty=khoa(actor,input.maKhoa(),true);
        if(!faculty.dangHoatDong()||!reference.maLuuDuoc(faculty.ma()))throw conflict("SHCN_THAM_CHIEU_KHONG_CON_HOP_LE");
        // Serialize version allocation before taking a predecessor root lock; all creates follow this order.
        int next=repository.phienBanTiepTheo(input.maKhoa(),input.namHoc());
        if(next!=input.phienBanDuKien())throw conflict("SHCN_PHIEN_BAN_KHONG_HOP_LE");
        List<PhanCongGvcnKhoa> inherited=List.of();
        if(input.maDanhSachTruoc()!=null){
            var old=locked(actor,input.maDanhSachTruoc(),false);
            if(!old.getMaKhoa().equals(input.maKhoa())||!Set.of("DA_KY","DA_GUI_CHUYEN_VIEN","DA_THAY_THE").contains(old.getTrangThai()))throw conflict("SHCN_NGUON_KE_THUA_KHONG_HOP_LE");
            inherited=repository.cacPhanCong(old.getId());
            if(inherited.size()>2000)throw conflict("SHCN_CAN_REVIEW_GIOI_HAN");
        }
        var now=OffsetDateTime.now(clock);
        var row=repository.taoDanhSach(DanhSachPhanCongKhoa.nhap(input.maKhoa(),input.namHoc(),next,input.maDanhSachTruoc(),input.hieuLucTu(),input.hieuLucDen(),actor.accountId(),input.lyDoDieuChinh(),now));
        long id=row.getId();if(!inherited.isEmpty())repository.saoChepCacDong(id,inherited,now);
        audit.ghi(actor.userId(),"SHCN_KHOA_CREATE_DRAFT",id,input.lyDoDieuChinh());return chiTiet(actor,id);
    }
    public DanhSachDTO chiTiet(Actor actor,long id){
        var root=locked(actor,id,false);var faculty=khoa(actor,root.getMaKhoa(),false);var rows=repository.cacPhanCong(id);
        var classes=reference.lopTheoMa(faculty,rows.stream().map(PhanCongGvcnKhoa::getMaLop).toList());
        var teachers=reference.giangVienTheoMa(faculty,rows.stream().map(PhanCongGvcnKhoa::getMaGiaoVien).toList());
        var lines=rows.stream().map(r->new DongPhanCongDTO(r.getId().toString(),r.getMaLop(),r.getMaGiaoVien(),r.getGhiChu(),classes.get(r.getMaLop()),teachers.get(r.getMaGiaoVien()))).toList();
        return new DanhSachDTO(root.getId().toString(),root.getMaKhoa(),root.getNamHoc(),root.getPhienBan(),root.getTrangThai(),
            instant(root.getHieuLucTu()),instant(root.getHieuLucDen()),rows.size(),instant(root.getNgayTao()),instant(root.getNgayCapNhat()),
            root.getMaDanhSachTruoc()==null?null:root.getMaDanhSachTruoc().toString(),root.getMaNguoiLap(),root.getLyDoDieuChinh(),Base64.getEncoder().encodeToString(root.getPhienBanGhi()),lines);
    }
    public DanhSachDTO luu(Actor actor,long id,LuuNhap input){
        var old=locked(actor,id,true);var faculty=khoa(actor,old.getMaKhoa(),true);
        if(!Arrays.equals(input.expectedVersion(),old.getPhienBanGhi()))throw conflict("VERSION_CONFLICT");
        if(!"NHAP".equals(old.getTrangThai()))throw conflict("SHCN_KHONG_CON_LA_NHAP");
        if(!faculty.dangHoatDong()||!reference.maLuuDuoc(faculty.ma()))throw conflict("SHCN_THAM_CHIEU_KHONG_CON_HOP_LE");
        if(reference.trungMaLopTheoCollation(input.cacPhanCong().stream().map(PhanCongKhoaRepository.DongNhap::maLop).toList()))throw invalid("cacPhanCong");
        var classes=reference.lopTheoMa(faculty,input.cacPhanCong().stream().map(PhanCongKhoaRepository.DongNhap::maLop).toList());
        var teachers=reference.giangVienTheoMa(faculty,input.cacPhanCong().stream().map(PhanCongKhoaRepository.DongNhap::maGiaoVien).toList());
        for(var row:input.cacPhanCong()){
            var cls=classes.get(row.maLop());var teacher=teachers.get(row.maGiaoVien());
            if(cls==null||teacher==null||!cls.coTheChon()||!teacher.coTheChon())throw conflict("SHCN_THAM_CHIEU_KHONG_CON_HOP_LE");
        }
        repository.luuNhap(id,input.expectedVersion(),input.hieuLucTu(),input.hieuLucDen(),input.lyDoDieuChinh(),input.cacPhanCong());
        audit.ghi(actor.userId(),"SHCN_KHOA_SAVE_DRAFT",id,input.lyDoDieuChinh());return chiTiet(actor,id);
    }
    private DanhSachPhanCongKhoa locked(Actor actor,long id,boolean write){
        var root=repository.khoaDanhSach(id).orElseThrow(PhanCongKhoaFailure::notFound);khoa(actor,root.getMaKhoa(),write);return root;
    }
    private static DanhSachTomTatDTO tomTat(DanhSachPhanCongKhoa r,int count){return new DanhSachTomTatDTO(r.getId().toString(),r.getMaKhoa(),r.getNamHoc(),r.getPhienBan(),r.getTrangThai(),instant(r.getHieuLucTu()),instant(r.getHieuLucDen()),count,instant(r.getNgayTao()),instant(r.getNgayCapNhat()));}
    private static String instant(OffsetDateTime value){return value==null?null:value.toInstant().toString();}
}
