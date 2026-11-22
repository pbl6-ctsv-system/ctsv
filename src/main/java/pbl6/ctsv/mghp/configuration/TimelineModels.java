package pbl6.ctsv.mghp.configuration;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Planned values never double as actual workflow events. */
final class TimelineModels {
    record Schedule(Instant supplementPlannedStartAt, Instant supplementPlannedEndAt,
                    Instant reviewPlannedStartAt, LocalDate councilPlannedDate,
                    Instant signingPlannedStartAt, Instant signingPlannedEndAt,
                    Instant publicationPlannedStartAt, Instant publicationPlannedEndAt) {
        Map<String,Object> view() {
            var m=new LinkedHashMap<String,Object>();
            m.put("supplementPlannedStartAt",string(supplementPlannedStartAt));
            m.put("supplementPlannedEndAt",string(supplementPlannedEndAt));
            m.put("reviewPlannedStartAt",string(reviewPlannedStartAt));
            m.put("councilPlannedDate",string(councilPlannedDate));
            m.put("signingPlannedStartAt",string(signingPlannedStartAt));
            m.put("signingPlannedEndAt",string(signingPlannedEndAt));
            m.put("publicationPlannedStartAt",string(publicationPlannedStartAt));
            m.put("publicationPlannedEndAt",string(publicationPlannedEndAt));
            return m;
        }
        static Schedule empty(){return new Schedule(null,null,null,null,null,null,null,null);}
        boolean unplanned(){return view().values().stream().allMatch(java.util.Objects::isNull);}
    }
    static String string(Object value){return value==null?null:value.toString();}
    private TimelineModels() {}
}
