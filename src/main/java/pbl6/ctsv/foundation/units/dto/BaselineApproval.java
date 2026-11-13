package pbl6.ctsv.foundation.units.dto;
import java.time.Instant;
import java.util.*;
/** Explicit internal operator confirmation, never accepted from a public HTTP endpoint. */
public record BaselineApproval(long sourceSystemId,UUID previousEpoch,UUID sourceEpoch,
        Map<String,String> sourceIdentities,Map<String,Long> targetMappings,
        String approvedBy,String reason,Instant approvedAt) {
    public BaselineApproval {
        if(sourceSystemId<1||sourceEpoch==null||sourceIdentities==null||targetMappings==null
                ||sourceIdentities.size()>10000||targetMappings.size()>10000
                ||approvedBy==null||approvedBy.isBlank()||reason==null||reason.isBlank()||approvedAt==null)
            throw new IllegalArgumentException("Scoped baseline confirmation is incomplete");
        sourceIdentities=Map.copyOf(sourceIdentities);targetMappings=Map.copyOf(targetMappings);
    }
}

