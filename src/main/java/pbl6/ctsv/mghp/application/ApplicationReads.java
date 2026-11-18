package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pbl6.ctsv.foundation.api.FoundationAccess.Identity;
import pbl6.ctsv.mghp.configuration.IntakeConfiguration;
import static pbl6.ctsv.mghp.application.ApplicationEntities.*;
import static pbl6.ctsv.mghp.application.IntakeMapping.*;

@Service
@ConditionalOnProperty(name="ctsv.mghp.applications.enabled",havingValue="true")
class ApplicationReads {
    record Detail(Map<String,Object> body,String tag) {}
    private final IntakeRepository repo;
    private final IntakeConfiguration cfg;
    private final ObjectMapper json;

    ApplicationReads(IntakeRepository repo,IntakeConfiguration cfg,ObjectMapper json) {
        this.repo=repo; this.cfg=cfg; this.json=json;
    }

    Object form(long student,long roundId) {
        var s=repo.student(student);
        if(repo.rows("""
            SELECT id FROM dbo.mghp_dot_xet WHERE id=? AND trang_thai IN ('open','closed','completed')
            """,roundId).isEmpty()) throw IntakeProblem.conflict("ROUND_UNAVAILABLE");
        var round=cfg.publicRound(roundId);
        var claims=new LinkedHashMap<String,Object>();
        for(var field:IntakeRules.CLAIMS) claims.put(field,wire(s.get(field)));
        var processes=repo.processes(student).stream().map(IntakeMapping::wire).toList();
        long sourced=IntakeRules.CLAIMS.stream().filter(f -> !Set.of("studentCode","fullName","permanentAddress").contains(f)
            && claims.get(f)!=null).count();
        String source=sourced==0 && processes.isEmpty()?"unavailable":sourced==9 && !processes.isEmpty()?"ready":"partial";
        var policies=IntakeConfiguration.list(round,"policyVersions").stream()
            .map(p -> cfg.publicPolicy(Long.parseLong(p.get("policyVersionId").toString()))).toList();
        return map("student",map("id",s.get("id"),"studentCode",s.get("studentCode"),"fullName",s.get("fullName")),
            "round",round,"sourceStatus",source,"sourceObservedAt",s.get("observedAt"),"suggestedClaims",claims,
            "studyProcesses",processes,"policyVersions",policies);
    }

    Object list(Identity actor,Long round,String status,int page,int size) {
        String filter=" where 1=1";
        var params=new LinkedHashMap<String,Object>();
        if(actor.studentId()!=null){filter+=" and a.studentId=:student";params.put("student",actor.studentId());}
        if(round!=null){filter+=" and a.roundId=:round";params.put("round",round);}
        if("UNSPECIFIED".equals(status))filter+=" and a.status is null";
        else if(status!=null){filter+=" and a.status=:status";params.put("status",status);}
        var query=repo.query("from P06Application a"+filter+" order by a.updatedAt desc,a.id desc",Application.class);
        params.forEach(query::setParameter);
        long offset=(long)page*size;
        var items=offset>Integer.MAX_VALUE?List.of():query.setFirstResult((int)offset).setMaxResults(size).getResultList()
            .stream().map(a -> {
                var summary=summary(a);
                if(actor.studentId()==null) {
                    var student=repo.row("SELECT mssv studentCode,ho_ten fullName FROM dbo.sinh_vien WHERE id=?",a.studentId);
                    summary.putAll(map("studentId",a.studentId,"studentCode",student.get("studentCode"),"fullName",student.get("fullName")));
                }
                return summary;
            }).toList();
        var count=repo.query("select count(a) from P06Application a"+filter,Long.class);
        params.forEach(count::setParameter);
        return Map.of("items",items,"page",page,"size",size,"totalElements",count.getSingleResult());
    }

    Application scoped(Identity actor,long id) {
        var root=repo.root(id);
        if(actor.studentId()!=null && !actor.studentId().equals(root.studentId)) throw IntakeProblem.missing();
        return root;
    }

    Detail detail(Application root) {
        var response=summary(root);
        var versions=repo.versions(root.id).stream().map(v -> {
            var dto=version(v);
            dto.put("documents",documents(v.id));
            return dto;
        }).toList();
        response.put("submittedVersions",versions);
        return new Detail(response,tag(root,response));
    }

    List<Map<String,Object>> documents(long versionId) {
        var related=repo.children(RelatedPerson.class,"versionId",versionId,"id");
        var indexes=new HashMap<Long,Integer>();
        for(int i=0;i<related.size();i++) indexes.put(related.get(i).id,i);
        return repo.children(Document.class,"versionId",versionId,"id").stream().map(d -> {
            if(d.personId!=null && !indexes.containsKey(d.personId)) throw new IllegalStateException("Invalid related-person binding");
            return document(d,repo.fileId(d),indexes.get(d.personId));
        }).toList();
    }

    Object declaration(Application root,long id) {
        var version=repo.find(Submission.class,id);
        if(version==null || !root.id.equals(version.applicationId)) throw IntakeProblem.missing();
        var result=version(version);
        result.put("claims",claims(version));
        result.put("statements",statements(version));
        result.put("relatedPeople",repo.children(RelatedPerson.class,"versionId",id,"id").stream().map(IntakeMapping::people).toList());
        result.put("requestedSemesters",repo.children(SemesterRequest.class,"versionId",id,"id").stream()
            .map(row -> map("semesterId",row.semesterId,"policyVersionId",row.policyVersionId,"requestType",row.requestType,"note",row.note)).toList());
        return result;
    }

    void match(Application root,String supplied) {
        matchTag(detail(root).tag(),supplied);
    }

    static void matchTag(String expected,String supplied) {
        if(supplied==null)throw new IntakeProblem(428,"PRECONDITION_REQUIRED","If-Match");
        if(!supplied.matches("\"[A-Za-z0-9_-]+\""))throw IntakeProblem.invalid("If-Match");
        if(!expected.equals(supplied))throw new IntakeProblem(412,"VERSION_CONFLICT","If-Match");
    }

    private String tag(Application root,Object body) {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            digest.update(("HS03-D|"+root.id+"|application/json|").getBytes(StandardCharsets.UTF_8));
            digest.update(root.fence);
            return "\""+Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(json.writeValueAsBytes(body)))+"\"";
        } catch(Exception e){throw new IllegalStateException("Cannot generate application ETag",e);}
    }
}
