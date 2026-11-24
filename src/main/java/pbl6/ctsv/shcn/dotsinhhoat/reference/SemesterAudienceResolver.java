package pbl6.ctsv.shcn.dotsinhhoat.reference;

import java.util.*;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;

/** Pure projection of source evidence. No writes, inferred years or period-code conversion. */
public final class SemesterAudienceResolver {
 public record Member(long studentId,String studentCode,String studentName,long classId,String classCode,String className,Long cohortId,String cohortCode,String cohortYear,String kind,String withdrawn,String paused,String suspended,Long profileCohortId,String facultyCode,String facultyName,String programCode,String programName){
  public Member(long id,String code,String name,long classId,String classCode,String className,Long cohortId,String cohortCode,String cohortYear,String kind,String withdrawn,String paused,String suspended){
   this(id,code,name,classId,classCode,className,cohortId,cohortCode,cohortYear,kind,withdrawn,paused,suspended,null,null,null,null,null);
  }
 }
 public record Resolved(Member member,String verification){}
 public record Projection(NhomLopDTO groups,List<Resolved> members){public Projection{members=List.copyOf(members);}}
 private static boolean yes(String value){return value!=null&&Set.of("1","true").contains(value.strip().toLowerCase(Locale.ROOT));}
 private static boolean no(String value){return value!=null&&Set.of("0","false").contains(value.strip().toLowerCase(Locale.ROOT));}
 private static boolean stopped(Member m){return yes(m.withdrawn())||yes(m.paused())||yes(m.suspended());}
 private static boolean verified(Member m){return no(m.withdrawn())&&no(m.paused())&&no(m.suspended());}
 private static boolean text(String v){return v!=null&&!v.isBlank();}
 private static DotFailure mapping(){return new DotFailure(409,"SOURCE_MAPPING_INCOMPLETE");}
 public static Projection resolve(List<Member> academic,List<Member> delayed){
  Map<Long,List<Member>> status=new LinkedHashMap<>();for(var m:academic){if(m.studentId()<1||!text(m.studentCode()))throw mapping();status.computeIfAbsent(m.studentId(),k->new ArrayList<>()).add(m);}
  Map<Long,Member> receiving=new LinkedHashMap<>();
  for(var m:delayed){if(m.studentId()<1||m.classId()<1||!text(m.studentCode())||!text(m.classCode()))throw mapping();var prior=receiving.putIfAbsent(m.studentId(),m);if(prior!=null&&prior.classId()!=m.classId())throw mapping();}
  List<Resolved> people=new ArrayList<>();
  for(var entry:status.entrySet()){
   if(receiving.containsKey(entry.getKey())||entry.getValue().stream().anyMatch(SemesterAudienceResolver::stopped))continue;
   var m=entry.getValue().getFirst();if(m.studentId()<1||m.classId()<1||!text(m.studentCode())||!text(m.classCode()))throw mapping();
   if(entry.getValue().stream().anyMatch(v->v.classId()!=m.classId()))throw mapping();
   // A profile currently pointing at a RenLuyen class is not a period roster.
   if(m.className()!=null&&m.className().toLowerCase(Locale.ROOT).contains("renluyen"))continue;
   // The current projection has no evidence of complete semester roster coverage.
   people.add(new Resolved(m,"CAN_XAC_MINH"));
  }
  for(var m:receiving.values()){
   var evidence=status.getOrDefault(m.studentId(),List.of());
   boolean complete=!evidence.isEmpty()&&evidence.stream().allMatch(SemesterAudienceResolver::verified);
   people.add(new Resolved(m,complete?"DU_CAN_CU_NGUON":"CAN_XAC_MINH"));
  }
  people.sort(Comparator.comparingLong((Resolved r)->r.member().classId()).thenComparingLong(r->r.member().studentId()));
  Map<String,Long> studentCodes=new HashMap<>(),classCodes=new HashMap<>(),cohortCodes=new HashMap<>();
  Map<Long,Member> classes=new LinkedHashMap<>();Map<Long,Set<Long>> cohortClasses=new LinkedHashMap<>();Map<Long,Member> cohortLabels=new HashMap<>();Map<Long,Long> renCounts=new LinkedHashMap<>();Set<Long> missing=new HashSet<>();
  for(var r:people){var m=r.member();unique(studentCodes,m.studentCode(),m.studentId());unique(classCodes,m.classCode(),m.classId());classes.putIfAbsent(m.classId(),m);
   if("TRE_TIEN_DO".equals(m.kind()))renCounts.merge(m.classId(),1L,Long::sum);
   else if(m.cohortId()==null||!text(m.cohortCode()))missing.add(m.classId());
   else{unique(cohortCodes,m.cohortCode(),m.cohortId());cohortClasses.computeIfAbsent(m.cohortId(),k->new HashSet<>()).add(m.classId());cohortLabels.put(m.cohortId(),m);}
  }
  var cohorts=cohortClasses.entrySet().stream().map(e->{var m=cohortLabels.get(e.getKey());return new KhoaHocDTO(m.cohortCode(),"Khóa "+(text(m.cohortYear())?m.cohortYear():m.cohortCode()),e.getValue().size());}).sorted(Comparator.comparing(KhoaHocDTO::ma)).toList();
  var ren=renCounts.entrySet().stream().map(e->{var m=classes.get(e.getKey());return new LopTreTienDoDTO(m.classCode(),m.className(),e.getValue());}).sorted(Comparator.comparing(LopTreTienDoDTO::ma)).toList();
  if(cohorts.size()>1000||ren.size()>1000)throw DotFailure.unavailable();
  List<CanhBao> warnings=new ArrayList<>();
  warnings.add(new CanhBao("CHUA_XAC_MINH_DO_PHU_PHAN_LOP","Danh sách phân lớp của kỳ này chưa được xác minh đầy đủ. Các SV đã có lớp rèn luyện được tách khỏi nhóm khóa."));
  if(academic.isEmpty())warnings.add(new CanhBao("CHUA_CO_HOC_VU_DUNG_KY","Chưa có dữ liệu học vụ cho học kỳ này; chưa đủ căn cứ xác định các khóa tham gia."));
  if(ren.isEmpty())warnings.add(new CanhBao("CHUA_CO_PHAN_LOP_DUNG_KY","Chưa có danh sách lớp rèn luyện nhận sinh viên đúng kỳ. Không dùng danh sách kỳ trước thay thế."));
  if(!missing.isEmpty())warnings.add(new CanhBao("CHUA_CO_MAPPING_KHOA","Có lớp chưa được liên kết với khóa; chưa đưa vào lựa chọn theo khóa."));
  if(people.stream().anyMatch(r->{var m=r.member();return m.profileCohortId()!=null&&m.cohortId()!=null&&!m.profileCohortId().equals(m.cohortId())&&"THUONG".equals(m.kind());}))
   warnings.add(new CanhBao("KHOA_SV_KHAC_KHOA_LOP","Có thông tin khóa trên hồ sơ SV khác khóa của lớp; cần đối chiếu dữ liệu nguồn."));
  return new Projection(new NhomLopDTO(cohorts,ren,missing.size(),ren.isEmpty()?"CHUA_CO_DU_LIEU":"CO_DU_LIEU",academic.isEmpty()?"CHUA_CO_DU_LIEU":"CO_DU_LIEU","CAN_XAC_MINH",warnings),people);
 }
 private static void unique(Map<String,Long> index,String code,long id){var old=index.putIfAbsent(code,id);if(old!=null&&old!=id)throw mapping();}
    public static Page<LopTheoKyDTO> classes(Projection projection,String type,String selector,Query q){
        boolean exists="KHOA_HOC".equals(type)?projection.groups().khoaHoc().stream().anyMatch(k->k.ma().equals(selector)):"LOP".equals(type)&&projection.groups().lopTreTienDo().stream().anyMatch(l->l.ma().equals(selector));
        if(!exists)throw DotFailure.invalid("maDoiTuong");
        Map<Long,List<SemesterAudienceResolver.Resolved>> classes=new TreeMap<>();
        for(var row:projection.members()){
            var m=row.member();boolean selected="KHOA_HOC".equals(type)?"THUONG".equals(m.kind())&&Objects.equals(m.cohortCode(),selector):"TRE_TIEN_DO".equals(m.kind())&&m.classCode().equals(selector);
            if(selected&&matches(q.q(),m.classCode(),m.className()))classes.computeIfAbsent(m.classId(),k->new ArrayList<>()).add(row);
        }
        var values=new ArrayList<LopTheoKyDTO>();for(var rows:classes.values()){var m=rows.getFirst().member();values.add(new LopTheoKyDTO(m.classCode(),m.className(),m.kind(),m.cohortCode(),m.cohortYear()==null?null:"Khóa "+m.cohortYear(),rows.size(),rows.stream().filter(r->!"DU_CAN_CU_NGUON".equals(r.verification())).count()));}
        if(q.descending())Collections.reverse(values);return page(values,q);
    }
    public static Page<SinhVienTheoKyDTO> students(Projection projection,String classroom,Query q){
        var rows=projection.members().stream().filter(r->r.member().classCode().equals(classroom)).sorted(Comparator.comparingLong(r->r.member().studentId())).toList();
        if(rows.isEmpty())throw DotFailure.invalid("maLop");
        var values=new ArrayList<SinhVienTheoKyDTO>();for(var row:rows){var m=row.member();if(matches(q.q(),m.studentCode(),m.studentName()))values.add(new SinhVienTheoKyDTO(Long.toString(m.studentId()),m.studentCode(),m.studentName(),m.classCode(),m.className(),m.kind(),"TRE_TIEN_DO".equals(m.kind())?"PHAN_LOP_REN_LUYEN_DUNG_KY":"HOC_VU_DUNG_KY","TRE_TIEN_DO".equals(m.kind())?"LOP_NHAN_DUNG_KY":"LOP_HIEN_TAI_NGUON",row.verification(),"DU_CAN_CU_NGUON".equals(row.verification())));}
        if(q.descending())Collections.reverse(values);return page(values,q);
    }
    private static boolean matches(String query,String...values){return query.isEmpty()||Arrays.stream(values).filter(Objects::nonNull).anyMatch(v->v.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)));}
    private static <T> Page<T> page(List<T> values,Query q){int start=Math.min(q.offset(),values.size()),end=(int)Math.min((long)values.size(),(long)start+q.size());return new Page<>(values.subList(start,end),q.page(),q.size(),values.size());}
}
