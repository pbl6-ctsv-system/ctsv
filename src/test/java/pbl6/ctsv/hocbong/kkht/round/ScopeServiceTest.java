package pbl6.ctsv.hocbong.kkht.round;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pbl6.ctsv.foundation.api.ScholarshipScopeSource;
import pbl6.ctsv.hocbong.kkht.round.entity.*;
import pbl6.ctsv.hocbong.kkht.scope.entity.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** HB-T024-011–021/023/025–031/036. Mock ports/repository, NOT SQL rollback or locking evidence. */
class ScopeServiceTest {
    final RoundRepository repo=mock(RoundRepository.class);
    final RoundService rounds=mock(RoundService.class);
    final ScholarshipScopeSource source=mock(ScholarshipScopeSource.class);
    final CompletedAssessmentLookup completed=mock(CompletedAssessmentLookup.class);
    final RoundScopeUsage usage=mock(RoundScopeUsage.class);
    final ScopeService service=new ScopeService(repo,rounds,source,completed,List.of(usage),new ObjectMapper(),Clock.systemUTC());
    final RoundAccess.Actor actor=new RoundAccess.Actor(301,null,401L);
    final UUID key=UUID.fromString("a1234567-1234-4234-8234-123456789012");
    final String revision="A".repeat(43);
    final ScopeRules.Selection all=new ScopeRules.Selection("all",List.of());
    final List<ScholarshipScopeSource.Group> groups=List.of(new ScholarshipScopeSource.Group(10,"K25",20,"CNTT","Công nghệ thông tin",2));
    final Map<Long,Object> saved=new HashMap<>();
    final List<RoundScopeGroup> savedGroups=new ArrayList<>();
    long next=500;
    @BeforeEach void fixture() {
        when(rounds.sourceById(21)).thenReturn(new RoundDtos.SourceSemesterDto("21","2520",null,"2025-2026",2,"2026-01-01","2026-06-30",null,true,null));
        when(source.catalog(21)).thenReturn(new ScholarshipScopeSource.Catalog(revision,groups));
        when(rounds.detail(anyLong())).thenAnswer(call->view(call.getArgument(0),"\"tag\""));
        when(repo.lockRound(anyLong())).thenReturn(new byte[]{1});
        doAnswer(call->{Object row=call.getArgument(0);ReflectionTestUtils.setField(row,"id",++next);saved.put(next,row);
            if(row instanceof RoundScopeGroup group)savedGroups.add(group);return null;}).when(repo).persist(any());
        when(repo.scope(anyLong())).thenAnswer(call->saved.get(call.getArgument(0)));
        when(repo.scopeGroups(anyLong())).thenAnswer(call->savedGroups.stream().filter(g->g.getScopeId().equals(call.getArgument(0))).toList());
    }
    @Test void createGeneratesNumberNameFixedScopeAndOneCorrelatedAuditTransaction() {
        var result=service.create(command(),key,actor);
        assertThat(result.replayed()).isFalse();
        var root=saved.values().stream().filter(ScholarshipRound.class::isInstance).map(ScholarshipRound.class::cast).findFirst().orElseThrow();
        assertThat(root.getNumber()).isEqualTo(1);
        assertThat(root.getName()).isEqualTo("2025/2026 - Học kỳ 2 - Đợt 1");
        assertThat(root.getCreationRequestId()).isEqualTo(key);
        assertThat(root.getCreationHash()).isEqualTo(ScopeRules.intentHash(command()));
        assertThat(savedGroups).singleElement().satisfies(g->{assertThat(g.getCohortId()).isEqualTo(10);assertThat(g.getStudentCount()).isEqualTo(2);});
        verify(repo).changeScope(eq(root.getId()),any(),anyLong());
        var events=org.mockito.ArgumentCaptor.forClass(ScholarshipAudit.class);verify(repo,times(3)).persistAudit(events.capture());
        assertThat(events.getAllValues()).extracting(ScholarshipAudit::getCorrelationId).containsOnly(events.getValue().getCorrelationId());
        assertThat(events.getValue().getAfterData()).contains("catalogRevision");
    }
    @Test void replayReturnsCurrentRepresentationBeforeSourceAndSequenceEvenAfterEdits() {
        var root=root();root.setCreationHash(ScopeRules.intentHash(command()));
        when(repo.creation(actor.userId(),key)).thenReturn(Optional.of(root));
        var replay=service.create(command(),key,actor);
        assertThat(replay.replayed()).isTrue();assertThat(replay.view().etag()).isEqualTo("\"tag\"");
        verifyNoInteractions(source,completed);verify(rounds,never()).sourceById(anyLong());verify(repo,never()).persist(any());
        failure(()->service.create(new ScopeRules.Create(22,revision,all),key,actor),"IDEMPOTENCY_KEY_REUSED");
    }
    @Test void commandLookupIsBoundToActorAndDoesNotReadSource() {
        when(repo.creation(actor.userId(),key)).thenReturn(Optional.of(root()));
        assertThat(service.lookup(key,actor).roundId()).isEqualTo("1001");
        assertThatThrownBy(()->service.lookup(key,new RoundAccess.Actor(302,null,401L))).isInstanceOfSatisfying(RoundFailure.class,e->assertThat(e.status).isEqualTo(404));
        verifyNoInteractions(source);
    }
    @Test void staleOrEmptyOrMissingGroupLeavesNoPersistence() {
        failure(()->service.create(new ScopeRules.Create(21,"B".repeat(43),all),key,actor),"SCOPE_SOURCE_CHANGED");
        failure(()->service.create(new ScopeRules.Create(21,revision,new ScopeRules.Selection("groups",List.of(new ScopeRules.Pair(99,99)))),key,actor),"SCOPE_GROUP_UNAVAILABLE");
        when(source.catalog(21)).thenReturn(new ScholarshipScopeSource.Catalog(revision,List.of()));
        failure(()->service.create(command(),key,actor),"SCOPE_EMPTY");
        verify(repo,never()).persist(any());verify(repo,never()).persistAudit(any());
    }
    @Test void periodWithLegacyBlocksButAnUnrelatedCleanPeriodDoesNot() {
        var period=new ScholarshipSemester();ReflectionTestUtils.setField(period,"id",99L);
        when(repo.semesterByPeriod("2025-2026",2)).thenReturn(Optional.of(period));
        var legacy=root();legacy.setCurrentScopeId(null);when(repo.periodRounds(99)).thenReturn(List.of(legacy));
        failure(()->service.create(command(),key,actor),"LEGACY_ROUND_RECONCILIATION_REQUIRED");
        verifyNoInteractions(source);verify(repo,never()).persist(any());
        when(repo.periodRounds(99)).thenReturn(List.of());period.setAcademicYear("2025-2026");period.setSemesterOrder(2);
        assertThat(service.create(command(),key,actor).replayed()).isFalse();
    }
    @Test void nextRoundRequiresMatchingFullPipelineProofAndProtectsOverflow() {
        var period=new ScholarshipSemester();ReflectionTestUtils.setField(period,"id",99L);period.setAcademicYear("2025-2026");period.setSemesterOrder(2);
        when(repo.semesterByPeriod("2025-2026",2)).thenReturn(Optional.of(period));
        var previous=root();when(repo.periodRounds(99)).thenReturn(List.of(previous));
        failure(()->service.create(command(),key,actor),"ROUND_SEQUENCE_NOT_READY");
        when(completed.latestCompleted(1001)).thenReturn(Optional.of(new CompletedAssessmentLookup.Proof(1001,9,999,OffsetDateTime.now())));
        failure(()->service.create(command(),key,actor),"ROUND_SEQUENCE_NOT_READY");
        previous.setNumber(Integer.MAX_VALUE);
        failure(()->service.create(command(),key,actor),"ROUND_NUMBER_EXHAUSTED");
    }
    @Test void completedGroupsCannotBeSelectedAgainAndTheirHistoryChangesCatalogRevision() {
        var period=new ScholarshipSemester();ReflectionTestUtils.setField(period,"id",99L);period.setAcademicYear("2025-2026");period.setSemesterOrder(2);
        when(repo.semesterByPeriod("2025-2026",2)).thenReturn(Optional.of(period));
        when(repo.periodRounds(99)).thenReturn(List.of(root()));
        when(completed.latestCompleted(1001)).thenReturn(Optional.of(new CompletedAssessmentLookup.Proof(1001,9,88,OffsetDateTime.now())));
        when(completed.assessedGroups(99)).thenReturn(Set.of(new CompletedAssessmentLookup.GroupKey(10,20)));
        when(rounds.sourceById(21)).thenReturn(new RoundDtos.SourceSemesterDto("21","2520",null,"2025-2026",2,"2026-01-01","2026-06-30","99",true,null));
        var eligible=service.catalog(21,null,null,null,new int[]{0,20});
        assertThat(eligible.items()).isEmpty();assertThat(eligible.catalogRevision()).isNotEqualTo(revision);
        failure(()->service.create(new ScopeRules.Create(21,eligible.catalogRevision(),new ScopeRules.Selection("groups",List.of(new ScopeRules.Pair(10,20)))),key,actor),"SCOPE_GROUP_UNAVAILABLE");
        failure(()->service.create(new ScopeRules.Create(21,eligible.catalogRevision(),all),key,actor),"SCOPE_EMPTY");
        verify(repo,never()).persist(any());
    }
    @Test void updateRequiresFreshTagBeforeStateOrLegacyAndLocksUsedScope() {
        var root=root();when(repo.round(1001)).thenReturn(root);
        assertThatThrownBy(()->service.update(1001,new ScopeRules.Update(revision,all),"\"stale\"",actor)).isInstanceOfSatisfying(RoundFailure.class,e->assertThat(e.status).isEqualTo(412));
        root.setCurrentScopeId(null);failure(()->service.update(1001,new ScopeRules.Update(revision,all),"\"tag\"",actor),"LEGACY_ROUND_RECONCILIATION_REQUIRED");
        root.setCurrentScopeId(88L);when(usage.used(1001,88)).thenReturn(true);
        failure(()->service.update(1001,new ScopeRules.Update(revision,all),"\"tag\"",actor),"SCOPE_LOCKED");
        verifyNoInteractions(source);verify(repo,never()).changeScope(anyLong(),any(),anyLong());
    }
    @Test void noOpStillChecksSourceAndDoesNotAppendOrAudit() {
        storedScope();
        var result=service.update(1001,new ScopeRules.Update(revision,all),"\"tag\"",actor);
        assertThat(result.etag()).isEqualTo("\"tag\"");verify(source).catalog(21);
        verify(repo,never()).persist(any());verify(repo,never()).persistAudit(any());
        when(source.catalog(21)).thenReturn(new ScholarshipScopeSource.Catalog("B".repeat(43),groups));
        failure(()->service.update(1001,new ScopeRules.Update(revision,all),"\"tag\"",actor),"SCOPE_SOURCE_CHANGED");
    }
    @Test void changedScopeAppendsRevisionAndKeepsOldSnapshotAndRootIdentity() {
        storedScope();
        var updated=List.of(new ScholarshipScopeSource.Group(10,"K25-new",20,"CNTT","Đổi nhãn",3));
        when(source.catalog(21)).thenReturn(new ScholarshipScopeSource.Catalog(revision,updated));
        service.update(1001,new ScopeRules.Update(revision,all),"\"tag\"",actor);
        var revision2=saved.values().stream().filter(RoundScopeVersion.class::isInstance).map(RoundScopeVersion.class::cast).filter(v->v.getRevision()==2).findFirst().orElseThrow();
        verify(repo).changeScope(eq(1001L),any(),eq(revision2.getId()));
        verify(repo).persistAudit(any());
        assertThat(savedGroups.getFirst().getCohortCode()).isEqualTo("K25");
        assertThat(savedGroups.getLast().getCohortCode()).isEqualTo("K25-new");
    }
    @Test void legacyScopeReadsNullAndDoesNotQueryProvider() {
        var root=root();root.setCurrentScopeId(null);when(repo.round(1001)).thenReturn(root);
        assertThat(service.scope(1001).scope()).isNull();verifyNoInteractions(source);
    }
    @Test void catalogHasLiteralAndFiltersAndRevisionIndependentOfPage() {
        var catalog=service.catalog(21,10L,20L,"công",new int[]{0,1});
        assertThat(catalog.catalogRevision()).isEqualTo(revision);assertThat(catalog.totalElements()).isEqualTo(1);
        assertThat(service.catalog(21,null,null,"%",new int[]{0,20}).items()).isEmpty();
        assertThat(service.catalog(21,null,null,null,new int[]{1,1}).items()).isEmpty();
    }
    void storedScope() {
        when(repo.round(1001)).thenReturn(root());
        var scope=new RoundScopeVersion();ReflectionTestUtils.setField(scope,"id",88L);scope.setRoundId(1001L);scope.setRevision(1);scope.setMode("all");scope.setCatalogRevision(revision);scope.setCreatedBy(301L);scope.setCreatedAt(OffsetDateTime.now());saved.put(88L,scope);
        var group=new RoundScopeGroup();group.setScopeId(88L);group.setCohortId(10L);group.setMajorId(20L);group.setCohortCode("K25");group.setMajorCode("CNTT");group.setMajorName("Công nghệ thông tin");group.setStudentCount(2);savedGroups.add(group);
    }
    ScholarshipRound root() {
        var row=new ScholarshipRound();ReflectionTestUtils.setField(row,"id",1001L);row.setSemesterId(99L);row.setNumber(1);row.setStatus("draft");row.setCurrentScopeId(88L);row.setCreationRequestId(key);row.setCreationHash(new byte[32]);return row;
    }
    ScopeRules.Create command() {return new ScopeRules.Create(21,revision,all);}
    RoundDtos.View view(long id,String tag) {
        return new RoundDtos.View(new RoundDtos.RoundDetail(""+id,"21",1,"2025/2026 - Học kỳ 2 - Đợt 1","draft","301",new RoundDtos.SemesterDto("99","2025-2026",2,"2026-01-01","2026-06-30","draft"),null,null,null,null),tag);
    }
    void failure(Runnable call,String code) {assertThatThrownBy(call::run).isInstanceOfSatisfying(RoundFailure.class,e->assertThat(e.code).isEqualTo(code));}
}
