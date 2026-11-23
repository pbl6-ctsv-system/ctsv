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
    private final ApplicationWorkflow workflow;

    ApplicationReads(IntakeRepository repo,IntakeConfiguration cfg,ObjectMapper json,ApplicationWorkflow workflow) {
        this.repo=repo; this.cfg=cfg; this.json=json; this.workflow=workflow;
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
        return list(actor,round,null,status,page,size);
    }

    Object list(Identity actor,Long round,Long semester,String status,int page,int size) {
        var result=workflow.page(actor.studentId(),round,semester,status,page,size);
        var items=result.facts().stream().map(facts->{
            var a=repo.find(Application.class,((Number)facts.get("id")).longValue());
            var dto=summary(a);dto.putAll(workflow.fields(facts,actor.studentId()!=null));
            if(actor.studentId()==null){
                var student=repo.row("SELECT mssv studentCode,ho_ten fullName FROM dbo.sinh_vien WHERE id=?",a.studentId);
                dto.putAll(map("studentId",a.studentId,"studentCode",student.get("studentCode"),"fullName",student.get("fullName")));
                var latest=repo.versions(a.id).stream().findFirst().orElse(null);
                dto.put("classCode",latest==null?null:latest.classCode);dto.put("policyCode",cfg.policyCode(a.policyVersionId));
            }
            return dto;
        }).toList();
        return Map.of("items",items,"page",page,"size",size,"totalElements",result.count());
    }
    Application scoped(Identity actor,long id) {
        var root=repo.root(id);
        if(actor.studentId()!=null && !actor.studentId().equals(root.studentId)) throw IntakeProblem.missing();
        return root;
    }

    Detail detail(Application root) {
        return detail(root,false);
    }
    Detail detail(Application root,boolean student) {
        var response=summary(root);
        response.putAll(workflow.fields(root.id,student));
        var versions=repo.versions(root.id).stream().map(v -> {
            var dto=version(v);
            dto.put("documents",documents(v.id));
            return dto;
        }).toList();
        response.put("submittedVersions",versions);
        response.put("policyCode",cfg.policyCode(root.policyVersionId));
        var policy=cfg.publicPolicy(root.policyVersionId);
        response.put("policyName",policy.get("name"));
        response.put("documentRequirements",IntakeConfiguration.list(policy,"documentGroups").stream()
            .flatMap(g->IntakeConfiguration.list(g,"documents").stream()).map(d->map("id",d.get("id"),"name",d.get("name"))).toList());
        var round=repo.row("SELECT r.ten roundName,h.id semesterId,h.ma semesterCode,h.ten semesterName FROM dbo.mghp_dot_xet r JOIN dbo.hoc_ky h ON h.id=r.hoc_ky_id WHERE r.id=?",root.roundId);
        response.putAll(wire(round));
        response.put("supplementRequests",repo.children(Supplement.class,"applicationId",root.id,"number desc,id desc").stream()
            .map(s->{var answers=repo.children(Submission.class,"supplementId",s.id,"id");
                return map("id",s.id,"roundNumber",s.number,"status",s.status,"reason",s.reason,"deadline",s.deadline,
                    "requestedAt",s.requestedAt,"respondedAt",s.respondedAt,"closedAt",s.closedAt,
                    "sourceVersionId",s.baseVersionId,"responseVersionId",answers.isEmpty()?null:answers.getFirst().id,
                    "general",repo.children(SupplementItem.class,"supplementId",s.id,"id").stream().allMatch(i->"general".equals(i.type)));}).toList());
        response.put("dispositionHistory",repo.children(ApplicationEvent.class,"applicationId",root.id,"id desc").stream()
            .filter(e->Set.of("approve_application","reject_application").contains(e.action==null?"":e.action))
            .map(e->map("id",e.id,"result",e.current,"reason",e.reason,"at",e.createdAt)).toList());
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
        result.put("claimDisplayNames",map("ethnicityId",version.ethnicityName,"classId",version.classCode,
            "cohortId",version.cohortCode,"managementUnitId",version.managementUnitName));
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
