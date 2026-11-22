package pbl6.ctsv.mghp.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import static pbl6.ctsv.mghp.configuration.TimelineModels.*;

final class TimelineRules {
    static final String FIELDS="supplementPlannedStartAt,supplementPlannedEndAt,reviewPlannedStartAt,councilPlannedDate,signingPlannedStartAt,signingPlannedEndAt,publicationPlannedStartAt,publicationPlannedEndAt";
    static Schedule parse(JsonNode node) {
        ConfigRules.shape(node,FIELDS);
        var plan=new Schedule(instant(node,"supplementPlannedStartAt"),instant(node,"supplementPlannedEndAt"),
            instant(node,"reviewPlannedStartAt"),ConfigRules.date(node,"councilPlannedDate"),
            instant(node,"signingPlannedStartAt"),instant(node,"signingPlannedEndAt"),
            instant(node,"publicationPlannedStartAt"),instant(node,"publicationPlannedEndAt"));
        pair(plan.supplementPlannedStartAt(),plan.supplementPlannedEndAt(),"supplementPlannedEndAt");
        pair(plan.signingPlannedStartAt(),plan.signingPlannedEndAt(),"signingPlannedEndAt");
        pair(plan.publicationPlannedStartAt(),plan.publicationPlannedEndAt(),"publicationPlannedEndAt");
        return plan;
    }
    static Instant instant(JsonNode node,String field) {
        var value=ConfigRules.instant(node,field);
        if(value!=null&&value.getNano()%100!=0)throw ConfigFailure.invalid(field);
        return value;
    }
    static void pair(Instant from,Instant to,String field){
        if(from!=null&&to!=null&&from.isAfter(to))throw ConfigFailure.invalid(field);
    }
    private TimelineRules() {}
}
