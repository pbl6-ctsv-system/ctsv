package pbl6.ctsv.hocbong.kkht.round;

import java.util.List;

final class ScopeDtos {
    record Group(String cohortId,String cohortCode,String majorId,String majorCode,String majorName,int studentCount) {}
    record Catalog(String catalogRevision,List<Group> items,int page,int size,long totalElements) {}
    record Version(String id,int revision,String mode,String catalogRevision,List<Group> groups,String createdAt,String createdBy) {}
    record View(String roundId,String roundEtag,Version scope) {}
    record Created(RoundDtos.View view,boolean replayed) {}
    record CreationLookup(String requestId,String roundId) {}
    private ScopeDtos() {}
}
