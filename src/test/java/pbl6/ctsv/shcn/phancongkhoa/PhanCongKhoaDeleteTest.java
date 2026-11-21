package pbl6.ctsv.shcn.phancongkhoa;
import java.util.*;
import java.time.Clock;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shcn.foundation.PhanCongKhoaFoundationPort;
import pbl6.ctsv.shcn.foundation.PhanCongKhoaFoundationPort.*;
import pbl6.ctsv.shcn.phancongkhoa.entity.DanhSachPhanCongKhoa;
import pbl6.ctsv.shcn.phancongkhoa.repository.*;
import pbl6.ctsv.shcn.phancongkhoa.service.PhanCongKhoaService;
import pbl6.ctsv.shcn.phancongkhoa.validation.PhanCongKhoaRequests;
import pbl6.ctsv.shcn.phancongkhoa.exception.PhanCongKhoaFailure;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PhanCongKhoaDeleteTest {
 final byte[] token=new byte[8];
 final PhanCongKhoaRepository repo=mock(PhanCongKhoaRepository.class);
 final PhanCongKhoaAudit audit=mock(PhanCongKhoaAudit.class);
 final PhanCongKhoaFoundationPort ref=mock(PhanCongKhoaFoundationPort.class);
 final FoundationAccess ids=mock(FoundationAccess.class);
 final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
 PhanCongKhoaService service;
 DanhSachPhanCongKhoa root;
 Actor actor(boolean write,String khoa){return new Actor(1,"synthetic",true,write,List.of(new Khoa(1,khoa,"Synthetic",true,write,true)));}
 @BeforeEach void setup(){
  when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
  service=new PhanCongKhoaService(manager,ids,ref,repo,audit,Clock.systemUTC());
  root=mock(DanhSachPhanCongKhoa.class);when(root.getId()).thenReturn(10L);when(root.getMaKhoa()).thenReturn("A");when(root.getPhienBanGhi()).thenReturn(token);when(root.getTrangThai()).thenReturn("NHAP");when(repo.khoaDanhSach(10)).thenReturn(Optional.of(root));
 }
 @Test void correctScopeVersionAndDraftDeleteWithAudit(){service.xoa(actor(true,"A"),10,token);verify(repo).xoaNhap(10,token);verify(audit).ghi(1,"SHCN_KHOA_DELETE_DRAFT",10,"Xóa bản nháp");}
 @Test void readOnlyCapabilityCannotDelete(){when(ref.quyen(any())).thenReturn(actor(false,"A"));assertThatThrownBy(()->service.thucHien(null,true,a->{service.xoa(a,10,token);return null;})).isInstanceOfSatisfying(PhanCongKhoaFailure.class,e->assertThat(e.status()).isEqualTo(403));verify(repo,never()).xoaNhap(anyLong(),any());}
 @Test void otherFacultyCannotDelete(){assertThatThrownBy(()->service.xoa(actor(true,"B"),10,token)).hasMessage("NOT_FOUND");verify(repo,never()).xoaNhap(anyLong(),any());}
 @Test void staleVersionCannotDelete(){byte[] stale=token.clone();stale[0]=1;assertThatThrownBy(()->service.xoa(actor(true,"A"),10,stale)).hasMessage("VERSION_CONFLICT");verify(repo,never()).xoaNhap(anyLong(),any());}
 @Test void closedOrDeletedRecordCannotDelete(){when(root.getTrangThai()).thenReturn("DA_KY");assertThatThrownBy(()->service.xoa(actor(true,"A"),10,token)).hasMessage("SHCN_KHONG_CON_LA_NHAP");when(root.getTrangThai()).thenReturn("DA_HUY");assertThatThrownBy(()->service.xoa(actor(true,"A"),10,token)).hasMessage("NOT_FOUND");verify(repo,never()).xoaNhap(anyLong(),any());}
 @Test void deleteParserRequiresOnlyCanonicalVersion()throws Exception{
  var json=new ObjectMapper();var parser=new PhanCongKhoaRequests(json);var body=json.createObjectNode().put("expectedVersion",Base64.getEncoder().encodeToString(token));assertThat(parser.xoa(body)).containsExactly(token);
  assertThatThrownBy(()->parser.xoa(body.deepCopy().put("trangThai","NHAP"))).isInstanceOf(PhanCongKhoaFailure.class);
  assertThatThrownBy(()->parser.xoa(json.createObjectNode())).isInstanceOf(PhanCongKhoaFailure.class);
  assertThatThrownBy(()->parser.xoa(body.put("expectedVersion","wrong"))).isInstanceOf(PhanCongKhoaFailure.class);
 }
}
