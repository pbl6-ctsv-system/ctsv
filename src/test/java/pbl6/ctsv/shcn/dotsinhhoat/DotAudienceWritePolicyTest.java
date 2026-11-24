package pbl6.ctsv.shcn.dotsinhhoat;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
import pbl6.ctsv.shcn.dotsinhhoat.validation.DotRequests;
import pbl6.ctsv.shcn.dotsinhhoat.service.DotService.Action;
class DotAudienceWritePolicyTest {
 final DotServiceTest f=new DotServiceTest();final ObjectMapper json=new ObjectMapper();final DotRequests parser=new DotRequests(json);
 final DoiTuong group=new DoiTuong("KHOA_HOC","2023","OFFLINE",LocalDate.of(2026,12,1),LocalDate.of(2026,12,7),null);
 @BeforeEach void setup(){f.setup();when(f.repository.create(any(),any(),any())).thenReturn(f.root());}
 @Test void createRequiresAtLeastOneAudienceBeforeAnyWrite(){
  assertThrows(DotFailure.class,()->f.service.execute(f.principal,Action.PREPARE,a->f.service.create(a,new CauHinh(f.input.maHocKy(),f.input.tenDot(),f.input.khungNgayBatDau(),f.input.khungNgayKetThuc(),f.input.khungTietChoPhep(),f.input.hanChotDangKyLich()))));
  verify(f.repository,never()).create(any(),any(),any());
 }
 @Test void createChecksEnvelopeAgainstAllSelectedGroups(){
  var c=new CauHinh("0010","Synthetic",LocalDate.of(2026,12,2),LocalDate.of(2026,12,7),f.input.khungTietChoPhep(),f.input.hanChotDangKyLich(),List.of(group));
  assertThrows(DotFailure.class,()->f.service.execute(f.principal,Action.PREPARE,a->f.service.create(a,c)));
  verify(f.repository,never()).create(any(),any(),any());
 }
 @Test void omittedPatchArrayStillRevalidatesSourceBeforeSaving(){
  when(f.repository.find(7,true)).thenReturn(f.root());when(f.repository.audiences(7)).thenReturn(List.of(group));
  when(f.repository.update(eq(7L),any(),any(),anyBoolean(),any())).thenReturn(f.root());
  f.service.execute(f.principal,Action.PREPARE,a->f.service.update(a,7,new SuaDot(f.token,new CauHinh(f.input.maHocKy(),f.input.tenDot(),f.input.khungNgayBatDau(),f.input.khungNgayKetThuc(),f.input.khungTietChoPhep(),f.input.hanChotDangKyLich()))));
  verify(f.reference).requireAudiences("0010",List.of(group));
 }
 @Test void emptyEffectivePatchCannotEraseAudienceConfiguration(){
  when(f.repository.find(7,true)).thenReturn(f.root());when(f.repository.audiences(7)).thenReturn(List.of());
  assertThrows(DotFailure.class,()->f.service.execute(f.principal,Action.PREPARE,a->f.service.update(a,7,new SuaDot(f.token,new CauHinh(f.input.maHocKy(),f.input.tenDot(),f.input.khungNgayBatDau(),f.input.khungNgayKetThuc(),f.input.khungTietChoPhep(),f.input.hanChotDangKyLich())))));
  verify(f.repository,never()).update(anyLong(),any(),any(),anyBoolean(),any());
 }
 @Test void parserRejectsMissingEmptyOrInheritedDatesForNewAudience()throws Exception{
  var root=json.readTree("{\"maHocKy\":\"0010\",\"tenDot\":\"Synthetic\",\"khungNgayBatDau\":\"2026-12-01\",\"khungNgayKetThuc\":\"2026-12-07\",\"khungTietChoPhep\":[{\"tietDau\":11,\"tietCuoi\":12}],\"hanChotDangKyLich\":\"2026-11-29T10:00:00Z\"}");
  assertThrows(DotFailure.class,()->parser.create(root));
  ((com.fasterxml.jackson.databind.node.ObjectNode)root).putArray("doiTuongApDung");assertThrows(DotFailure.class,()->parser.create(root));
  var g=json.createObjectNode().put("loaiDoiTuong","KHOA_HOC").put("maDoiTuong","2023").put("hinhThucChoPhep","OFFLINE");g.putNull("khungNgayBatDau");g.putNull("khungNgayKetThuc");g.putNull("khungTietChoPhep");
  ((com.fasterxml.jackson.databind.node.ArrayNode)root.get("doiTuongApDung")).add(g);assertThrows(DotFailure.class,()->parser.create(root));
 }
}
