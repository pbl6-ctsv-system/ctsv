package pbl6.ctsv.mghp.configuration;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Read model shared by round management and student intake. Called within the intake transaction. */
@Component
@ConditionalOnProperty(name="ctsv.mghp.configuration.enabled",havingValue="true")
public final class IntakeConfiguration {
    private final ConfigurationRepository repository;

    IntakeConfiguration(ConfigurationRepository repository) { this.repository=repository; }

    public void lock(long roundId) {
        lockWrites();
        repository.fence("mghp_dot_xet",roundId);
    }

    /** First MGHP lock after authorization, before operation/root/chain locks in every affected writer. */
    public void lockWrites() {
        try { repository.serializeWrites(); }
        catch(ConfigFailure failure) { throw new pbl6.ctsv.foundation.api.FoundationAccess.Failure(failure.status,failure.code,failure.field); }
    }

    public void requireReviewOpen(long receivingRoundId) {
        var round=repository.find(pbl6.ctsv.mghp.configuration.entity.MghpDotXet.class,receivingRoundId);
        if(round.getThoiDiemDongXet()!=null)
            throw new pbl6.ctsv.foundation.api.FoundationAccess.Failure(409,"ROUND_STATE_CONFLICT","receivingRoundId");
    }

    public Map<String,Object> publicRound(long roundId) { return repository.roundView(roundId,false); }

    public Map<String,Object> policy(long id) { return repository.policyView(id); }

    public String policyCode(long id) { return repository.policyCode(id); }

    public boolean receives(Map<String,Object> round,Instant received) {
        return RoundRules.receives((String)round.get("status"),instant(round.get("opensAt")),
            instant(round.get("submissionDeadline")),received);
    }

    private static Instant instant(Object value) { return value==null ? null : Instant.parse(value.toString()); }

    public Map<String,Object> publicPolicy(long id) {
        var source=policy(id);
        var result=select(source,"id","policyId","version","name","rate","scopeCode","effectiveFrom","effectiveUntil");
        result.put("criteria",list(source,"criteria").stream().map(c -> select(c,
            "id","code","name","required","conditional","applicabilityGuidance","order")).toList());
        result.put("documentGroups",list(source,"documentGroups").stream().map(g -> {
            var group=new LinkedHashMap<>(g);
            group.put("documents",list(g,"documents").stream().map(d -> {
                var doc=new LinkedHashMap<>(d);
                doc.remove("reviewGuidance");
                return doc;
            }).toList());
            return group;
        }).toList());
        return result;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String,Object>> list(Map<String,Object> row,String field) {
        return (List<Map<String,Object>>)row.get(field);
    }

    private static Map<String,Object> select(Map<String,Object> row,String... keys) {
        var view=new LinkedHashMap<String,Object>();
        for(var key:keys) view.put(key,row.get(key));
        return view;
    }
}
