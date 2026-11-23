package pbl6.ctsv.shcn.dotsinhhoat.service;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.entity.DotSinhHoat;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
import pbl6.ctsv.shcn.dotsinhhoat.reference.DotReference;
import pbl6.ctsv.shcn.dotsinhhoat.repository.DotRepository;

public final class DotService {
    public record Actor(FoundationAccess.Identity identity,Quyen permissions){}
    public record Result<T>(T value,boolean canPrepare){}
    public enum Action{READ,PREPARE,REFERENCE}
    private final TransactionTemplate tx;private final FoundationAccess access;private final DotReference reference;private final DotRepository repository;private final Clock clock;
    public DotService(PlatformTransactionManager manager,FoundationAccess access,DotReference reference,DotRepository repository,Clock clock){
        tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        this.access=access;this.reference=reference;this.repository=repository;this.clock=clock;
    }
    public <T> Result<T> execute(AuthAccess.Principal principal,Action action,Function<Actor,T> command){
        return tx.execute(status->{var identity=access.identify(principal,true);var p=reference.permissions(identity);
            if(!(switch(action){case READ->p.canRead();case PREPARE->p.prepare();case REFERENCE->p.reference();}))throw new DotFailure(403,"FORBIDDEN");
            return new Result<>(command.apply(new Actor(identity,p)),p.prepare());});
    }
    public NhomLopDTO groups(String code){return reference.groups(code);}
    public Page<HocKyDTO> semesters(Query query){return reference.semesters(query);}
    public Page<DotDTO> list(Query q){var p=repository.list(q);return new Page<>(p.items().stream().map(this::dto).toList(),p.page(),p.size(),p.totalElements());}
    public DotDTO detail(long id){return dto(repository.find(id,false));}
    public DotDTO create(Actor actor,CauHinh input){
        reference.requireSemester(input.maHocKy());if(input.doiTuongApDung()!=null)reference.requireAudiences(input.maHocKy(),input.doiTuongApDung());var root=repository.create(input,actor.identity().principal().accountId().toString(),OffsetDateTime.now(clock));
        if(input.doiTuongApDung()!=null)repository.replaceAudiences(root.getId(),input.doiTuongApDung(),OffsetDateTime.now(clock));
        repository.audit(actor.identity().userId(),"SHCN_DOT_CREATE_DRAFT",root.getId());return dto(root);
    }
    public DotDTO update(Actor actor,long id,SuaDot input){
        var root=repository.find(id,true);if(!Arrays.equals(root.getPhienBanGhi(),input.expectedVersion()))throw new DotFailure(409,"VERSION_CONFLICT");
        writable(root);reference.requireSemester(input.cauHinh().maHocKy());
        var c=input.cauHinh();var old=repository.audiences(id);var effective=c.doiTuongApDung()==null?old:c.doiTuongApDung();
        boolean groupsChanged=c.doiTuongApDung()!=null&&!old.equals(c.doiTuongApDung());
        if(c.doiTuongApDung()!=null||!Objects.equals(root.getMaHocKy(),c.maHocKy()))reference.requireAudiences(c.maHocKy(),effective);
        boolean reset=groupsChanged||!Objects.equals(root.getMaHocKy(),c.maHocKy())||!Objects.equals(root.getKhungNgayBatDau(),c.khungNgayBatDau())||!Objects.equals(root.getKhungNgayKetThuc(),c.khungNgayKetThuc())||!repository.ranges(root.getKhungTietChoPhep()).equals(c.khungTietChoPhep())||!root.getHanChotDangKyLich().toInstant().equals(c.hanChotDangKyLich().toInstant());
        var saved=repository.update(id,input.expectedVersion(),c,reset,OffsetDateTime.now(clock));if(c.doiTuongApDung()!=null)repository.replaceAudiences(id,c.doiTuongApDung(),OffsetDateTime.now(clock));repository.audit(actor.identity().userId(),"SHCN_DOT_UPDATE_DRAFT",id);return dto(saved);
    }
    public static void writable(DotSinhHoat root){
        if(!Set.of("NHAP","BI_TRA_LAI").contains(root.getTrangThai())||Set.of("CHO_PHO_PHONG_DUYET","CHO_BGH_KY","CO_HIEU_LUC").contains(root.getTrangThaiPhanCong())
          ||root.getMaHoSoDuyetDot()!=null||root.getMaHoSoTaiLieuPhanCong()!=null||root.getMaHoSoTrinhLich()!=null||root.getNgayPhatHanh()!=null||root.getNgayHuy()!=null||root.getNgayCongBoLich()!=null)
            throw new DotFailure(409,"STATE_CONFLICT"); // Unknown shared workflow is locked, never assumed completed.
    }
    private static String time(OffsetDateTime value){return value==null?null:value.toInstant().toString();}
    public DotDTO dto(DotSinhHoat d){return new DotDTO(d.getId().toString(),Base64.getEncoder().encodeToString(d.getPhienBanGhi()),d.getMaHocKy(),d.getTenDot(),d.getKhungNgayBatDau(),d.getKhungNgayKetThuc(),repository.ranges(d.getKhungTietChoPhep()),time(d.getHanChotDangKyLich()),d.getTrangThai(),d.getTrangThaiPhanCong(),d.getPhienBanPhanCongApDung(),d.getTrangThaiLichTongHop(),d.getPhienBanLichTongHop(),time(d.getNgayPhatHanh()),time(d.getNgayCongBoLich()),time(d.getNgayTao()),time(d.getNgayCapNhat()),repository.audiences(d.getId()));}
}
