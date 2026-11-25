package pbl6.ctsv.hocbong.kkht.configuration.combination;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import pbl6.ctsv.foundation.api.ScholarshipCombinationSource;
import pbl6.ctsv.hocbong.kkht.configuration.combination.entity.TrainingCombination;
import pbl6.ctsv.hocbong.kkht.round.RoundAccess;
import pbl6.ctsv.hocbong.kkht.round.RoundFailure;
import pbl6.ctsv.hocbong.kkht.round.RoundRules;
import pbl6.ctsv.hocbong.kkht.round.entity.ScholarshipAudit;

class CombinationService {
    private final CombinationRepository repository;
    private final ScholarshipCombinationSource source;
    private final List<CombinationUsage> usages;
    private final ObjectMapper json;
    private final Clock clock;

    CombinationService(CombinationRepository repository, ScholarshipCombinationSource source,
            List<CombinationUsage> usages, ObjectMapper json, Clock clock) {
        this.repository=repository; this.source=source; this.usages=List.copyOf(usages); this.json=json; this.clock=clock;
    }

    CombinationDtos.Page<ScholarshipCombinationSource.Option> sources(ScholarshipCombinationSource.Kind kind,
            String q, int[] page) {
        var result=source.findPage(kind, q, page[0], page[1]);
        return new CombinationDtos.Page<>(result.items(), page[0], page[1], result.total());
    }

    CombinationDtos.Page<CombinationDtos.Detail> list(int[] page) {
        return new CombinationDtos.Page<>(repository.list(page[0],page[1]).stream().map(this::detail).toList(),
                page[0], page[1], repository.count());
    }

    CombinationDtos.View get(long id) { return view(repository.get(id)); }

    CombinationDtos.View create(CombinationDtos.Command command, RoundAccess.Actor actor) {
        repository.serializeWrites();
        var resolved=source.resolve(command.programId(), command.cohortId(), command.majorId(), command.curriculumCode());
        checkOverlap(0, command);
        var entity=new TrainingCombination();
        apply(entity, command, resolved);
        repository.save(entity);
        var result=view(entity);
        audit(actor, entity.getId(), "combination.created", null, result.body());
        return result;
    }

    CombinationDtos.View update(long id, CombinationDtos.Command command, String ifMatch, RoundAccess.Actor actor) {
        repository.serializeWrites();
        var entity=repository.lock(id);
        var before=view(entity);
        RoundRules.match(before.etag(), ifMatch);
        requireUnused(id);
        var resolved=source.resolve(command.programId(), command.cohortId(), command.majorId(), command.curriculumCode());
        checkOverlap(id, command);
        apply(entity, command, resolved);
        repository.save(entity);
        var result=view(entity);
        audit(actor, id, "combination.updated", before.body(), result.body());
        return result;
    }

    void delete(long id, String ifMatch, RoundAccess.Actor actor) {
        repository.serializeWrites();
        var entity=repository.lock(id);
        var before=view(entity);
        RoundRules.match(before.etag(), ifMatch);
        requireUnused(id);
        repository.delete(entity);
        audit(actor, id, "combination.deleted", before.body(), Map.of("id",Long.toString(id),"deleted",true));
    }

    private void checkOverlap(long id, CombinationDtos.Command command) {
        if (repository.overlaps(id,command)) throw new RoundFailure(409,"COMBINATION_OVERLAP","effectiveFrom");
    }

    private void requireUnused(long id) {
        if (usages.stream().anyMatch(usage -> usage.used(id))) throw new RoundFailure(409,"COMBINATION_IN_USE");
    }

    private void apply(TrainingCombination e, CombinationDtos.Command c, ScholarshipCombinationSource.Resolved s) {
        e.setProgramId(s.programId()); e.setProgramCode(s.programCode()); e.setProgramName(s.programName());
        e.setCohortId(s.cohortId()); e.setCohortCode(s.cohortCode()); e.setMajorId(s.majorId()); e.setMajorCode(s.majorCode());
        e.setCurriculumCode(s.curriculumCode()); e.setStandardSemesters((short)c.standardSemesters());
        e.setProgramType(c.programType()); e.setEffectiveFrom(c.effectiveFrom()); e.setEffectiveTo(c.effectiveTo());
    }

    private CombinationDtos.Detail detail(TrainingCombination e) {
        return new CombinationDtos.Detail(e.getId().toString(),e.getProgramId().toString(),e.getProgramCode(),e.getProgramName(),
                e.getCohortId().toString(),e.getCohortCode(),e.getMajorId().toString(),e.getMajorCode(),e.getCurriculumCode(),
                e.getStandardSemesters().intValue(),e.getProgramType(),e.getEffectiveFrom(),e.getEffectiveTo());
    }

    private CombinationDtos.View view(TrainingCombination entity) {
        var body=detail(entity);
        try {
            var hash=MessageDigest.getInstance("SHA-256");
            hash.update(("hocbong:combination:"+entity.getId()+"|").getBytes(StandardCharsets.UTF_8));
            hash.update(entity.getVersion());
            return new CombinationDtos.View(body,"\""+Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(hash.digest(json.writeValueAsBytes(body)))+"\"");
        } catch (Exception ex) { throw new IllegalStateException("Cannot encode combination",ex); }
    }

    private void audit(RoundAccess.Actor actor, long id, String action, Object before, Object after) {
        var event=new ScholarshipAudit();
        event.setUserId(actor.userId()); event.setRoleId(actor.roleId()); event.setObjectType("combination");
        event.setObjectId(id); event.setScope("HOC_BONG"); event.setAction(action);
        try {
            event.setBeforeData(before==null?null:json.writeValueAsString(before));
            event.setAfterData(json.writeValueAsString(after));
        } catch (Exception ex) { throw new IllegalStateException("Cannot encode combination audit",ex); }
        event.setOccurredAt(OffsetDateTime.now(clock)); event.setCorrelationId(UUID.randomUUID());
        repository.audit(event);
    }
}
