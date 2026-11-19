package pbl6.ctsv.hocbong.kkht.round;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pbl6.ctsv.foundation.api.AcademicSemesterSource;
import pbl6.ctsv.hocbong.kkht.round.entity.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Service business rules with mocked public source/repository. Does not prove SQL rollback/races. */
class RoundServiceTest {
    final RoundRepository repo=mock(RoundRepository.class);
    final AcademicSemesterSource source=mock(AcademicSemesterSource.class);
    final ObjectMapper json=new ObjectMapper();
    final Clock clock=Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"),ZoneOffset.UTC);
    final RoundService service=new RoundService(repo,source,json,clock);
    final RoundAccess.Actor actor=new RoundAccess.Actor(301L,null,401L);
    final byte[] version={0,0,0,0,0,0,0,1};
    long nextId=500;

    @BeforeEach void generatedIds() {
        doAnswer(call -> {ReflectionTestUtils.setField(call.getArgument(0),"id",++nextId);return null;})
            .when(repo).persist(any());
    }

    @Test void sourcesAreReadOnlyAndUseCodeYearInsteadOfRawYear() {
        when(source.findPage(null,0,20)).thenReturn(new AcademicSemesterSource.Page(List.of(term(21,"2510")),0,20,1));
        var page=service.sources(null,new int[]{0,20});
        assertThat(page).isNotNull();
        assertThat(page.items()).singleElement().satisfies(row -> {
            assertThat(row.academicYear()).isEqualTo("2025-2026");
            assertThat(row.semesterOrder()).isEqualTo(1); assertThat(row.selectable()).isTrue();
            assertThat(row.scholarshipSemesterId()).isNull();
        });
        verify(repo,never()).persist(any());
    }

    @Test void aFullSourcePageDoesNotIssueOneAliasAndSemesterQueryPerRow() {
        var items=java.util.stream.IntStream.range(0,100)
            .mapToObj(i -> term(i+1,String.format("%02d10",i))).toList();
        when(source.findPage(null,0,100)).thenReturn(new AcademicSemesterSource.Page(items,0,100,100));
        var page=service.sources(null,new int[]{0,100});
        assertThat(page.items()).hasSize(100);
        verify(repo).semestersBySources(anyList());
        verify(repo).semestersByPeriods(anyCollection());
        verify(repo,never()).mappingBySource(anyLong());
        verify(repo,never()).semesterByPeriod(anyString(),anyInt());
    }

    @Test void summerCannotCreateAScholarshipPeriodOrRound() {
        when(source.findById(22)).thenReturn(Optional.of(term(22,"2521")));
        failure(() -> service.create(new RoundDtos.CreateCommand(22,1,"Đợt"),actor),409,"SOURCE_SEMESTER_UNAVAILABLE");
        verify(repo,never()).persist(any()); verify(repo,never()).persistAudit(any());
    }

    @Test void unknownCodeDoesNotCreateAMapping() {
        when(source.findById(24)).thenReturn(Optional.of(term(24,"2530")));
        failure(() -> service.create(new RoundDtos.CreateCommand(24,1,"Đợt"),actor),409,"SEMESTER_MAPPING_REQUIRED");
        verify(repo,never()).persist(any());
    }

    @Test void createInitializesPeriodMappingRoundAndActorAuditTogether() {
        when(source.findById(21)).thenReturn(Optional.of(term(21,"2510")));
        when(repo.lockRound(anyLong())).thenReturn(version);
        // The in-memory persistence fixture returns the created objects on subsequent readback.
        Map<Class<?>,Object> saved=new HashMap<>();
        doAnswer(call -> {
            Object entity=call.getArgument(0);ReflectionTestUtils.setField(entity,"id",++nextId);saved.put(entity.getClass(),entity);return null;
        }).when(repo).persist(any());
        when(repo.round(anyLong())).thenAnswer(call -> saved.get(ScholarshipRound.class));
        when(repo.semester(anyLong())).thenAnswer(call -> saved.get(ScholarshipSemester.class));
        when(repo.mapping(anyLong())).thenAnswer(call -> saved.get(SourceSemesterMapping.class));
        var view=service.create(new RoundDtos.CreateCommand(21,1,"Đợt 1"),actor);
        assertThat(view).isNotNull();
        assertThat(view.body().semester().academicYear()).isEqualTo("2025-2026");
        assertThat(view.body().sourceSemesterId()).isEqualTo("21");
        assertThat(view.body().createdBy()).isEqualTo("301");
        assertThat(view.body().status()).isEqualTo("draft");
        assertThat(view.body().ruleVersionId()).isNull();
        var audits=org.mockito.ArgumentCaptor.forClass(ScholarshipAudit.class);
        verify(repo,times(3)).persistAudit(audits.capture());
        assertThat(audits.getAllValues()).extracting(ScholarshipAudit::getAction)
            .containsExactly("semester.initialized","semester.source-linked","round.created");
        assertThat(audits.getAllValues()).allSatisfy(a -> {
            assertThat(a.getUserId()).isEqualTo(301);assertThat(a.getRoleId()).isEqualTo(401);
            assertThat(a.getOccurredAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        });
        assertThat(audits.getAllValues()).extracting(ScholarshipAudit::getCorrelationId).containsOnly(audits.getAllValues().getFirst().getCorrelationId());
    }

    @Test void periodMismatchIsAConflictAndDoesNotOverwriteFacts() {
        var existing=semester(501,"2024-2025",1);
        var mapping=mapping(601,501,21);
        when(source.findById(21)).thenReturn(Optional.of(term(21,"2510")));
        when(repo.mappingBySource(21)).thenReturn(Optional.of(mapping));when(repo.semester(501)).thenReturn(existing);
        failure(() -> service.create(new RoundDtos.CreateCommand(21,1,"Đợt"),actor),409,"SEMESTER_MAPPING_CONFLICT");
        verify(repo,never()).persist(any());
    }

    @Test void repeatedPostIsConflictEvenWithIdenticalName() {
        var sem=semester(501,"2025-2026",1);
        when(source.findById(21)).thenReturn(Optional.of(term(21,"2510")));
        when(repo.semesterByPeriod("2025-2026",1)).thenReturn(Optional.of(sem));
        when(repo.numberTaken(501,1,null)).thenReturn(true);
        failure(() -> service.create(new RoundDtos.CreateCommand(21,1,"Đợt"),actor),409,"ROUND_EXISTS");
        verify(repo,never()).persistAudit(any());
    }

    @Test void noOpWithCurrentTagKeepsTagAndAuditUnchanged() {
        stored("draft");
        var current=service.detail(1001);assertThat(current).isNotNull();
        var result=service.update(1001,new RoundDtos.UpdateCommand(1,"Đợt 1"),current.etag(),actor);
        assertThat(result).isEqualTo(current);
        verify(repo,never()).changeRound(anyLong(),any(),anyInt(),anyString());verify(repo,never()).persistAudit(any());
    }

    @Test void staleTagIsCheckedBeforeStateAndEvenForUnchangedPayload() {
        stored("closed");
        failure(() -> service.update(1001,new RoundDtos.UpdateCommand(1,"Đợt 1"),"\"stale\"",actor),412,"VERSION_CONFLICT");
        verify(repo,never()).changeRound(anyLong(),any(),anyInt(),anyString());
    }

    @Test void nonDraftWithCurrentTagCannotBeEdited() {
        stored("closed");var current=service.detail(1001);assertThat(current).isNotNull();
        failure(() -> service.update(1001,new RoundDtos.UpdateCommand(1,"Đợt 1"),current.etag(),actor),409,"ROUND_NOT_EDITABLE");
    }

    @Test void changedUpdateKeepsSemesterAliasCreatorAndAuditsBeforeAndAfter() throws Exception {
        var row=stored("draft");
        var currentVersion=new java.util.concurrent.atomic.AtomicReference<>(version);
        when(repo.lockRound(1001)).thenAnswer(call -> currentVersion.get());
        doAnswer(call -> {
            row.setNumber(call.getArgument(2)); row.setName(call.getArgument(3));
            currentVersion.set(new byte[]{0,0,0,0,0,0,0,2}); return null;
        }).when(repo).changeRound(eq(1001L),any(),anyInt(),anyString());
        var before=service.detail(1001);
        var after=service.update(1001,new RoundDtos.UpdateCommand(2,"Tên mới"),before.etag(),actor);
        assertThat(after).isNotNull();
        assertThat(after.etag()).isNotEqualTo(before.etag());
        assertThat(after.body().number()).isEqualTo(2);assertThat(after.body().name()).isEqualTo("Tên mới");
        assertThat(after.body().semester()).isEqualTo(before.body().semester());
        assertThat(after.body().sourceSemesterId()).isEqualTo("21");assertThat(after.body().createdBy()).isEqualTo("301");
        var captured=org.mockito.ArgumentCaptor.forClass(ScholarshipAudit.class);
        verify(repo).persistAudit(captured.capture());
        assertThat(captured.getValue().getAction()).isEqualTo("round.updated");
        assertThat(json.readTree(captured.getValue().getBeforeData()).path("number").asInt()).isEqualTo(1);
        assertThat(json.readTree(captured.getValue().getAfterData()).path("number").asInt()).isEqualTo(2);
    }

    @Test void duplicateNumberOnUpdateLeavesDataAndAuditUntouched() {
        stored("draft");var before=service.detail(1001);
        when(repo.numberTaken(501,2,1001L)).thenReturn(true);
        failure(() -> service.update(1001,new RoundDtos.UpdateCommand(2,"Đợt 2"),before.etag(),actor),409,"ROUND_EXISTS");
        verify(repo,never()).changeRound(anyLong(),any(),anyInt(),anyString());verify(repo,never()).persistAudit(any());
    }

    @Test void summerReadFilterResolvesMainSemesterWithoutCreatingAlias() {
        when(source.findById(22)).thenReturn(Optional.of(term(22,"2521")));
        when(repo.semesterByPeriod("2025-2026",2)).thenReturn(Optional.of(semester(502,"2025-2026",2)));
        var expected=new RoundDtos.Page<RoundDtos.RoundSummary>(List.of(),0,20,0);
        when(repo.list(502L,null,new int[]{0,20})).thenReturn(expected);
        assertThat(service.list(null,22L,null,new int[]{0,20})).isEqualTo(expected);
        verify(repo,never()).persist(any());
    }

    AcademicSemesterSource.Semester term(long id,String code) {
        return new AcademicSemesterSource.Semester(id,code,"SYN-HB","raw-is-not-authority",LocalDate.parse("2025-09-01"),LocalDate.parse("2026-01-15"),"verified",true,version);
    }
    ScholarshipSemester semester(long id,String year,int order) {
        var s=new ScholarshipSemester();ReflectionTestUtils.setField(s,"id",id);s.setAcademicYear(year);s.setSemesterOrder(order);
        s.setStartDate(LocalDate.parse("2025-09-01"));s.setEndDate(LocalDate.parse("2026-01-15"));s.setStatus("draft");return s;
    }
    SourceSemesterMapping mapping(long id,long semester,long sourceId) {
        var m=new SourceSemesterMapping();ReflectionTestUtils.setField(m,"id",id);m.setSemesterId(semester);m.setSourceSystem("CTSV");m.setSourceKind("ctsv_hoc_ky_id");m.setSourceSemesterCode(""+sourceId);return m;
    }
    ScholarshipRound stored(String state) {
        var r=new ScholarshipRound();ReflectionTestUtils.setField(r,"id",1001L);r.setSemesterId(501L);r.setSourceMappingId(601L);r.setNumber(1);r.setName("Đợt 1");r.setStatus(state);r.setCreatedBy(301L);
        when(repo.lockRound(1001)).thenReturn(version);when(repo.round(1001)).thenReturn(r);when(repo.semester(501)).thenReturn(semester(501,"2025-2026",1));when(repo.mapping(601)).thenReturn(mapping(601,501,21));return r;
    }
    void failure(Runnable action,int status,String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(RoundFailure.class,e -> {assertThat(e.status).isEqualTo(status);assertThat(e.code).isEqualTo(code);});
    }
}
