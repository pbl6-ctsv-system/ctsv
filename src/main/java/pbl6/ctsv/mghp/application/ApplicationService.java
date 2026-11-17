package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pbl6.ctsv.foundation.api.FoundationAccess.Identity;
import pbl6.ctsv.mghp.configuration.IntakeConfiguration;
import static pbl6.ctsv.mghp.application.ApplicationEntities.*;
import static pbl6.ctsv.mghp.application.IntakeMapping.*;
import static pbl6.ctsv.mghp.application.IntakeRules.*;
import static pbl6.ctsv.mghp.application.IntakeProblem.invalid;

@Service
@ConditionalOnProperty(name="ctsv.mghp.applications.enabled",havingValue="true")
class ApplicationService {
    record Accepted(Application root,Submission version,boolean replay) {}
    private static final List<String> FIELDS=List.of("operationId","roundId","studyProcessId","policyVersionId",
        "claims","requestedSemesterIds","statements","relatedPeople","documents");
    private final IntakeRepository repo;
    private final ApplicationReads reads;
    private final IntakeConfiguration cfg;
    private final ObjectMapper json;
    private final Clock clock;
    private final InboxService inbox;

    ApplicationService(IntakeRepository repo,ApplicationReads reads,IntakeConfiguration cfg,ObjectMapper json,Clock clock,InboxService inbox) {
        this.repo=repo; this.reads=reads; this.cfg=cfg; this.json=json; this.clock=clock; this.inbox=inbox;
    }

    Instant received() { return clock.instant(); }

    Accepted submit(Identity actor,Long target,JsonNode body,String tag,Instant received) {
        shape(body,FIELDS);
        long round=id(body.get("roundId"),"roundId");
        long process=id(body.get("studyProcessId"),"studyProcessId");
        long policyVersion=id(body.get("policyVersionId"),"policyVersionId");
        UUID operation=operation(body.get("operationId"));
        String hash=hash(target,body);
        long student=actor.studentId();
        repo.lockOperation(operation);
        if(target!=null) {
            var scope=repo.row("SELECT sinh_vien_id, dot_xet_id FROM dbo.mghp_ho_so WHERE id=?",target);
            if(!Objects.equals(scope.get("sinh_vien_id"),student)) throw IntakeProblem.missing();
        }
        var previous=repo.replay(operation);
        if(previous!=null) {
            var root=reads.scoped(actor,previous.applicationId);
            boolean sameTarget=target==null?"initial".equals(previous.type):
                target.equals(root.id)&&"resubmission".equals(previous.type);
            if(!sameTarget || !hash.equals(previous.hash)) throw IntakeProblem.conflict("SUBMISSION_KEY_CONFLICT");
            return new Accepted(root,previous,true);
        }
        if(target!=null) {
            var scope=repo.row("SELECT dot_xet_id,qua_trinh_hoc_id,phien_ban_chinh_sach_id FROM dbo.mghp_ho_so WHERE id=?",target);
            if(!Objects.equals(scope.get("dot_xet_id"),round)||!Objects.equals(scope.get("qua_trinh_hoc_id"),process)
                ||!Objects.equals(scope.get("phien_ban_chinh_sach_id"),policyVersion))
                throw IntakeProblem.conflict("APPLICATION_IDENTITY_CONFLICT");
        }
        cfg.lock(round);
        var currentRound=availableRound(round);
        if(!cfg.receives(currentRound,received)) throw IntakeProblem.conflict("SUBMISSION_NOT_ALLOWED");
        if(IntakeConfiguration.list(currentRound,"policyVersions").stream()
            .noneMatch(p -> Long.toString(policyVersion).equals(p.get("policyVersionId")))) {
            throw IntakeProblem.conflict("SUBMISSION_NOT_ALLOWED");
        }
        var policy=cfg.policy(policyVersion);
        long policyId=Long.parseLong(policy.get("policyId").toString());
        repo.processes(student).stream().filter(p -> Objects.equals(p.get("id"),process)).findFirst()
            .orElseThrow(IntakeProblem::missing);
        var root=repo.root(target,round,process,policyId);
        if(target==null && root!=null) throw IntakeProblem.conflict("APPLICATION_EXISTS");
        if(target!=null) {
            if(!root.studentId.equals(student)) throw IntakeProblem.missing();
            reads.match(root,tag);
            if(!root.roundId.equals(round)||!root.processId.equals(process)||!root.policyVersionId.equals(policyVersion)
                ||!"reopened".equals(root.status)||repo.confirmed(root)) throw IntakeProblem.conflict("SUBMISSION_NOT_ALLOWED");
        }
        var v=new Submission();
        claims(v,body.get("claims"));
        statements(v,body.get("statements"));
        if(!Boolean.TRUE.equals(v.declarationConfirmed)) throw invalid("declarationConfirmed");
        if(Boolean.TRUE.equals(policy.get("firstDegreeRequired")) && v.firstDegree==null) throw invalid("firstDegree");
        var source=repo.student(student);
        if(!v.studentCode.equals(source.get("studentCode")))
            throw new IntakeProblem(400,"STUDENT_IDENTITY_MISMATCH","studentCode");
        var names=repo.rows("""
            SELECT e.ten ethnicity,l.ma classCode,k.ma cohortCode,d.ten unit
            FROM dbo.dan_toc e CROSS JOIN dbo.lop_sinh_hoat l CROSS JOIN dbo.khoa_hoc k CROSS JOIN dbo.don_vi d
            WHERE e.id=? AND l.id=? AND k.id=? AND d.id=?
            """,v.ethnicityId,v.classId,v.cohortId,v.managementUnitId);
        if(names.isEmpty()) throw invalid("claims");
        var name=names.getFirst();
        v.ethnicityName=(String)name.get("ethnicity"); v.classCode=(String)name.get("classCode");
        v.cohortCode=(String)name.get("cohortCode"); v.managementUnitName=(String)name.get("unit");
        var people=array(body.get("relatedPeople"),"relatedPeople",false).stream().map(n -> {
            var row=new RelatedPerson();
            people(row,n);
            if(row.statusFrom!=null && row.statusUntil!=null && row.statusUntil.isBefore(row.statusFrom))
                throw invalid("statusUntil");
            if(row.ethnicityId!=null && repo.rows("SELECT id FROM dbo.dan_toc WHERE id=?",row.ethnicityId).isEmpty())
                throw invalid("ethnicityId");
            if(row.workplaceUnitId!=null && repo.rows("SELECT id FROM dbo.don_vi WHERE id=?",row.workplaceUnitId).isEmpty())
                throw invalid("workplaceUnitId");
            return row;
        }).toList();
        var semesters=new LinkedHashSet<Long>();
        for(var n:array(body.get("requestedSemesterIds"),"requestedSemesterIds",true)) {
            long semester=id(n,"requestedSemesterIds");
            if(!semesters.add(semester) || repo.rows("SELECT id FROM dbo.hoc_ky WHERE id=?",semester).isEmpty())
                throw invalid("requestedSemesterIds");
        }
        var docs=parseDocuments(body.get("documents"),policy,people,cfg.policyCode(policyVersion));
        var now=received.atOffset(ZoneOffset.UTC);
        if(root==null) {
            root=new Application();
            root.code="MGHP-"+UUID.randomUUID();
            root.studentId=student; root.roundId=round; root.processId=process; root.policyId=policyId;
            root.policyVersionId=policyVersion; root.status="submitted"; root.source="portal";
            root.submittedAt=now; root.createdAt=now; root.updatedAt=now; root.createdBy=actor.userId();
            repo.save(root);
        }
        v.applicationId=root.id; v.operationId=operation; v.hash=hash; v.actorId=actor.userId();
        v.type=target==null?"initial":"resubmission";
        v.revision=target==null?1:repo.versions(root.id).stream().findFirst().orElseThrow().revision+1;
        v.submittedAt=now; v.createdAt=now; v.roundId=round; v.policyVersionId=policyVersion;
        v.sourceAt=(OffsetDateTime)source.get("observedAt");
        var comparison=compare(v,source);
        repo.save(v);
        for(var person:people){person.versionId=v.id; repo.save(person);}
        var files=new HashMap<Long,String>();
        for(long file:docs.stream().map(Attached::fileId).distinct().sorted().toList()) {
            files.put(file,repo.fileHash(actor.userId(),file,root));
        }
        for(var entry:docs) {
            Document document=entry.document();
            document.versionId=v.id; document.actorId=actor.userId();
            document.submittedAt=now; document.createdAt=now; document.hash=files.get(entry.fileId());
            document.personId=entry.personIndex()==null?null:people.get(entry.personIndex()).id;
            repo.save(document);
            var link=new EvidenceLink();
            link.documentId=document.id; link.fileId=entry.fileId();
            repo.save(link);
        }
        for(long semester:semesters) {
            var row=new SemesterRequest();
            row.versionId=v.id; row.semesterId=semester; row.policyVersionId=policyVersion;
            repo.save(row);
        }
        for(var row:comparison){row.versionId=v.id; repo.save(row);}
        if(target!=null)repo.change(root,"submitted",now,null);
        event(root,v,actor.userId(),v.type,target==null?null:"reopened","submitted",null,now,null);
        inbox.publish(root,actor.userId(),actor.userId(),"Đã tiếp nhận hồ sơ",now);
        repo.flush();
        return new Accepted(root,v,false);
    }

    Object transition(Identity actor,long id,String tag,String reason,boolean reopen,Instant received) {
        if(reopen)cfg.lock((Long)repo.row("SELECT dot_xet_id FROM dbo.mghp_ho_so WHERE id=?",id).get("dot_xet_id"));
        var root=reopen?repo.root(id):reads.scoped(actor,id);
        reads.match(root,tag);
        var now=received.atOffset(ZoneOffset.UTC);
        String prior=root.status;
        if(!reopen && "withdrawn".equals(prior)) return summary(root);
        String rejection=reopen?"APPLICATION_NOT_REOPENABLE":"APPLICATION_NOT_WITHDRAWABLE";
        if(repo.confirmed(root)) throw IntakeProblem.conflict(rejection);
        String next=reopen?"reopened":"withdrawn";
        if(reopen) {
            if(!"withdrawn".equals(prior) || !cfg.receives(availableRound(root.roundId,rejection),received))
                throw IntakeProblem.conflict(rejection);
        } else {
            if(prior==null || !Set.of("submitted","processing","supplementing","reopened").contains(prior))
                throw IntakeProblem.conflict(rejection);
            for(var supplement:repo.children(Supplement.class,"applicationId",root.id,"id")) {
                if(supplement.status!=null && Set.of("completed","cancelled").contains(supplement.status)) continue;
                supplement.status="cancelled"; supplement.closedAt=now;
                event(root,null,actor.userId(),"cancel_supplement",prior,next,reason,now,supplement.id);
            }
        }
        repo.change(root,next,now,reason);
        event(root,null,actor.userId(),reopen?"reopen":"withdraw",prior,next,reason,now,null);
        var recipient=(Long)repo.row("SELECT nguoi_dung_id FROM dbo.sinh_vien WHERE id=?",root.studentId).get("nguoi_dung_id");
        if(recipient!=null)inbox.publish(root,actor.userId(),recipient,reopen?"Hồ sơ đã được mở lại":"Hồ sơ đã được rút",now);
        return summary(root);
    }

    private Map<String,Object> availableRound(long id) { return availableRound(id,"SUBMISSION_NOT_ALLOWED"); }

    private Map<String,Object> availableRound(long id,String code) {
        if(repo.rows("SELECT id FROM dbo.mghp_dot_xet WHERE id=? AND trang_thai IN ('open','closed','completed')",id).isEmpty())
            throw IntakeProblem.conflict(code);
        return cfg.publicRound(id);
    }

    private record Attached(Document document,long fileId,Integer personIndex) {}

    private List<Attached> parseDocuments(JsonNode nodes,Map<String,Object> policy,List<RelatedPerson> people,String policyCode) {
        var groups=IntakeConfiguration.list(policy,"documentGroups");
        var requirements=new HashMap<Long,Map<String,Object>>();
        for(var group:groups) for(var item:IntakeConfiguration.list(group,"documents"))
            requirements.put(Long.parseLong(item.get("id").toString()),item);
        var seen=new HashSet<Long>();
        var residenceSubjects=new HashSet<String>();
        List<Attached> result=new ArrayList<>();
        for(var n:array(nodes,"documents",true)) {
            shape(n,DOCUMENT);
            var doc=new Document();
            doc.requirementId=id(n.get("requirementId"),"requirementId");
            var requirement=requirements.get(doc.requirementId);
            if(requirement==null || !seen.add(doc.requirementId))throw invalid("requirementId");
            doc.code=(String)requirement.get("code");
            doc.documentNumber=text(n.get("documentNumber"),"documentNumber",128,false);
            doc.issuer=text(n.get("issuer"),"issuer",512,false);
            doc.issuedOn=date(n.get("issuedOn"),"issuedOn",false);
            doc.expiresOn=date(n.get("expiresOn"),"expiresOn",false);
            if(doc.issuedOn!=null && doc.expiresOn!=null && doc.expiresOn.isBefore(doc.issuedOn)) throw invalid("expiresOn");
            var index=n.get("relatedPersonIndex");
            Integer person=null;
            if(!index.isNull()) {
                if(!index.isIntegralNumber()||!index.canConvertToInt()||index.intValue()<0||index.intValue()>=people.size())
                    throw invalid("relatedPersonIndex");
                person=index.intValue();
            }
            String subject=(String)requirement.get("evidenceSubject");
            if("sinh_vien".equals(subject) && person!=null) throw invalid("relatedPersonIndex");
            if("cha_hoac_me".equals(subject) && (person==null || !isParent(people.get(person).relation)))
                throw invalid("relatedPersonIndex");
            if("xac_nhan_cu_tru".equals(requirement.get("type")) && subject!=null)residenceSubjects.add(subject);
            result.add(new Attached(doc,id(n.get("fileId"),"fileId"),person));
        }
        for(var group:groups) {
            long covered=IntakeConfiguration.list(group,"documents").stream()
                .filter(d->seen.contains(Long.parseLong(d.get("id").toString()))).count();
            if(!(group.get("minimumRequired") instanceof Number min) || covered<min.intValue())
                throw invalid("documents");
        }
        if("M6".equals(policyCode)&&!residenceSubjects.containsAll(Set.of("sinh_vien","cha_hoac_me")))
            throw invalid("documents");
        return result;
    }

    private static boolean isParent(String relation) {
        if(relation==null)return false;
        return Set.of("cha","me","mẹ","bố","bo","ba","father","mother")
            .contains(relation.strip().toLowerCase(Locale.ROOT));
    }

    private List<Comparison> compare(Submission v,Map<String,Object> source) {
        var claims=claims(v);
        List<Comparison> rows=new ArrayList<>();
        boolean differs=false,missing=false;
        for(String field:CLAIMS) {
            var row=new Comparison();
            row.field=field;
            row.claimed=Objects.toString(claims.get(field),null);
            row.source=Objects.toString(wire(source.get(field)),null);
            boolean same=Objects.equals(row.claimed,row.source);
            if("fullName".equals(field) && row.source!=null) same=normalizedName(row.claimed).equals(normalizedName(row.source));
            row.result=row.source==null?"khong_co_nguon":same?"trung_khop":"khac_biet";
            row.observedAt=v.sourceAt;
            differs|="khac_biet".equals(row.result);
            missing|="khong_co_nguon".equals(row.result);
            rows.add(row);
        }
        v.comparisonStatus=differs?"khac_biet":missing?"khong_co_nguon":"trung_khop";
        return rows;
    }

    private static String normalizedName(String value) {
        return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFC)
            .strip().replaceAll("(?U)\\s+"," ").toLowerCase(Locale.ROOT);
    }

    private void event(Application root,Submission v,long user,String action,String prior,String next,
                       String reason,OffsetDateTime now,Long supplement) {
        var row=new ApplicationEvent();
        row.applicationId=root.id; row.versionId=v==null?null:v.id; row.actorId=user; row.actorType="user";
        row.action=action; row.previous=prior; row.current=next; row.reason=reason; row.createdAt=now;
        row.requestId=v==null?UUID.randomUUID():v.operationId; row.supplementId=supplement;
        repo.save(row);
    }

    private String hash(Long target,JsonNode body) {
        try {
            var canonical=json.createObjectNode();
            canonical.put("target",target==null?"initial":target.toString());
            var payload=(ObjectNode)sorted(body);
            payload.remove("operationId");
            canonical.set("content",payload);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(canonical)));
        } catch(java.io.IOException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }

    private JsonNode sorted(JsonNode value) {
        if(value.isObject()) {
            var fields=new TreeMap<String,JsonNode>();
            value.properties().forEach(e -> fields.put(e.getKey(),sorted(e.getValue())));
            var node=json.createObjectNode();
            fields.forEach(node::set);
            return node;
        }
        if(value.isArray()) {
            var list=json.createArrayNode();
            value.forEach(n -> list.add(sorted(n)));
            return list;
        }
        return value;
    }
}
