package pbl6.ctsv.shcn.dotsinhhoat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import pbl6.ctsv.shcn.dotsinhhoat.dto.MauDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.Quyen;
import pbl6.ctsv.shcn.dotsinhhoat.entity.*;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
import pbl6.ctsv.shcn.dotsinhhoat.repository.MauRepository;
import pbl6.ctsv.shcn.dotsinhhoat.service.*;
import pbl6.ctsv.shcn.dotsinhhoat.validation.*;
class MauServiceTest {
 final DotServiceTest f=new DotServiceTest();final MauRepository repo=mock(MauRepository.class);final MauRequests parser=new MauRequests();final MauService service=new MauService(f.repository,repo,parser,f.clock);
 final Input input=new Input(f.token,"Biên bản SHCN",TemplateDictionary.defaults());final MauBienBan template=mock(MauBienBan.class);DotSinhHoat root;
 @BeforeEach void setup(){f.setup();root=f.root();when(f.repository.find(7,false)).thenReturn(root);when(f.repository.find(7,true)).thenReturn(root);when(template.id()).thenReturn(19L);when(template.name()).thenReturn(input.tenMau());when(template.state()).thenReturn("HOP_LE");when(repo.sections(19)).thenReturn(input.muc());}
 MauDTO get(){return f.service.execute(f.principal,DotService.Action.READ,a->service.read(7,a)).value();}
 MauDTO put(){return f.service.execute(f.principal,DotService.Action.PREPARE,a->service.save(7,input,a)).value();}
 @Test void emptyGetIsReadOnlyAndDoesNotNeedReference(){var out=get();assertFalse(out.daCoMau());assertNull(out.id());assertTrue(out.muc().isEmpty());assertTrue(out.duocSua());verify(repo,never()).save(anyLong(),any(),any(),any());verify(f.repository,never()).audit(anyLong(),any(),anyLong());verify(f.reference,never()).requireSemester(any());}
 @Test void readOnlyUserCanReadButCannotPut(){when(f.reference.permissions(f.identity)).thenReturn(new Quyen(true,false,false));assertFalse(get().duocSua());assertEquals("FORBIDDEN",assertThrows(DotFailure.class,this::put).code());verify(repo,never()).save(anyLong(),any(),any(),any());}
 @Test void lockedRoundIsReadableAndNeverWritable(){ReflectionTestUtils.setField(root,"trangThai","DA_PHAT_HANH");when(repo.find(7)).thenReturn(template);assertEquals("19",get().id());assertFalse(get().duocSua());assertEquals("STATE_CONFLICT",assertThrows(DotFailure.class,this::put).code());verify(repo,never()).compareAndTouch(anyLong(),any(),any());}
 @Test void lockedTemplateIsReadableButCannotSave(){when(repo.find(7)).thenReturn(template);when(template.state()).thenReturn("DA_KHOA");assertFalse(get().duocSua());assertEquals("STATE_CONFLICT",assertThrows(DotFailure.class,this::put).code());}
 @Test void staleRootFailsBeforeAnyTemplateWrite(){ReflectionTestUtils.setField(root,"phienBanGhi",new byte[]{0,0,0,0,0,0,0,2});assertEquals("VERSION_CONFLICT",assertThrows(DotFailure.class,this::put).code());verifyNoInteractions(repo);assertEquals(1,f.manager.rollbacks);}
 @Test void firstSaveLocksRootCasChildrenAuditThenReturnsNewVersion(){when(repo.find(7)).thenReturn(null,template);doAnswer(i->{ReflectionTestUtils.setField(root,"phienBanGhi",new byte[]{0,0,0,0,0,0,0,2});return null;}).when(repo).compareAndTouch(eq(7L),any(),any());var out=put();assertEquals("AAAAAAAAAAI=",out.dotVersion());assertEquals("19",out.id());var order=inOrder(f.repository,repo);order.verify(f.repository).find(7,true);order.verify(repo).find(7);order.verify(repo).compareAndTouch(eq(7L),eq(f.token),any());order.verify(repo).save(eq(7L),eq(input),eq(f.principal.accountId().toString()),any());order.verify(f.repository).audit(31L,"SHCN_TEMPLATE_CREATE",7L);assertEquals(1,f.manager.commits);verify(f.repository,never()).replaceAudiences(anyLong(),any(),any());}
 @Test void updateKeepsIdAndAuditType(){when(repo.find(7)).thenReturn(template);assertEquals("19",put().id());verify(f.repository).audit(31L,"SHCN_TEMPLATE_UPDATE",7L);}
 @Test void childFailureRollsBackAndNeverAuditsSuccess(){doThrow(new IllegalStateException("fixture child failure")).when(repo).save(anyLong(),any(),any(),any());assertThrows(IllegalStateException.class,this::put);assertEquals(1,f.manager.rollbacks);assertEquals(0,f.manager.commits);verify(f.repository,never()).audit(anyLong(),any(),anyLong());}
 @Test void casFailureDoesNotWriteChildren(){doThrow(new DotFailure(409,"VERSION_CONFLICT")).when(repo).compareAndTouch(anyLong(),any(),any());assertThrows(DotFailure.class,this::put);verify(repo,never()).save(anyLong(),any(),any(),any());}
 @Test void permissionRevokeStopsAllTemplateReads(){when(f.reference.permissions(f.identity)).thenReturn(new Quyen(false,false,false));assertThrows(DotFailure.class,this::get);verifyNoInteractions(repo);}
 @Test void malformedStoredTemplateIsNotReturnedAsEmptySuccess(){when(repo.find(7)).thenReturn(template);when(repo.sections(19)).thenReturn(List.of());assertThrows(IllegalStateException.class,this::get);}
}
