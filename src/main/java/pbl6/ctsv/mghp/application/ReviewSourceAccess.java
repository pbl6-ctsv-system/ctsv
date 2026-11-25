package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import static pbl6.ctsv.mghp.application.ApplicationEntities.*;
import static pbl6.ctsv.mghp.application.EvaluationEntities.*;

/** Public MGHP boundary: no persistence entities escape this read-only source preflight. */
@Component @ConditionalOnProperty(name="ctsv.mghp.applications.enabled",havingValue="true")
public final class ReviewSourceAccess {
    public record Blocker(String applicationId,String submissionId,String code) {}
    public record Snapshot(String sha256,int eligibleCount,int excludedCount,List<Blocker> blockers) {}
    private final IntakeRepository r;
    private final EvaluationRepository evaluations;
    private final EvaluationService service;
    private final ObjectMapper json;
    ReviewSourceAccess(IntakeRepository r,EvaluationRepository evaluations,EvaluationService service,ObjectMapper json) {
        this.r=r;this.evaluations=evaluations;this.service=service;this.json=json;
    }
    /** Intake handoff is independent of hard-copy assessment: do not require XET to finish online review. */
    public Snapshot readIntake(long roundId) {
        evaluations.flush();
        var facts=new ArrayList<Object>();var blockers=new ArrayList<Blocker>();int eligible=0,excluded=0;
        var heads=r.rows("""
            SELECT h.id,h.trang_thai status,CONVERT(varchar(18),h.phien_ban_ky_thuat,1) fence,v.id submissionId
            FROM dbo.mghp_ho_so h OUTER APPLY(SELECT TOP(1) s.id FROM dbo.mghp_phien_ban_ho_so s
             WHERE s.ho_so_id=h.id AND COALESCE(s.dot_tiep_nhan_id,h.dot_xet_id)=? ORDER BY s.lan_sua_doi DESC,s.id DESC) v
            WHERE h.dot_xet_id=? OR v.id IS NOT NULL ORDER BY h.id
            """,roundId,roundId);
        facts.add(heads);
        for(var head:heads) {
            String state=Objects.toString(head.get("status"),"");
            if(Set.of("rejected","withdrawn").contains(state)){excluded++;continue;}
            if("approved".equals(state)&&head.get("submissionId")!=null){eligible++;continue;}
            blockers.add(new Blocker(head.get("id").toString(),Objects.toString(head.get("submissionId"),null),"INTAKE_UNRESOLVED"));
        }
        var supplements=r.rows("""
            SELECT y.id,y.ho_so_id root,y.trang_thai status,y.han_xu_ly deadline
            FROM dbo.mghp_yeu_cau_bo_sung y JOIN dbo.mghp_ho_so h ON h.id=y.ho_so_id
            WHERE COALESCE(y.dot_tiep_nhan_id,h.dot_xet_id)=? ORDER BY y.id
            """,roundId);
        facts.add(supplements);
        for(var s:supplements) {
            var items=r.rows("SELECT id,ket_qua_xu_ly result FROM dbo.mghp_noi_dung_bo_sung WHERE yeu_cau_bo_sung_id=? ORDER BY id",s.get("id"));facts.add(items);
            String state=Objects.toString(s.get("status"),"");
            if(!Set.of("completed","cancelled").contains(state)||"completed".equals(state)&&items.stream().anyMatch(i->!Set.of("satisfied","unsatisfied","not_applicable").contains(Objects.toString(i.get("result"),""))))
                blockers.add(new Blocker(s.get("root").toString(),null,"SUPPLEMENT_UNRESOLVED"));
        }
        return snapshot(facts,eligible,excluded,blockers);
    }
    public Snapshot read(long roundId) {
        evaluations.flush();
        var facts=new ArrayList<Object>();var blockers=new ArrayList<Blocker>();int eligible=0,excluded=0;
        var emptyRoots=r.rows("SELECT h.id,h.trang_thai state FROM dbo.mghp_ho_so h WHERE h.dot_xet_id=? AND NOT EXISTS(SELECT 1 FROM dbo.mghp_phien_ban_ho_so v WHERE v.ho_so_id=h.id) ORDER BY h.id",roundId);
        facts.add(emptyRoots);
        for(var root:emptyRoots)if(!"withdrawn".equals(root.get("state")))blockers.add(new Blocker(root.get("id").toString(),null,"MISSING_SUBMISSION"));
        var heads=r.rows("""
            SELECT v.id,h.id root FROM dbo.mghp_phien_ban_ho_so v JOIN dbo.mghp_ho_so h ON h.id=v.ho_so_id
            WHERE COALESCE(v.dot_tiep_nhan_id,h.dot_xet_id)=? AND NOT EXISTS(
                SELECT 1 FROM dbo.mghp_phien_ban_ho_so n WHERE n.ho_so_id=v.ho_so_id
                AND COALESCE(n.quyen_huong_cap_nhat_id,0)=COALESCE(v.quyen_huong_cap_nhat_id,0)
                AND COALESCE(n.dot_tiep_nhan_id,h.dot_xet_id)=? AND n.id>v.id)
            ORDER BY h.id,v.id
            """,roundId,roundId);
        for(var head:heads) {
            long rootId=((Number)head.get("root")).longValue(),subId=((Number)head.get("id")).longValue();
            var root=r.root(rootId);var sub=r.find(Submission.class,subId);
            facts.add(IntakeMapping.map("root",root.id,"status",root.status,"fence",Base64.getEncoder().encodeToString(root.fence),"submission",sub.id));
            if("withdrawn".equals(root.status)||"rejected".equals(root.status)){excluded++;continue;}
            var requests=r.children(SemesterRequest.class,"versionId",subId,"id");
            if(requests.isEmpty())blockers.add(block(rootId,subId,"MISSING_REQUESTED_SEMESTER"));
            for(var request:requests) {
                var chain=evaluations.sourceChain(root.id,sub.benefitId,request.semesterId,request.policyVersionId);
                facts.add(IntakeMapping.map("semester",request.semesterId,"policy",request.policyVersionId));
                if(chain.isEmpty()){blockers.add(block(rootId,subId,"MISSING_ASSESSMENT"));continue;}
                var e=chain.getFirst();
                facts.add(IntakeMapping.map("evaluation",e.id,"fence",Base64.getEncoder().encodeToString(e.fence),"source",e.submissionId,
                    "update",e.updateSubmissionId,"current",e.current,"state",e.status,"result",e.result,"scope",e.scopeCode));
                facts.add(r.student(e.studentId));
                facts.add(r.row("SELECT id,trang_thai_doi_chieu,thoi_diem_bat_dau,ngay_ket_thuc_thuc_te FROM dbo.qua_trinh_theo_hoc WHERE id=?",e.processId));
                facts.add(r.row("SELECT id,ngay_bat_dau,ngay_ket_thuc,trang_thai_xac_minh_moc,can_cu_xac_minh_moc FROM dbo.hoc_ky WHERE id=?",e.semesterId));
                facts.add(r.row("SELECT id,CONVERT(varchar(18),phien_ban_ky_thuat,1) fence FROM dbo.mghp_phien_ban_chinh_sach WHERE id=?",e.policyVersionId));
                if(e.benefitId!=null)facts.add(r.row("SELECT id,CONVERT(varchar(18),phien_ban_ky_thuat,1) fence FROM dbo.mghp_quyen_huong WHERE id=?",e.benefitId));
                for(var evidence:evaluations.children(Evidence.class,e.id)) {
                    facts.add(IntakeMapping.map("evidence",evidence.id,"result",evidence.result,"from",evidence.from,"to",evidence.to));
                    var d=r.find(Document.class,evidence.documentId);
                    facts.add(IntakeMapping.map("document",d.id,"file",r.fileId(d),"expires",d.expiresOn));
                    facts.add(r.row("SELECT id,sha256,trang_thai_luu_tru,thoi_diem_xoa FROM dbo.tep_dinh_kem WHERE id=?",r.fileId(d)));
                    facts.add(r.children(DocumentCheck.class,"documentId",d.id,"id").stream().map(c->IntakeMapping.map("id",c.id,"kind",c.kind,"result",c.result,"previous",c.previousId)).toList());
                }
                facts.add(evaluations.children(Period.class,e.id).stream().map(p->IntakeMapping.map("id",p.id,"from",p.from,"to",p.to,"evidence",p.evidence)).toList());
                Long source=sub.benefitId==null?e.submissionId:e.updateSubmissionId;
                if(!"confirmed".equals(e.status)||!Boolean.TRUE.equals(e.current)||!Objects.equals(source,subId)
                    ||!Objects.equals(e.roundId,roundId)||!Objects.equals(e.observedAt,r.student(e.studentId).get("observedAt"))) {
                    blockers.add(block(rootId,subId,"STALE_OR_UNCONFIRMED_ASSESSMENT"));continue;
                }
                try { service.ready(e); }
                catch(IntakeProblem failure) {blockers.add(block(rootId,subId,"ASSESSMENT_SOURCE_NOT_READY"));continue;}
                if("dat".equals(e.result))eligible++;else if(Set.of("khong_dat","can_xac_minh").contains(e.result)&&e.reason!=null&&!e.reason.isBlank())excluded++;
                else blockers.add(block(rootId,subId,"UNRESOLVED_ASSESSMENT"));
            }
        }
        // Unanswered/expired rounds and unfinished items are blockers, even when no current application is eligible.
        var supplements=r.rows("""
            SELECT y.id,y.ho_so_id root,y.trang_thai status,y.han_xu_ly deadline
            FROM dbo.mghp_yeu_cau_bo_sung y JOIN dbo.mghp_ho_so h ON h.id=y.ho_so_id
            WHERE COALESCE(y.dot_tiep_nhan_id,h.dot_xet_id)=? ORDER BY y.id
            """,roundId);
        facts.add(supplements);
        for(var s:supplements) {
            var items=r.rows("SELECT id,ket_qua_xu_ly result FROM dbo.mghp_noi_dung_bo_sung WHERE yeu_cau_bo_sung_id=? ORDER BY id",s.get("id"));facts.add(items);
            if(!Set.of("completed","cancelled").contains(Objects.toString(s.get("status"),"")) || "completed".equals(s.get("status"))&&items.stream().anyMatch(i->!Set.of("satisfied","unsatisfied","not_applicable").contains(Objects.toString(i.get("result"),""))))
                blockers.add(new Blocker(s.get("root").toString(),null,"SUPPLEMENT_UNRESOLVED"));
        }
        return snapshot(facts,eligible,excluded,blockers);
    }
    private Snapshot snapshot(Object facts,int eligible,int excluded,List<Blocker> blockers) {
        try { return new Snapshot(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(IntakeMapping.wire(facts)))),eligible,excluded,List.copyOf(blockers)); }
        catch(java.security.NoSuchAlgorithmException|com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
    }
    private static Blocker block(long root,long sub,String code){return new Blocker(Long.toString(root),Long.toString(sub),code);}
}
