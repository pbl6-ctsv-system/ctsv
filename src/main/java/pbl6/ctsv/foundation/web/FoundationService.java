package pbl6.ctsv.foundation.web;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.security.MessageDigest;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.dto.FoundationDtos.SemesterDetail;
import pbl6.ctsv.foundation.entity.*;
import pbl6.ctsv.foundation.mapper.FoundationMapper;
import pbl6.ctsv.foundation.repository.FoundationRepository;

/** Business validation and transaction orchestration. Persistence is owned by FoundationRepository. */
final class FoundationService {
    private final FoundationRepository repository;
    private final FoundationSecurity security;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final pbl6.ctsv.foundation.api.FoundationUsage usage;
    record View(Object body,String tag) {}
    FoundationService(FoundationSecurity security,FoundationRepository repository,PlatformTransactionManager manager,ObjectMapper json,pbl6.ctsv.foundation.api.FoundationUsage usage) {
        this.security=security;this.repository=repository;this.json=json;this.usage=usage;
        tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    <T> T execute(AuthAccess.Principal p,boolean online,String capability,Function<Long,T> action) {
        return tx.execute(s -> action.apply(security.authorize(p,online,capability).userId()));
    }
    FoundationSecurity.MenuAccess menuAccess(AuthAccess.Principal principal) {
        return tx.execute(status -> security.menuAccess(principal));
    }
    View view(Object body,byte[] version) {
        try {
            var digest=MessageDigest.getInstance("SHA-256");digest.update(version);
            return new View(body,"\""+Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(json.writeValueAsBytes(body)))+"\"");
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    void match(View view,String tag) {
        if(tag==null)throw new FoundationFailure(428,"PRECONDITION_REQUIRED");
        if(!tag.matches("\"[A-Za-z0-9_-]+\""))throw FoundationFailure.invalid("If-Match");
        if(!view.tag.equals(tag))throw new FoundationFailure(412,"VERSION_CONFLICT");
    }
    Object students(String q,FoundationRules.PageInput p) { return repository.students(q,p.page(),p.size(),Math.toIntExact(p.offset())); }
    Object student(long id) { return FoundationMapper.student(repository.find(SinhVien.class,id),repository.processes(id)); }
    Object lookup(String kind,String q,FoundationRules.PageInput p) { return repository.lookup(kind,q,p.page(),p.size(),Math.toIntExact(p.offset())); }
    View semester(long id) {
        var version=repository.version("hoc_ky",id);
        return view(FoundationMapper.semester(repository.find(HocKy.class,id)),version);
    }
    View verifySemester(long actor,long id,String tag,JsonNode b) {
        var current=semester(id);match(current,tag);
        if(!b.path("order").isIntegralNumber()||!b.path("order").canConvertToInt()||b.path("order").intValue()<1)throw FoundationFailure.invalid("order");
        int order=b.get("order").intValue();
        var from=date(b,"startDate",false);var until=date(b,"endDate",false);FoundationRules.dates(from,until);
        String evidence=text(b,"evidence",16000,false);
        var old=(SemesterDetail)current.body;
        boolean changed=!Objects.equals(old.order(),order)||!Objects.equals(old.startDate(),from.toString())||!Objects.equals(old.endDate(),until.toString());
        if(repository.semesterOrderTaken(order,id)||changed&&usage.semesterUsed(id))throw new FoundationFailure(409,"SEMESTER_VERIFICATION_CONFLICT");
        if(!changed&&Objects.equals(old.evidence(),evidence)&&Objects.equals(old.verificationStatus(),"verified"))return current;
        // The aggregate was fenced with UPDLOCK/HOLDLOCK before comparing its strong ETag.
        var h=repository.find(HocKy.class,id);
        h.setSoThuTu(order);h.setNgayBatDau(from);h.setNgayKetThuc(until);h.setCanCuXacMinhMoc(evidence);h.setTrangThaiXacMinhMoc("verified");
        repository.flush();repository.audit(actor,"SEMESTER_VERIFIED","hoc_ky",id,evidence);
        return semester(id);
    }
    View process(long id) {
        var version=repository.version("qua_trinh_theo_hoc",id);
        return view(FoundationMapper.process(repository.find(QuaTrinhTheoHoc.class,id),repository.statuses(id),repository.classes(id)),version);
    }
    View verifyProcess(long actor,long id,String tag,JsonNode b) {
        var current=process(id);match(current,tag);
        Long program=optionalId(b,"programId"),cohort=optionalId(b,"cohortId"),start=optionalId(b,"startSemesterId"),end=optionalId(b,"endSemesterId"),file=optionalId(b,"evidenceFileId");
        repository.foreignKey("chuong_trinh_dao_tao",program);repository.foreignKey("khoa_hoc",cohort);repository.foreignKey("hoc_ky",start);repository.foreignKey("hoc_ky",end);repository.foreignKey("tep_tin",file);
        var from=date(b,"actualStart",true);var expected=date(b,"expectedEnd",true);var actual=date(b,"actualEnd",true);
        if(from!=null){FoundationRules.dates(from,expected);FoundationRules.dates(from,actual);}
        String evidence=text(b,"evidence",16000,false);
        if(usage.processUsed(id))throw new FoundationFailure(409,"HISTORY_IN_USE");
        var p=repository.find(QuaTrinhTheoHoc.class,id);
        p.setChuongTrinhDaoTaoId(program);p.setKhoaHocId(cohort);p.setHocKyBatDauId(start);p.setHocKyKetThucNguonId(end);
        p.setThoiDiemBatDau(from);p.setNgayKetThucDuKien(expected);p.setNgayKetThucThucTe(actual);p.setCanCuXacMinh(evidence);p.setTepCanCuId(file);
        p.setTrangThaiDoiChieu(program!=null&&cohort!=null&&start!=null&&from!=null?"verified":"partial");
        p.setNguoiXacMinhId(actor);p.setThoiDiemXacMinh(repository.now());
        repository.flush();repository.audit(actor,"PROCESS_VERIFIED","qua_trinh_theo_hoc",id,evidence);return process(id);
    }
    View history(long actor,long id,String tag,JsonNode b,boolean status) {
        var current=process(id);match(current,tag);var version=repository.version("qua_trinh_theo_hoc",id);
        if(usage.processUsed(id))throw new FoundationFailure(409,"HISTORY_IN_USE");
        LocalDate from=date(b,"from",false),end=date(b,status?"to":"until",true);FoundationRules.dates(from,end);
        String evidence=text(b,"evidence",16000,false);
        long reference=FoundationRules.id(text(b,status?"semesterId":"classId",19,false));repository.foreignKey(status?"hoc_ky":"lop_sinh_hoat",reference);
        if(repository.overlap(id,from,end,status))throw new FoundationFailure(409,"HISTORY_CONFLICT");
        if(status) {
            var h=new LichSuTrangThaiSv();h.setQuaTrinhHocId(id);h.setHocKyApDungId(reference);h.setTrangThai(text(b,"status",128,false));
            h.setNgayBatDauHieuLuc(from);h.setNgayKetThucHieuLuc(end);h.setSoQdNguon(text(b,"decisionNumber",512,true));h.setNgayQdNguon(date(b,"decisionDate",true));
            h.setCanCuXacMinh(evidence);h.setNguoiXacMinhId(actor);h.setThoiDiemXacMinh(repository.now());repository.save(h);
        } else {
            var h=new QuaTrinhThuocLop();h.setQuaTrinhHocId(id);h.setLopId(reference);h.setHieuLucTu(from);h.setHieuLucDen(end);
            h.setCanCuXacMinh(evidence);h.setNguoiXacMinhId(actor);h.setThoiDiemXacMinh(repository.now());repository.save(h);
        }
        repository.touch("qua_trinh_theo_hoc",id,version);
        repository.audit(actor,"HISTORY_ADDED",status?"lich_su_trang_thai_sv":"qua_trinh_thuoc_lop",id,evidence);return process(id);
    }
    View roles(long user) {
        var version=repository.version("nguoi_dung",user);
        return view(Map.of("userId",Long.toString(user),"roles",repository.roles(user,true).stream().map(FoundationMapper::role).toList()),version);
    }
    View role(long actor,long user,long role,String tag,boolean grant) {
        var current=roles(user);match(current,tag);var version=repository.version("nguoi_dung",user);
        if(!repository.applicableRole(role))throw new FoundationFailure(409,"ROLE_NOT_APPLICABLE");
        var active=repository.roles(user,false).stream().filter(p -> Objects.equals(p.getVaiTroId(),role)).toList();
        if(grant==!active.isEmpty())return current;
        if(grant) {
            var p=new PhanQuyenNguoiDung();p.setNguoiDungId(user);p.setVaiTroId(role);p.setMaPhanHe("MGHP");p.setNguoiCapQuyenId(actor);p.setThoiDiemTao(repository.now());repository.save(p);
        } else { var now=repository.now();active.forEach(p -> p.setHieuLucDen(now));repository.flush(); }
        repository.touch("nguoi_dung",user,version);repository.audit(actor,grant?"MGHP_ROLE_GRANTED":"MGHP_ROLE_REVOKED","nguoi_dung",user,null);return roles(user);
    }
    static String text(JsonNode b,String key,int max,boolean nullable) {
        var n=b.get(key);if(nullable&&n!=null&&n.isNull())return null;
        if(n==null||!n.isTextual()||n.asText().isBlank()||n.asText().length()>max)throw FoundationFailure.invalid(key);return n.asText();
    }
    static Long optionalId(JsonNode b,String key) { return b.get(key).isNull()?null:FoundationRules.id(text(b,key,19,false)); }
    static LocalDate date(JsonNode b,String key,boolean nullable) {
        String s=text(b,key,10,nullable);if(s==null)return null;
        try{if(!s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw FoundationFailure.invalid(key);return LocalDate.parse(s);}catch(java.time.DateTimeException e){throw FoundationFailure.invalid(key);}
    }
}
