package pbl6.ctsv.hocbong.kkht.round;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import pbl6.ctsv.foundation.api.ScholarshipScopeSource;
import pbl6.ctsv.hocbong.kkht.round.entity.*;
import pbl6.ctsv.hocbong.kkht.scope.entity.*;

/** Contract 0.3 workflow. Caller owns auth + transaction, including source locks and audit. */
class ScopeService {
    private final RoundRepository repo;
    private final RoundService rounds;
    private final ScholarshipScopeSource source;
    private final CompletedAssessmentLookup completed;
    private final List<RoundScopeUsage> usages;
    private final ObjectMapper json;
    private final Clock clock;
    ScopeService(RoundRepository repo,RoundService rounds,ScholarshipScopeSource source,
            CompletedAssessmentLookup completed,List<RoundScopeUsage> usages,ObjectMapper json,Clock clock) {
        this.repo=repo;this.rounds=rounds;this.source=source;this.completed=completed;
        this.usages=List.copyOf(usages);this.json=json;this.clock=clock;
    }
    ScopeDtos.Catalog catalog(long sourceId,Long cohort,Long major,String q,int[] page) {
        var semester=requireSource(sourceId);
        var catalog=eligibleCatalog(sourceId,semester.scholarshipSemesterId()==null?null:Long.parseLong(semester.scholarshipSemesterId()));
        var text=q==null?null:q.toLowerCase(Locale.ROOT);
        var groups=catalog.groups().stream()
            .filter(g->cohort==null||g.cohortId()==cohort)
            .filter(g->major==null||g.majorId()==major)
            .filter(g->text==null||contains(g.cohortCode(),text)||contains(g.majorCode(),text)||contains(g.majorName(),text))
            .sorted(DISPLAY_ORDER).toList();
        int from=Math.min(groups.size(),page[0]*page[1]);
        return new ScopeDtos.Catalog(catalog.revision(),groups.subList(from,Math.min(groups.size(),from+page[1])).stream()
            .map(ScopeService::dto).toList(),page[0],page[1],groups.size());
    }
    ScopeDtos.CreationLookup lookup(UUID key,RoundAccess.Actor actor) {
        var row=repo.creation(actor.userId(),key).orElseThrow(()->new RoundFailure(404,"NOT_FOUND"));
        return new ScopeDtos.CreationLookup(key.toString(),row.getId().toString());
    }
    ScopeDtos.View scope(long round) {
        var parent=rounds.detail(round);
        return new ScopeDtos.View(Long.toString(round),parent.etag(),version(repo.round(round).getCurrentScopeId()));
    }
    ScopeDtos.Created create(ScopeRules.Create command,UUID key,RoundAccess.Actor actor) {
        repo.serializeWrites();
        byte[] hash=ScopeRules.intentHash(command);
        var prior=repo.creation(actor.userId(),key);
        if(prior.isPresent()) {
            if(!java.security.MessageDigest.isEqual(prior.get().getCreationHash(),hash)) throw new RoundFailure(409,"IDEMPOTENCY_KEY_REUSED");
            return new ScopeDtos.Created(rounds.detail(prior.get().getId()),true);
        }
        var sourceSemester=requireSource(command.sourceSemesterId());
        var semester=repo.semesterByPeriod(sourceSemester.academicYear(),sourceSemester.semesterOrder()).orElse(null);
        int number=semester==null?1:nextNumber(semester.getId());
        var groups=resolve(command.sourceSemesterId(),command.catalogRevision(),command.scope(),semester==null?null:semester.getId());
        var now=OffsetDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);
        UUID correlation=UUID.randomUUID();
        if(semester==null) {
            semester=new ScholarshipSemester();semester.setAcademicYear(sourceSemester.academicYear());
            semester.setSemesterOrder(sourceSemester.semesterOrder());semester.setStartDate(LocalDate.parse(sourceSemester.startDate()));
            semester.setEndDate(LocalDate.parse(sourceSemester.endDate()));semester.setStatus("draft");repo.persist(semester);
            audit(actor,"semester.initialized","hb_hoc_ky",semester.getId(),semester.getId(),null,RoundMapper.semester(semester),correlation,now);
        }
        var mapping=repo.mappingBySource(command.sourceSemesterId()).orElse(null);
        if(mapping==null) {
            mapping=new SourceSemesterMapping();mapping.setSourceSystem("CTSV");mapping.setSourceKind("ctsv_hoc_ky_id");
            mapping.setSourceSemesterCode(Long.toString(command.sourceSemesterId()));mapping.setSemesterId(semester.getId());
            mapping.setConfirmedBy(actor.userId());mapping.setConfirmedAt(now);repo.persist(mapping);
            audit(actor,"semester.source-linked","hb_anh_xa_ky_nguon",mapping.getId(),semester.getId(),null,
                Map.of("sourceSemesterId",Long.toString(command.sourceSemesterId()),"semesterId",semester.getId().toString()),correlation,now);
        }
        var row=new ScholarshipRound();row.setSemesterId(semester.getId());row.setSourceMappingId(mapping.getId());
        row.setNumber(number);row.setName(ScopeRules.name(semester.getAcademicYear(),semester.getSemesterOrder(),number));
        row.setStatus("draft");row.setCreatedBy(actor.userId());row.setCreationRequestId(key);row.setCreationHash(hash);repo.persist(row);
        long id=row.getId();byte[] tag=repo.lockRound(id);
        var scope=append(id,1,command.catalogRevision(),command.scope(),groups,actor,now);
        repo.changeScope(id,tag,scope.getId());
        var view=rounds.detail(id);
        audit(actor,"round.created","kkht_dot_xet",id,semester.getId(),null,
            Map.of("round",view.body(),"scope",version(scope.getId())),correlation,now);
        return new ScopeDtos.Created(view,false);
    }
    RoundDtos.View update(long id,ScopeRules.Update command,String tag,RoundAccess.Actor actor) {
        repo.serializeWrites();
        var before=rounds.detail(id);RoundRules.match(before.etag(),tag);
        var row=repo.round(id);
        if(!"draft".equals(row.getStatus())) throw new RoundFailure(409,"ROUND_NOT_EDITABLE");
        if(legacy(row)) throw new RoundFailure(409,"LEGACY_ROUND_RECONCILIATION_REQUIRED");
        if(usages.stream().anyMatch(usage->usage.used(id,row.getCurrentScopeId()))) throw new RoundFailure(409,"SCOPE_LOCKED");
        var previous=version(row.getCurrentScopeId());
        var sourceId=Long.parseLong(before.body().sourceSemesterId());requireSource(sourceId);
        var groups=resolve(sourceId,command.catalogRevision(),command.scope(),row.getSemesterId());
        if(previous.mode().equals(command.scope().mode())&&previous.catalogRevision().equals(command.catalogRevision())
                &&previous.groups().equals(groups.stream().map(ScopeService::dto).toList())) return before;
        if(previous.revision()==Integer.MAX_VALUE) throw new RoundFailure(409,"SCOPE_LOCKED");
        long period=row.getSemesterId();byte[] version=repo.lockRound(id);
        var now=OffsetDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);
        var next=append(id,previous.revision()+1,command.catalogRevision(),command.scope(),groups,actor,now);
        repo.changeScope(id,version,next.getId());
        var after=rounds.detail(id);
        audit(actor,"round.scope-updated","kkht_dot_xet",id,period,previous,version(next.getId()),UUID.randomUUID(),now);
        return after;
    }
    private int nextNumber(long period) {
        repo.lockPeriod(period);
        var existing=repo.periodRounds(period);
        if(existing.stream().anyMatch(ScopeService::legacy)) throw new RoundFailure(409,"LEGACY_ROUND_RECONCILIATION_REQUIRED");
        if(existing.isEmpty()) return 1;
        var last=existing.getFirst();
        if(last.getNumber()==Integer.MAX_VALUE) throw new RoundFailure(409,"ROUND_NUMBER_EXHAUSTED");
        var proof=completed.latestCompleted(last.getId()).orElseThrow(()->new RoundFailure(409,"ROUND_SEQUENCE_NOT_READY"));
        if(proof.roundId()!=last.getId()||proof.scopeVersionId()!=last.getCurrentScopeId()||proof.runId()<1||proof.completedAt()==null)
            throw new RoundFailure(409,"ROUND_SEQUENCE_NOT_READY");
        return last.getNumber()+1;
    }
    private static boolean legacy(ScholarshipRound row) {
        return row.getCurrentScopeId()==null||row.getCreationRequestId()==null||row.getCreationHash()==null;
    }
    private RoundDtos.SourceSemesterDto requireSource(long id) {
        var semester=rounds.sourceById(id);
        if(!semester.selectable()) {
            String reason=semester.unavailableReason();
            throw new RoundFailure(409,Set.of("SEMESTER_MAPPING_REQUIRED","SEMESTER_MAPPING_CONFLICT").contains(reason)?reason:"SOURCE_SEMESTER_UNAVAILABLE");
        }
        return semester;
    }
    private List<ScholarshipScopeSource.Group> resolve(long sourceId,String revision,ScopeRules.Selection selection,Long period) {
        var catalog=eligibleCatalog(sourceId,period);
        if(!catalog.revision().equals(revision)) throw new RoundFailure(409,"SCOPE_SOURCE_CHANGED");
        var byId=new HashMap<ScopeRules.Pair,ScholarshipScopeSource.Group>();
        catalog.groups().forEach(group->byId.put(new ScopeRules.Pair(group.cohortId(),group.majorId()),group));
        if("all".equals(selection.mode())) {
            if(byId.isEmpty()) throw new RoundFailure(409,"SCOPE_EMPTY");
            if(byId.size()>ScopeRules.LIMIT) throw RoundFailure.invalid("scope");
            return byId.values().stream().sorted(ID_ORDER).toList();
        }
        return selection.groups().stream().sorted(ScopeRules.PAIRS).map(pair->{
            var group=byId.get(pair);if(group==null) throw new RoundFailure(409,"SCOPE_GROUP_UNAVAILABLE");return group;
        }).toList();
    }
    private ScholarshipScopeSource.Catalog eligibleCatalog(long sourceId,Long period) {
        var catalog=source.catalog(sourceId);
        var assessed=period==null?Set.<CompletedAssessmentLookup.GroupKey>of():completed.assessedGroups(period);
        if(assessed.isEmpty()) return catalog;
        var groups=catalog.groups().stream().filter(group->!assessed.contains(new CompletedAssessmentLookup.GroupKey(group.cohortId(),group.majorId()))).toList();
        try {
            var material=List.of("hocbong:scope-eligibility:v1",catalog.revision(),assessed.stream().sorted(
                Comparator.comparingLong(CompletedAssessmentLookup.GroupKey::cohortId).thenComparingLong(CompletedAssessmentLookup.GroupKey::majorId)).toList());
            var revision=Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(material)));
            return new ScholarshipScopeSource.Catalog(revision,groups);
        } catch(Exception e) {throw new IllegalStateException("Cannot fingerprint scope history",e);}
    }
    private RoundScopeVersion append(long round,int revision,String sourceRevision,ScopeRules.Selection selection,
            List<ScholarshipScopeSource.Group> groups,RoundAccess.Actor actor,OffsetDateTime now) {
        var scope=new RoundScopeVersion();scope.setRoundId(round);scope.setRevision(revision);scope.setMode(selection.mode());
        scope.setCatalogRevision(sourceRevision);scope.setCreatedBy(actor.userId());scope.setCreatedAt(now);repo.persist(scope);
        for(var value:groups) {
            var row=new RoundScopeGroup();row.setScopeId(scope.getId());row.setCohortId(value.cohortId());row.setMajorId(value.majorId());
            row.setCohortCode(value.cohortCode());row.setMajorCode(value.majorCode());row.setMajorName(value.majorName());
            row.setStudentCount(value.studentCount());repo.persist(row);
        }
        return scope;
    }
    private ScopeDtos.Version version(Long id) {
        if(id==null) return null;
        var row=repo.scope(id);
        var groups=repo.scopeGroups(id).stream().map(group->new ScopeDtos.Group(group.getCohortId().toString(),group.getCohortCode(),
            group.getMajorId().toString(),group.getMajorCode(),group.getMajorName(),group.getStudentCount())).toList();
        return new ScopeDtos.Version(row.getId().toString(),row.getRevision(),row.getMode(),row.getCatalogRevision(),groups,
            row.getCreatedAt().toString(),row.getCreatedBy().toString());
    }
    private void audit(RoundAccess.Actor actor,String action,String type,long id,long period,Object before,Object after,UUID correlation,OffsetDateTime now) {
        var event=new ScholarshipAudit();event.setUserId(actor.userId());event.setRoleId(actor.roleId());event.setAction(action);
        event.setObjectType(type);event.setObjectId(id);event.setScope("semester:"+period);event.setCorrelationId(correlation);event.setOccurredAt(now);
        try {event.setBeforeData(before==null?null:json.writeValueAsString(before));event.setAfterData(json.writeValueAsString(after));}
        catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalStateException("Cannot encode scope audit",e);}
        repo.persistAudit(event);
    }
    private static boolean contains(String value,String text) {return value!=null&&value.toLowerCase(Locale.ROOT).contains(text);}
    private static ScopeDtos.Group dto(ScholarshipScopeSource.Group group) {
        return new ScopeDtos.Group(Long.toString(group.cohortId()),group.cohortCode(),Long.toString(group.majorId()),group.majorCode(),group.majorName(),group.studentCount());
    }
    private static final Comparator<ScholarshipScopeSource.Group> ID_ORDER=Comparator.comparingLong(ScholarshipScopeSource.Group::cohortId).thenComparingLong(ScholarshipScopeSource.Group::majorId);
    private static final Comparator<ScholarshipScopeSource.Group> DISPLAY_ORDER=Comparator.comparing(ScholarshipScopeSource.Group::cohortCode,Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(ScholarshipScopeSource.Group::majorCode,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(ID_ORDER);
}
