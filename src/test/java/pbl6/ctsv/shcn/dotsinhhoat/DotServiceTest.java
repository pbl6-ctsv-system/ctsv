package pbl6.ctsv.shcn.dotsinhhoat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.entity.DotSinhHoat;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
import pbl6.ctsv.shcn.dotsinhhoat.reference.DotReference;
import pbl6.ctsv.shcn.dotsinhhoat.repository.DotRepository;
import pbl6.ctsv.shcn.dotsinhhoat.service.DotService;
import pbl6.ctsv.shcn.dotsinhhoat.service.DotService.Action;

class DotServiceTest {
 final FoundationAccess access=mock(FoundationAccess.class);
 final DotReference reference=mock(DotReference.class);
 final DotRepository repository=mock(DotRepository.class);
 final AuthAccess.Principal principal=new AuthAccess.Principal("ctsv",UUID.randomUUID(),1);
 final FoundationAccess.Identity identity=new FoundationAccess.Identity(31L,null,principal);
 final Clock clock=Clock.fixed(Instant.parse("2026-11-21T00:00:00Z"),ZoneOffset.UTC);
 final byte[] token={0,0,0,0,0,0,0,1};
 final CauHinh input=new CauHinh("0010","Synthetic round",LocalDate.of(2026,12,1),LocalDate.of(2026,12,7),List.of(new KhoangTiet(5,6)),OffsetDateTime.parse("2026-12-02T00:00:00Z"));
 final LocalTx manager=new LocalTx();
 final DotService service=new DotService(manager,access,reference,repository,clock);
 @BeforeEach void setup(){when(access.identify(principal,true)).thenReturn(identity);when(reference.permissions(identity)).thenReturn(new Quyen(true,true,true));when(repository.ranges("[]")).thenReturn(input.khungTietChoPhep());}
 DotSinhHoat root(){var d=DotSinhHoat.nhap(input,"[]","OTHER-CREATOR",OffsetDateTime.now(clock));ReflectionTestUtils.setField(d,"id",7L);ReflectionTestUtils.setField(d,"phienBanGhi",token);return d;}
 DotFailure fails(String code,Runnable fn){var e=assertThrows(DotFailure.class,fn::run);assertEquals(code,e.code());return e;}
 @Test void checksOnlineIdentityAndPermissionsWithinTransaction(){var result=service.execute(principal,Action.READ,a->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());return 1;});assertEquals(1,result.value());assertTrue(result.canPrepare());verify(access).identify(principal,true);assertEquals(1,manager.commits);}
 @Test void readOnlyGrantCannotWrite(){when(reference.permissions(identity)).thenReturn(new Quyen(true,false,true));assertFalse(service.execute(principal,Action.READ,a->1).canPrepare());fails("FORBIDDEN",()->service.execute(principal,Action.PREPARE,a->1));verifyNoInteractions(repository);}
 @Test void prepareIncludesRead(){when(reference.permissions(identity)).thenReturn(new Quyen(false,true,false));assertEquals(2,service.execute(principal,Action.READ,a->2).value());}
 @Test void referenceIsSeparate(){when(reference.permissions(identity)).thenReturn(new Quyen(true,true,false));fails("FORBIDDEN",()->service.execute(principal,Action.REFERENCE,a->1));}
 @Test void revocationTakesEffectOnNextRequest(){service.execute(principal,Action.READ,a->1);when(reference.permissions(identity)).thenReturn(new Quyen(false,false,false));fails("FORBIDDEN",()->service.execute(principal,Action.READ,a->1));}
 @Test void createUsesAccountIdAndWritesOnlyRootAndAudit(){var d=root();when(repository.create(eq(input),eq(principal.accountId().toString()),any())).thenReturn(d);var result=service.execute(principal,Action.PREPARE,a->service.create(a,input));assertEquals("NHAP",result.value().trangThai());assertEquals("CHUA_DONG_BO",result.value().trangThaiPhanCong());assertNull(result.value().phienBanPhanCongApDung());verify(reference).requireSemester("0010");verify(repository).audit(31L,"SHCN_DOT_CREATE_DRAFT",7L);verify(repository).create(eq(input),eq(principal.accountId().toString()),any());verify(repository).ranges("[]");verifyNoMoreInteractions(repository);}
 @Test void sourceFailureCannotWrite(){doThrow(DotFailure.unavailable()).when(reference).requireSemester(any());fails("DEPENDENCY_UNAVAILABLE",()->service.execute(principal,Action.PREPARE,a->service.create(a,input)));verifyNoInteractions(repository);assertEquals(1,manager.rollbacks);}
 @Test void historicalDetailDoesNotRevalidateSource(){when(repository.find(7,false)).thenReturn(root());assertEquals("0010",service.execute(principal,Action.READ,a->service.detail(7)).value().maHocKy());verify(reference,never()).requireSemester(any());}
 @Test void anotherCreatorCanUpdateAndUnchangedPayloadStillConsumesCas(){var d=root();when(repository.find(7,true)).thenReturn(d);when(repository.update(eq(7L),eq(token),eq(input),eq(false),any())).thenReturn(d);var result=service.execute(principal,Action.PREPARE,a->service.update(a,7,new SuaDot(token,input)));assertEquals("7",result.value().id());verify(repository).update(eq(7L),eq(token),eq(input),eq(false),any());verify(repository).audit(31L,"SHCN_DOT_UPDATE_DRAFT",7);}
 @Test void staleTokenPrecedesClosedState(){var d=root();ReflectionTestUtils.setField(d,"trangThai","DA_PHAT_HANH");when(repository.find(7,true)).thenReturn(d);fails("VERSION_CONFLICT",()->service.execute(principal,Action.PREPARE,a->service.update(a,7,new SuaDot(new byte[8],input))));verify(reference,never()).requireSemester(any());verify(repository,never()).update(anyLong(),any(),any(),anyBoolean(),any());}
 @Test void closedRootStateRejectsCurrentToken(){var d=root();ReflectionTestUtils.setField(d,"trangThai","CHO_DUYET");when(repository.find(7,true)).thenReturn(d);fails("STATE_CONFLICT",()->service.execute(principal,Action.PREPARE,a->service.update(a,7,new SuaDot(token,input))));}
 @Test void activeAssignmentBlocksDraftRoot(){var d=root();ReflectionTestUtils.setField(d,"trangThaiPhanCong","CO_HIEU_LUC");fails("STATE_CONFLICT",()->DotService.writable(d));}
 @Test void unknownSharedWorkflowFailsClosed(){var d=root();ReflectionTestUtils.setField(d,"maHoSoDuyetDot","external-pending");fails("STATE_CONFLICT",()->DotService.writable(d));}
 @Test void changingCalendarInvalidatesDraftAssignmentPointer(){var d=root();when(repository.find(7,true)).thenReturn(d);var c=new CauHinh(input.maHocKy(),input.tenDot(),input.khungNgayBatDau().plusDays(1),input.khungNgayKetThuc(),input.khungTietChoPhep(),input.hanChotDangKyLich());when(repository.update(eq(7L),eq(token),eq(c),eq(true),any())).thenReturn(d);service.execute(principal,Action.PREPARE,a->service.update(a,7,new SuaDot(token,c)));verify(repository).update(eq(7L),eq(token),eq(c),eq(true),any());}
 @Test void auditFailureRollsBackOperation(){when(repository.create(any(),any(),any())).thenReturn(root());doThrow(new IllegalStateException("audit unavailable")).when(repository).audit(anyLong(),any(),anyLong());assertThrows(IllegalStateException.class,()->service.execute(principal,Action.PREPARE,a->service.create(a,input)));assertEquals(0,manager.commits);assertEquals(1,manager.rollbacks);}
 @Test void dtoWhitelistNeverExposesCreatorOrWorkflowHandles(){var d=root();var dto=service.dto(d);assertEquals("AAAAAAAAAAE=",dto.version());assertEquals(input.khungTietChoPhep(),dto.khungTietChoPhep());assertFalse(Arrays.stream(dto.getClass().getRecordComponents()).anyMatch(c->c.getName().contains("Nguoi")||c.getName().contains("HoSo")));}
 static class LocalTx extends AbstractPlatformTransactionManager {
  int commits,rollbacks;
  protected Object doGetTransaction(){return new Object();}
  protected void doBegin(Object t,TransactionDefinition d){assertEquals(TransactionDefinition.ISOLATION_SERIALIZABLE,d.getIsolationLevel());}
  protected void doCommit(DefaultTransactionStatus s){commits++;}
  protected void doRollback(DefaultTransactionStatus s){rollbacks++;}
 }
}
