package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pbl6.ctsv.foundation.api.FoundationAccess.Identity;
import pbl6.ctsv.mghp.configuration.IntakeConfiguration;
import static pbl6.ctsv.mghp.application.ApplicationEntities.*;
import static pbl6.ctsv.mghp.application.ApplicationReads.matchTag;
import static pbl6.ctsv.mghp.application.IntakeMapping.*;
import static pbl6.ctsv.mghp.application.IntakeRules.*;
import static pbl6.ctsv.mghp.application.IntakeProblem.*;

@Service
@ConditionalOnProperty(name="ctsv.mghp.applications.enabled",havingValue="true")
class SupplementService {
    private static final Set<String> UPDATES=Set.of("initial_supplement","periodic_update","information_change","legal_refresh");
    private static final Set<String> REVIEW_RESULTS=Set.of("satisfied","unsatisfied","needs_verification","not_applicable");
    private static final Set<String> INITIAL_ROOTS=Set.of("submitted","processing","supplementing");
    private final IntakeRepository repo;
    private final ApplicationReads reads;
    private final ApplicationService submissions;
    private final IntakeConfiguration configuration;
    private final InboxService inbox;
    private final ObjectMapper json;

    SupplementService(IntakeRepository repo,ApplicationReads reads,ApplicationService submissions,
                      IntakeConfiguration configuration,InboxService inbox,ObjectMapper json) {
        this.repo=repo; this.reads=reads; this.submissions=submissions;
        this.configuration=configuration; this.inbox=inbox; this.json=json;
    }

    private record Locked(Application root,Supplement round) {}

    ApplicationReads.Detail get(Identity actor,boolean staff,long id,boolean context) {
        var locked=lock(actor,staff,id);
        return context?context(locked,staff):view(locked.round,staff);
    }

    ApplicationReads.Detail create(Identity actor,long rootId,JsonNode input,String tag,Instant received) {
        shape(input,List.of("sourceVersionId","receivingRoundId","benefitId","updateType","reason","deadline","items"));
        long versionId=id(input.get("sourceVersionId"),"sourceVersionId");
        long receiving=id(input.get("receivingRoundId"),"receivingRoundId");
        Long benefitId=nullableId(input,"benefitId");
        String updateType=choice(input,"updateType",UPDATES);
        String reason=reason(input);
        OffsetDateTime deadline=deadline(input,received);
        var round=receivingRound(receiving,"SUPPLEMENT_NOT_ALLOWED");
        var root=repo.root(rootId);
        reads.match(root,tag);
        var former=repo.children(Supplement.class,"applicationId",rootId,"number desc");
        var versions=repo.versions(rootId);
        boolean initial="initial_supplement".equals(updateType);
        if(versions.isEmpty() || !Objects.equals(versions.getFirst().id,versionId)
            || former.stream().anyMatch(s -> s.status==null || SupplementRules.ACTIVE.contains(s.status))
            || "withdrawn".equals(root.status) || instant(round.get("opensAt"))==null
            || deadline.toInstant().isBefore(instant(round.get("opensAt"))))
            throw conflict("SUPPLEMENT_NOT_ALLOWED");
        if(initial) {
            if(benefitId!=null || !root.roundId.equals(receiving) || root.status==null
                || !INITIAL_ROOTS.contains(root.status) || repo.confirmed(root))
                throw conflict("SUPPLEMENT_NOT_ALLOWED");
        } else if(benefitId==null || root.roundId.equals(receiving) || !matchesBenefit(root,benefitId)
            || IntakeConfiguration.list(round,"policyVersions").stream()
                .noneMatch(row -> Objects.equals(configuration.policy(Long.parseLong(row.get("policyVersionId").toString()))
                    .get("policyId"),root.policyId.toString()))) {
            throw conflict("SUPPLEMENT_NOT_ALLOWED");
        }
        var items=array(input.get("items"),"items",true).stream().map(n -> item(root,versions.getFirst(),n)).toList();
        var now=received.atOffset(ZoneOffset.UTC);
        var supplement=new Supplement();
        supplement.applicationId=root.id; supplement.baseVersionId=versionId; supplement.receivingRoundId=receiving;
        supplement.benefitId=benefitId; supplement.updateType=updateType; supplement.status="open";
        supplement.number=former.isEmpty()?1:Math.addExact(former.getFirst().number,1);
        supplement.reason=reason; supplement.deadline=deadline; supplement.requestedBy=actor.userId(); supplement.requestedAt=now;
        repo.save(supplement);
        for(var item:items) { item.supplementId=supplement.id; repo.save(item); }
        repo.change(root,initial?"supplementing":root.status,now,null);
        audit(root,supplement,null,actor,"request_supplement",null,"open",reason,now,null,deadline);
        var recipient=repo.row("SELECT nguoi_dung_id FROM dbo.sinh_vien WHERE id=?",root.studentId).get("nguoi_dung_id");
        if(recipient instanceof Long user) inbox.publish(root,actor.userId(),user,"Có yêu cầu bổ sung hồ sơ",now,supplement);
        return view(supplement,true);
    }

    Object extend(Identity actor,long id,JsonNode input,String tag,Instant received) {
        shape(input,List.of("deadline","reason"));
        var newDeadline=deadline(input,received);
        var reason=reason(input);
        var locked=lock(actor,true,id);
        var row=locked.round;
        matchTag(view(row,true).tag(),tag);
        var old=row.deadline;
        var previous=row.status;
        if(previous==null || !SupplementRules.ACTIVE.contains(previous)
            || old==null || !newDeadline.isAfter(old)) throw conflict("SUPPLEMENT_STATE_CONFLICT");
        String state="expired".equals(previous)?"open":previous;
        repo.changeSupplement(row,state,newDeadline,row.respondedAt,row.closedAt);
        audit(locked.root,row,null,actor,"extend_supplement",previous,state,reason,
            received.atOffset(ZoneOffset.UTC),old,newDeadline);
        return view(row,true).body();
    }

    ApplicationService.Accepted respond(Identity actor,long id,JsonNode input,String tag,Instant received) {
        shape(input,ApplicationService.FIELDS);
        long roundId=id(input.get("roundId"),"roundId");
        long process=id(input.get("studyProcessId"),"studyProcessId");
        long policyVersion=id(input.get("policyVersionId"),"policyVersionId");
        var operation=operation(input.get("operationId"));
        var hash=submissions.hash("supplement:"+id,input);
        var target=repo.rows("""
            SELECT s.ho_so_id,s.dot_tiep_nhan_id
            FROM dbo.mghp_yeu_cau_bo_sung s JOIN dbo.mghp_ho_so h ON h.id=s.ho_so_id
            WHERE s.id=? AND h.sinh_vien_id=?
            """,id,actor.studentId());
        if(target.isEmpty())throw missing();
        repo.lockOperation(operation);
        var replay=repo.replay(operation);
        if(replay!=null) {
            if(!Objects.equals(replay.supplementId,id) || !hash.equals(replay.hash))
                throw conflict("SUBMISSION_KEY_CONFLICT");
            return new ApplicationService.Accepted(reads.scoped(actor,replay.applicationId),replay,true);
        }
        if(!Objects.equals(target.getFirst().get("dot_tiep_nhan_id"),roundId))throw conflict("APPLICATION_IDENTITY_CONFLICT");
        var currentRound=receivingRound(roundId,"SUBMISSION_NOT_ALLOWED");
        var locked=lock(actor,false,id);
        var row=locked.round;
        var root=locked.root;
        matchTag(context(locked,false).tag(),tag);
        if(!SupplementRules.acceptsResponse(row.status,(String)currentRound.get("status"),
            instant(currentRound.get("opensAt")),row.deadline==null?null:row.deadline.toInstant(),received)
            || "withdrawn".equals(root.status) || row.updateType==null) throw conflict("SUBMISSION_NOT_ALLOWED");
        if(!root.processId.equals(process))throw conflict("APPLICATION_IDENTITY_CONFLICT");
        boolean initial="initial_supplement".equals(row.updateType);
        if(initial) {
            if(!root.policyVersionId.equals(policyVersion))throw conflict("APPLICATION_IDENTITY_CONFLICT");
        } else {
            if(IntakeConfiguration.list(currentRound,"policyVersions").stream()
                .noneMatch(p -> Long.toString(policyVersion).equals(p.get("policyVersionId"))))
                throw conflict("SUBMISSION_NOT_ALLOWED");
            if(!root.policyId.toString().equals(configuration.policy(policyVersion).get("policyId")))
                throw conflict("APPLICATION_IDENTITY_CONFLICT");
        }
        var result=submissions.persistSubmission(actor,root,roundId,process,policyVersion,input,operation,hash,received,row);
        var now=received.atOffset(ZoneOffset.UTC);
        repo.change(root,root.status,now,null);
        repo.changeSupplement(row,"responded",row.deadline,now,null);
        audit(root,row,result.version(),actor,"respond_supplement","open","responded",null,now,null,null);
        return result;
    }

    Object review(Identity actor,long itemId,JsonNode input,String tag,Instant received) {
        shape(input,List.of("responseDocumentId","result","reason"));
        var reason=reason(input);
        var documentId=nullableId(input,"responseDocumentId");
        String result=choice(input,"result",REVIEW_RESULTS);
        long supplementId=(Long)repo.row("SELECT yeu_cau_bo_sung_id FROM dbo.mghp_noi_dung_bo_sung WHERE id=?",itemId)
            .get("yeu_cau_bo_sung_id");
        var locked=lock(actor,true,supplementId);
        var row=locked.round;
        matchTag(view(row,true).tag(),tag);
        if(!"responded".equals(row.status))throw conflict("SUPPLEMENT_STATE_CONFLICT");
        var item=repo.find(SupplementItem.class,itemId);
        if(documentId!=null) {
            var document=repo.find(Document.class,documentId);
            var reply=response(row);
            if(document==null || reply==null || !reply.id.equals(document.versionId)
                || item.requirementId==null || !item.requirementId.equals(document.requirementId))
                throw conflict("SUPPLEMENT_STATE_CONFLICT");
        }
        var now=received.atOffset(ZoneOffset.UTC);
        item.responseDocumentId=documentId; item.result=result; item.reviewerId=actor.userId(); item.reviewedAt=now;
        repo.changeSupplement(row,row.status,row.deadline,row.respondedAt,row.closedAt);
        audit(locked.root,row,null,actor,"review_supplement_item:"+item.id,null,result,reason,now,null,null);
        return view(row,true).body();
    }

    ApplicationReads.Detail close(Identity actor,long id,String reason,String tag,boolean complete,Instant received) {
        var locked=lock(actor,true,id);
        var row=locked.round;
        matchTag(view(row,true).tag(),tag);
        String previous=row.status;
        if(!complete && "cancelled".equals(previous))return view(row,true);
        if(complete) {
            if(!SupplementRules.ready(previous,items(row).stream().map(i -> i.result).toList()))
                throw conflict("SUPPLEMENT_NOT_READY");
        } else if(previous==null || !SupplementRules.ACTIVE.contains(previous)) {
            throw conflict("SUPPLEMENT_STATE_CONFLICT");
        }
        String state=complete?"completed":"cancelled";
        var now=received.atOffset(ZoneOffset.UTC);
        if("supplementing".equals(locked.root.status))repo.change(locked.root,"processing",now,null);
        repo.changeSupplement(row,state,row.deadline,row.respondedAt,now);
        audit(locked.root,row,null,actor,complete?"complete_supplement":"cancel_supplement",previous,state,reason,now,null,null);
        return view(row,true);
    }

    private Locked lock(Identity actor,boolean staff,long id) {
        long rootId=(Long)repo.row("SELECT ho_so_id FROM dbo.mghp_yeu_cau_bo_sung WHERE id=?",id).get("ho_so_id");
        var root=staff?repo.root(rootId):reads.scoped(actor,rootId);
        return new Locked(root,repo.supplement(id));
    }

    private Map<String,Object> receivingRound(long id,String code) {
        if(!exists("SELECT id FROM dbo.mghp_dot_xet WHERE id=?",id))throw conflict(code);
        configuration.lock(id);
        var round=configuration.publicRound(id);
        if(!"open".equals(round.get("status")) && !"closed".equals(round.get("status")))throw conflict(code);
        return round;
    }

    private boolean matchesBenefit(Application root,long benefitId) {
        return exists("""
            SELECT q.id FROM dbo.mghp_quyen_huong q JOIN dbo.mghp_phien_ban_ho_so v ON v.id=q.phien_ban_ho_so_nguon_id
            WHERE q.id=? AND q.sinh_vien_id=? AND q.qua_trinh_hoc_id=? AND q.chinh_sach_id=? AND v.ho_so_id=?
            """,benefitId,root.studentId,root.processId,root.policyId,root.id);
    }

    private SupplementItem item(Application root,Submission base,JsonNode input) {
        shape(input,List.of("requirementId","documentId","semesterId","evaluationId","declarationField","type","reason"));
        var item=new SupplementItem();
        item.requirementId=nullableId(input,"requirementId"); item.documentId=nullableId(input,"documentId");
        item.semesterId=nullableId(input,"semesterId"); item.evaluationId=nullableId(input,"evaluationId");
        item.declarationField=text(input.get("declarationField"),"declarationField",128,false);
        item.type=choice(input,"type",Set.of("document","declaration","semester","evaluation")); item.reason=reason(input);
        if(switch(item.type) {
            case "document" -> item.requirementId==null;
            case "declaration" -> item.declarationField==null;
            case "semester" -> item.semesterId==null;
            default -> item.evaluationId==null;
        })throw invalid("items");
        if(item.declarationField!=null && !CLAIMS.contains(item.declarationField))throw invalid("declarationField");
        var basePolicy=base.policyVersionId==null?root.policyVersionId:base.policyVersionId;
        if(item.requirementId!=null && IntakeConfiguration.list(configuration.policy(basePolicy),"documentGroups").stream()
            .flatMap(group -> IntakeConfiguration.list(group,"documents").stream())
            .noneMatch(doc -> item.requirementId.toString().equals(doc.get("id"))))throw conflict("SUPPLEMENT_NOT_ALLOWED");
        if(item.documentId!=null && !exists("SELECT id FROM dbo.mghp_giay_to_ho_so WHERE id=? AND phien_ban_ho_so_id=? AND giay_to_bat_buoc_id=?",
            item.documentId,base.id,item.requirementId))throw conflict("SUPPLEMENT_NOT_ALLOWED");
        if(item.semesterId!=null && !exists("SELECT id FROM dbo.hoc_ky WHERE id=?",item.semesterId))throw invalid("semesterId");
        if(item.evaluationId!=null && !exists("""
            SELECT e.id FROM dbo.mghp_danh_gia_dieu_kien e
            WHERE e.id=? AND e.sinh_vien_id=? AND e.qua_trinh_hoc_id=?
              AND (e.ho_so_nguon_id=? OR e.phien_ban_ho_so_nguon_id IN (SELECT id FROM dbo.mghp_phien_ban_ho_so WHERE ho_so_id=?))
            """,item.evaluationId,root.studentId,root.processId,root.id,root.id))throw conflict("SUPPLEMENT_NOT_ALLOWED");
        return item;
    }

    private boolean exists(String sql,Object... parameters) { return !repo.rows(sql,parameters).isEmpty(); }
    private List<SupplementItem> items(Supplement row) { return repo.children(SupplementItem.class,"supplementId",row.id,"id"); }
    private Submission response(Supplement row) {
        return repo.children(Submission.class,"supplementId",row.id,"id").stream().findFirst().orElse(null);
    }

    private ApplicationReads.Detail view(Supplement round,boolean staff) {
        var body=map("id",round.id,"applicationId",round.applicationId,"sourceVersionId",round.baseVersionId,
            "receivingRoundId",round.receivingRoundId,"benefitId",round.benefitId,"roundNumber",round.number,
            "status",round.status,"reason",round.reason,"deadline",round.deadline,"respondedAt",round.respondedAt,
            "closedAt",round.closedAt,"items",items(round).stream().map(i -> map("id",i.id,"requirementId",i.requirementId,
                "documentId",i.documentId,"semesterId",i.semesterId,"evaluationId",i.evaluationId,
                "declarationField",i.declarationField,"type",i.type,"reason",i.reason,"responseDocumentId",i.responseDocumentId,
                "reviewResult",i.result,"reviewedAt",i.reviewedAt)).toList());
        return new ApplicationReads.Detail(body,tag("BS07",staff,round,null,body));
    }

    private ApplicationReads.Detail context(Locked locked,boolean staff) {
        var row=locked.round;
        var latest=response(row);
        var body=map("application",summary(locked.root),"supplement",view(row,staff).body(),
            "baseDeclaration",reads.declaration(locked.root,row.baseVersionId),"baseDocuments",reads.documents(row.baseVersionId),
            "latestResponse",latest==null?null:acknowledgement(locked.root,latest));
        return new ApplicationReads.Detail(body,tag("BS03-R",staff,row,locked.root,body));
    }

    private String tag(String kind,boolean staff,Supplement row,Application root,Object body) {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            digest.update((kind+"|"+row.id+"|"+staff+"|application/json|").getBytes(StandardCharsets.UTF_8));
            digest.update(row.fence);
            if(root!=null)digest.update(root.fence);
            return "\""+Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(json.writeValueAsBytes(body)))+"\"";
        } catch(java.io.IOException|java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Cannot create supplement ETag",failure);
        }
    }

    private void audit(Application root,Supplement row,Submission version,Identity actor,String action,String previous,
                       String state,String reason,OffsetDateTime now,OffsetDateTime oldDeadline,OffsetDateTime newDeadline) {
        var audit=new ApplicationEvent();
        audit.applicationId=root.id; audit.supplementId=row.id; audit.versionId=version==null?null:version.id;
        audit.actorId=actor.userId(); audit.actorType="user"; audit.action=action; audit.previous=previous; audit.current=state;
        audit.reason=reason; audit.createdAt=now; audit.requestId=version==null?UUID.randomUUID():version.operationId;
        audit.oldDeadline=oldDeadline; audit.newDeadline=newDeadline;
        repo.save(audit);
    }

    static String reason(JsonNode n) { return text(n.get("reason"),"reason",4000,true); }

    static String choice(JsonNode input,String key,Set<String> allowed) {
        String value=text(input.get(key),key,128,true);
        if(!allowed.contains(value))throw invalid(key);
        return value;
    }

    private static OffsetDateTime deadline(JsonNode input,Instant received) {
        try {
            var instant=Instant.parse(text(input.get("deadline"),"deadline",64,true));
            if(!instant.isAfter(received) || instant.getNano()%100!=0)throw invalid("deadline");
            return instant.atOffset(ZoneOffset.UTC);
        } catch(DateTimeException failure) { throw invalid("deadline"); }
    }

    private static Instant instant(Object value) { return value==null?null:Instant.parse(value.toString()); }
}
