package pbl6.ctsv.mghp.configuration;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import pbl6.ctsv.mghp.application.ReviewSourceAccess;
import pbl6.ctsv.mghp.configuration.entity.MghpDotXet;

/** Narrow round commands; caller owns authorization and the shared MGHP transaction write fence. */
final class RoundTimelineService {
    private final ConfigurationRepository r;
    private final RoundService rounds;
    private final RoundTimelineRepository history;
    private final ObjectProvider<ReviewSourceAccess> sources;
    private final ObjectMapper json;
    private final Clock clock;
    RoundTimelineService(ConfigurationRepository r,RoundService rounds,ObjectProvider<ReviewSourceAccess> sources,ObjectMapper json,Clock clock) {
        this.r=r;this.rounds=rounds;this.history=new RoundTimelineRepository(r);this.sources=sources;this.json=json;this.clock=clock;
    }
    PolicyService.View update(long id,JsonNode body,String tag,long actor) {
        ConfigRules.shape(body,"schedule,reason");String reason=ConfigRules.text(body,"reason",4000,true);
        var input=body.path("schedule");ConfigRules.shape(input,TimelineRules.FIELDS+",reviewDeadline");
        var planned=input.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)planned).remove("reviewDeadline");
        var plan=TimelineRules.parse(planned);var deadline=TimelineRules.instant(input,"reviewDeadline");
        var fence=r.fence("mghp_dot_xet",id);ConfigRules.match(rounds.detail(id,true).tag(),tag);
        var round=r.find(MghpDotXet.class,id);editable(round);
        if(deadline!=null&&round.getHanNop()!=null&&deadline.isBefore(round.getHanNop().toInstant()))throw ConfigFailure.invalid("reviewDeadline");
        history.baseline(id,actor,now());var values=RoundTimelineRepository.columns(plan);
        values.put("han_ra_soat",RoundTimelineRepository.offset(deadline));values.put("thoi_diem_cap_nhat",now());
        r.cas("mghp_dot_xet",id,fence,values);history.changed(id,actor,"SCHEDULE",reason,now());return rounds.detail(id,true);
    }
    PolicyService.View extend(long id,JsonNode body,String tag,long actor) {
        ConfigRules.shape(body,"submissionDeadline,reason");String reason=ConfigRules.text(body,"reason",4000,true);
        var deadline=TimelineRules.instant(body,"submissionDeadline");if(deadline==null)throw ConfigFailure.invalid("submissionDeadline");
        var fence=r.fence("mghp_dot_xet",id);ConfigRules.match(rounds.detail(id,true).tag(),tag);
        var round=r.find(MghpDotXet.class,id);
        if(!"open".equals(round.getTrangThai())||round.getThoiDiemDongXet()!=null)throw state();
        if(!deadline.isAfter(clock.instant())||round.getHanNop()==null||!deadline.isAfter(round.getHanNop().toInstant())
            ||round.getHanRaSoat()==null||deadline.isAfter(round.getHanRaSoat().toInstant()))throw ConfigFailure.invalid("submissionDeadline");
        history.baseline(id,actor,now());var values=new LinkedHashMap<String,Object>();
        values.put("han_nop",RoundTimelineRepository.offset(deadline));values.put("thoi_diem_cap_nhat",now());
        r.cas("mghp_dot_xet",id,fence,values);history.changed(id,actor,"EXTEND_SUBMISSION",reason,now());return rounds.detail(id,true);
    }
    Object history(long id,int[] page){return history.history(id,page);}
    PolicyService.View preflight(long id) {
        var round=r.find(MghpDotXet.class,id);
        var source=sources.getIfAvailable();if(source==null)throw new ConfigFailure(503,"DEPENDENCY_UNAVAILABLE");
        var snapshot=source.read(id);var blockers=new ArrayList<>(snapshot.blockers());
        if(!"closed".equals(round.getTrangThai()))blockers.add(new ReviewSourceAccess.Blocker(null,null,"INTAKE_NOT_CLOSED"));
        if(r.pending(id)&&blockers.stream().noneMatch(b->b.code().equals("SUPPLEMENT_UNRESOLVED")))
            blockers.add(new ReviewSourceAccess.Blocker(null,null,"MANDATORY_WORK_UNRESOLVED"));
        var counts=new TreeMap<String,Integer>();for(var b:blockers)counts.merge(b.code(),1,Integer::sum);
        var body=new LinkedHashMap<String,Object>();body.put("roundId",Long.toString(id));body.put("status",round.getTrangThai());
        body.put("reviewClosedAt",TimelineModels.string(RoundTimelineRepository.instant(round.getThoiDiemDongXet())));
        body.put("sourceSha256",snapshot.sha256());body.put("eligibleCount",snapshot.eligibleCount());body.put("excludedCount",snapshot.excludedCount());
        body.put("blockerCount",blockers.size());body.put("counts",counts);body.put("blockers",blockers.stream().limit(20).toList());body.put("hasMore",blockers.size()>20);
        // Planned dates and the clock cannot stale a source context. State/actual marker and every source fact can.
        return new PolicyService.View(body,ConfigRules.tag("close-review:"+id,new byte[0],body,json));
    }
    PolicyService.View close(long id,String reason,String tag,long actor) {
        var before=preflight(id);ConfigRules.match(before.tag(),tag);var context=(Map<?,?>)before.body();
        if(((Number)context.get("blockerCount")).intValue()!=0)throw state();
        var round=r.find(MghpDotXet.class,id);if(round.getThoiDiemDongXet()!=null)return before;
        var fence=r.fence("mghp_dot_xet",id);history.baseline(id,actor,now());
        var values=new LinkedHashMap<String,Object>();values.put("nguoi_dong_xet_id",actor);values.put("thoi_diem_dong_xet",now());
        values.put("sha256_nguon_dong_xet",context.get("sourceSha256"));values.put("thoi_diem_cap_nhat",now());
        r.cas("mghp_dot_xet",id,fence,values);history.changed(id,actor,"CLOSE_REVIEW",reason,now());return preflight(id);
    }
    Object progress(long id) {
        var round=r.find(MghpDotXet.class,id);var plan=RoundTimelineRepository.plan(round.getLichTrinh());
        var warnings=new ArrayList<String>();var stages=new ArrayList<Object>();
        stage(stages,warnings,"submission",RoundTimelineRepository.instant(round.getThoiDiemMo()),RoundTimelineRepository.instant(round.getHanNop()),
            Set.of("closed","completed").contains(Objects.toString(round.getTrangThai(),"")),null);
        stage(stages,warnings,"supplement",plan.supplementPlannedStartAt(),plan.supplementPlannedEndAt(),false,null);
        stage(stages,warnings,"review",plan.reviewPlannedStartAt(),RoundTimelineRepository.instant(round.getHanRaSoat()),round.getThoiDiemDongXet()!=null,RoundTimelineRepository.instant(round.getThoiDiemDongXet()));
        for(String code:List.of("council","signing","publication")) {
            Instant from=code.equals("council")?null:code.equals("signing")?plan.signingPlannedStartAt():plan.publicationPlannedStartAt();
            Instant to=code.equals("council")?null:code.equals("signing")?plan.signingPlannedEndAt():plan.publicationPlannedEndAt();
            var v=new LinkedHashMap<String,Object>();v.put("code",code);v.put("plannedStart",TimelineModels.string(from));v.put("plannedEnd",TimelineModels.string(to));
            if(code.equals("council")) {
                v.put("plannedDate",TimelineModels.string(plan.councilPlannedDate()));
                var actual=RoundTimelineRepository.instant(round.getThoiDiemHoiDongChot());
                var date=plan.councilPlannedDate();var today=LocalDate.ofInstant(clock.instant(),ZoneId.of("Asia/Ho_Chi_Minh"));
                String state=actual!=null?"completed":date==null?"unplanned":today.isBefore(date)?"upcoming":today.isAfter(date)?"overdue":"in_progress";
                v.put("actualTime",TimelineModels.string(actual));v.put("status",state);
                if(state.equals("overdue"))warnings.add("council:DEADLINE_PASSED");
            } else {v.put("actualTime",null);v.put("status","not_implemented");}
            stages.add(v);
            if(to!=null&&clock.instant().isAfter(to))warnings.add(code+":PLANNED_DEADLINE_PASSED");
        }
        if(plan.unplanned())warnings.add("UNPLANNED");
        if(plan.signingPlannedEndAt()!=null&&plan.publicationPlannedStartAt()!=null&&plan.publicationPlannedStartAt().isBefore(plan.signingPlannedEndAt()))warnings.add("PUBLICATION_OVERLAPS_SIGNING");
        if(plan.supplementPlannedEndAt()!=null&&round.getHanRaSoat()!=null&&plan.supplementPlannedEndAt().isAfter(round.getHanRaSoat().toInstant()))warnings.add("SUPPLEMENT_EXCEEDS_REVIEW_PLAN");
        return Map.of("asOf",clock.instant().toString(),"stages",stages,"warnings",warnings);
    }
    private void stage(List<Object> stages,List<String> warnings,String code,Instant start,Instant end,boolean done,Instant actual) {
        String state=done?"completed":start==null&&end==null?"unplanned":end!=null&&clock.instant().isAfter(end)?"overdue":start!=null&&clock.instant().isBefore(start)?"upcoming":"in_progress";
        if(state.equals("overdue"))warnings.add(code+":DEADLINE_PASSED");var m=new LinkedHashMap<String,Object>();
        m.put("code",code);m.put("plannedStart",TimelineModels.string(start));m.put("plannedEnd",TimelineModels.string(end));m.put("actualTime",TimelineModels.string(actual));m.put("status",state);stages.add(m);
    }
    private void editable(MghpDotXet e){if(!Set.of("draft","open","closed").contains(Objects.toString(e.getTrangThai(),"")))throw state();}
    private ConfigFailure state(){return new ConfigFailure(409,"ROUND_STATE_CONFLICT");}
    private OffsetDateTime now(){return clock.instant().atOffset(ZoneOffset.UTC);}
}
