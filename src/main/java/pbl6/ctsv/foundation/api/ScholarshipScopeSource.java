package pbl6.ctsv.foundation.api;

import java.util.List;

/** Read-only fixed cohort/major catalog. Caller owns a transaction; provider holds source locks until commit. */
public interface ScholarshipScopeSource {
    record Group(long cohortId,String cohortCode,long majorId,String majorCode,String majorName,int studentCount) {}
    record Catalog(String revision,List<Group> groups) {
        public Catalog { groups=List.copyOf(groups); }
    }
    Catalog catalog(long sourceSemesterId);
}
