package pbl6.ctsv.shcn.dotsinhhoat.reference;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
import pbl6.ctsv.shcn.dotsinhhoat.reference.SemesterAudienceResolver.*;
class AudiencePreviewResolverTest {
 final LocalDate from=LocalDate.of(2026,12,1),until=LocalDate.of(2026,12,7);
 Member m(long id,long classId,String kind){return new Member(id,"SV"+id,"Synthetic",classId,"L"+classId,"Class "+classId,1L,"K2024","2024",kind,"0","0","0");}
 DoiTuong g(String type,String code,String mode){return new DoiTuong(type,code,mode,from,until,null);}
 DoiTuongDTO preview(Projection p,List<DoiTuong> groups,Query q){return AudiencePreviewResolver.resolve("7","opaque",new CauHinh("0010","Synthetic",from,until,List.of(new KhoangTiet(11,12)),OffsetDateTime.parse("2026-11-28T00:00:00Z"),groups),p,q);}
 Query query(int page,int size,String q){return new Query(page,size,page*size,false,q,null,null,null);}
 @Test void countsRemainGlobalWhileClassSearchAndPaginationAreAppliedAfterGrouping(){
  var p=SemesterAudienceResolver.resolve(List.of(m(1,10,"THUONG"),m(2,10,"THUONG"),m(3,11,"THUONG")),List.of());
  var out=preview(p,List.of(g("KHOA_HOC","K2024","OFFLINE")),query(0,1,"Class 10"));
  assertEquals(2,out.tongHop().tongSoLop());assertEquals(1,out.lopApDung().totalElements());assertEquals(2,out.lopApDung().items().getFirst().soSinhVien());assertEquals(2,out.lopApDung().items().getFirst().soSinhVienCanXacMinh());
 }
 @Test void legacyDateInheritanceReadsWithoutMutatingStoredSelector(){
  var group=new DoiTuong("KHOA_HOC","K2024","OFFLINE",null,null,null);var p=SemesterAudienceResolver.resolve(List.of(m(1,10,"THUONG")),List.of());
  var out=preview(p,List.of(group),query(0,25,""));assertEquals(from,out.lopApDung().items().getFirst().tuNgay());assertNull(out.items().getFirst().khungNgayBatDau());
 }
 @Test void missingSavedSelectorCannotSilentlyBecomeAValidEmptyPreview(){
  var e=assertThrows(DotFailure.class,()->preview(SemesterAudienceResolver.resolve(List.of(),List.of()),List.of(g("KHOA_HOC","K2024","OFFLINE")),query(0,25,"")));
  assertEquals("SOURCE_MAPPING_INCOMPLETE",e.code());
 }
 @Test void conflictingConfigurationsAreNotChosenArbitrarily(){
  var p=SemesterAudienceResolver.resolve(List.of(m(1,10,"THUONG")),List.of());var e=assertThrows(DotFailure.class,()->preview(p,List.of(g("KHOA_HOC","K2024","OFFLINE"),g("KHOA_HOC","K2024","ONLINE")),query(0,25,"")));
  assertEquals("TARGET_CONFIG_CONFLICT",e.code());
 }
 @Test void regularAndReceivingClassesUseDisjointMemberSets(){
  var p=SemesterAudienceResolver.resolve(List.of(m(1,10,"THUONG"),m(2,10,"THUONG")),List.of(m(1,30,"TRE_TIEN_DO")));
  var out=preview(p,List.of(g("KHOA_HOC","K2024","OFFLINE"),g("LOP","L30","ONLINE")),query(0,25,""));
  assertEquals(1,out.tongHop().soLopThuong());assertEquals(1,out.tongHop().soLopTreTienDo());assertEquals(1,out.lopApDung().items().getFirst().soSinhVien());assertEquals(1,out.lopApDung().items().getLast().soSinhVien());
 }
 @Test void facultyAndProgramMetadataAreProjectedFromReadOnlySourceFields(){
  var member=new Member(1,"SV1","Synthetic",10,"L10","Class 10",1L,"K2024","2024","THUONG","0","0","0",1L,"F1","Faculty","P1","Program");
  var out=preview(SemesterAudienceResolver.resolve(List.of(member),List.of()),List.of(g("KHOA_HOC","K2024","OFFLINE")),query(0,25,""));
  assertEquals("Faculty",out.lopApDung().items().getFirst().tenKhoa());assertEquals("Program",out.lopApDung().items().getFirst().tenChuongTrinh());
 }
 @Test void differentProfileCohortProducesWarningWithoutOverwritingClassCohort(){
  var member=new Member(1,"SV1","Synthetic",10,"L10","Class 10",1L,"K2024","2024","THUONG","0","0","0",2L,null,null,null,null);
  var p=SemesterAudienceResolver.resolve(List.of(member),List.of());assertEquals("K2024",p.groups().khoaHoc().getFirst().ma());assertTrue(p.groups().canhBao().stream().anyMatch(w->w.ma().equals("KHOA_SV_KHAC_KHOA_LOP")));
 }
}
