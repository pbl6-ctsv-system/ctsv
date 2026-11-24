package pbl6.ctsv.shcn.dotsinhhoat.reference;

import java.time.LocalDate;
import java.util.*;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
import pbl6.ctsv.shcn.dotsinhhoat.reference.SemesterAudienceResolver.*;

/** A read projection of the saved configuration, not a publication/recipient snapshot. */
public final class AudiencePreviewResolver {
    private AudiencePreviewResolver(){}
    private record Config(LocalDate from,LocalDate until,List<KhoangTiet> periods,String mode){}
    private static final class Applied {
        final Member member;final Config config;long count,unverified;
        final Set<BoChonNguon> selectors=new LinkedHashSet<>();
        Applied(Member member,Config config){this.member=member;this.config=config;}
    }
    public static DoiTuongDTO resolve(String id,String version,CauHinh input,Projection projection,Query q){
        var selectors=input.doiTuongApDung()==null?List.<DoiTuong>of():input.doiTuongApDung();
        Set<String> valid=new HashSet<>();projection.groups().khoaHoc().forEach(k->valid.add("KHOA_HOC:"+k.ma()));
        projection.groups().lopTreTienDo().forEach(k->valid.add("LOP:"+k.ma()));
        Map<String,List<DoiTuong>> selected=new HashMap<>();
        for(var g:selectors){
            String key=g.loaiDoiTuong()+":"+g.maDoiTuong();
            if(!valid.contains(key))throw new DotFailure(409,"SOURCE_MAPPING_INCOMPLETE");
            selected.computeIfAbsent(key,k->new ArrayList<>()).add(g);
        }
        Map<Long,Applied> classes=new TreeMap<>();
        for(var row:projection.members()){
            var m=row.member();String key="TRE_TIEN_DO".equals(m.kind())?"LOP:"+m.classCode():"KHOA_HOC:"+m.cohortCode();
            var targets=selected.getOrDefault(key,List.of());if(targets.isEmpty())continue;
            for(var g:targets){
                var config=new Config(g.khungNgayBatDau()==null?input.khungNgayBatDau():g.khungNgayBatDau(),g.khungNgayKetThuc()==null?input.khungNgayKetThuc():g.khungNgayKetThuc(),g.khungTietChoPhep()==null?input.khungTietChoPhep():g.khungTietChoPhep(),g.hinhThucChoPhep());
                var existing=classes.computeIfAbsent(m.classId(),n->new Applied(m,config));
                if(!existing.config.equals(config))throw new DotFailure(409,"TARGET_CONFIG_CONFLICT");
                existing.selectors.add(new BoChonNguon(g.loaiDoiTuong(),g.maDoiTuong()));
            }
            var a=classes.get(m.classId());a.count++;if(!"DU_CAN_CU_NGUON".equals(row.verification()))a.unverified++;
        }
        long ordinary=classes.values().stream().filter(a->"THUONG".equals(a.member.kind())).count();
        var total=new TongHopDoiTuong(selectors.size(),classes.size(),ordinary,classes.size()-ordinary);
        var items=new ArrayList<LopApDungDTO>();
        for(var a:classes.values()){
            var m=a.member;String search=q.q().toLowerCase(Locale.ROOT);
            if(!search.isEmpty()&&!m.classCode().toLowerCase(Locale.ROOT).contains(search)&&(m.className()==null||!m.className().toLowerCase(Locale.ROOT).contains(search)))continue;
            items.add(new LopApDungDTO(m.classCode(),m.className(),m.kind(),m.cohortYear()==null?null:"Khóa "+m.cohortYear(),m.facultyCode(),m.facultyName(),m.programCode(),m.programName(),a.config.from(),a.config.until(),a.config.periods(),a.config.mode(),List.copyOf(a.selectors),a.count,a.unverified));
        }
        if(q.descending())Collections.reverse(items);
        int start=Math.min(q.offset(),items.size()),end=(int)Math.min((long)items.size(),(long)start+q.size());
        return new DoiTuongDTO(id,version,List.copyOf(selectors),total,new Page<>(List.copyOf(items.subList(start,end)),q.page(),q.size(),items.size()),projection.groups().trangThaiPhanNhom(),projection.groups().canhBao());
    }
}
