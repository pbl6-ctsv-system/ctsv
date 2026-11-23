package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pbl6.ctsv.foundation.api.FoundationAccess.Identity;
import static pbl6.ctsv.mghp.application.ApplicationEntities.*;
import static pbl6.ctsv.mghp.application.IntakeMapping.*;
import static pbl6.ctsv.mghp.application.IntakeRules.*;
import static pbl6.ctsv.mghp.application.IntakeProblem.*;

@Service
@ConditionalOnProperty(name="ctsv.mghp.applications.enabled",havingValue="true")
class DocumentReviewService {
    private static final Set<String> KINDS=Set.of("form","content","validity","subject");
    private static final Set<String> RESULTS=Set.of("pass","fail","needs_verification","not_applicable");
    private final IntakeRepository repo;
    private final ApplicationReads reads;
    private final pbl6.ctsv.mghp.configuration.IntakeConfiguration configuration;

    DocumentReviewService(IntakeRepository repo,ApplicationReads reads,pbl6.ctsv.mghp.configuration.IntakeConfiguration configuration) {
        this.repo=repo; this.reads=reads;this.configuration=configuration;
    }

    Object context(long id) {
        var root=repo.root(id);
        var versions=repo.versions(id);
        var comparisons=versions.stream().findFirst().stream()
            .flatMap(v -> repo.children(Comparison.class,"versionId",v.id,"id").stream())
            .filter(c -> CLAIMS.contains(c.field))
            .map(c -> map("field",c.field,"declaredValue",c.claimed,"sourceValue",c.source,"status",c.result,"observedAt",c.observedAt))
            .toList();
        var documents=versions.stream().flatMap(v -> repo.children(Document.class,"versionId",v.id,"id").stream())
            .map(d -> map("id",d.id,"submissionId",d.versionId,"requirementId",d.requirementId,"fileId",repo.fileId(d),
                "documentNumber",d.documentNumber,"issuer",d.issuer,"issuedOn",d.issuedOn,"expiresOn",d.expiresOn,
                "checks",repo.children(DocumentCheck.class,"documentId",d.id,"id").stream().map(DocumentReviewService::view).toList()))
            .toList();
        var evaluations=repo.rows("""
            SELECT id,so_phien_ban_danh_gia revision,trang_thai_xu_ly status,ket_qua result,
                   hoc_ky_ap_dung_id semesterId,phien_ban_chinh_sach_id policyVersionId,
                   qua_trinh_hoc_id processId,ma_pham_vi scopeCode
            FROM dbo.mghp_danh_gia_dieu_kien
            WHERE ho_so_nguon_id=? OR phien_ban_ho_so_nguon_id IN (SELECT id FROM dbo.mghp_phien_ban_ho_so WHERE ho_so_id=?)
            ORDER BY so_phien_ban_danh_gia DESC,id DESC
            """,id,id).stream().map(IntakeMapping::wire).toList();
        return map("applicationId",root.id,"submittedVersions",versions.stream().map(IntakeMapping::version).toList(),
            "comparisons",comparisons,"documents",documents,"evaluations",evaluations);
    }

    Object check(Identity actor,long id,JsonNode input,String tag,boolean hardCopy,Instant received) {
        configuration.lockWrites();
        var row=new DocumentCheck();
        if(hardCopy) {
            shape(input,List.of("received","note"));
            if(!input.get("received").isBoolean())throw invalid("received");
            row.kind="hard_copy"; row.result=input.get("received").asBoolean()?"received":"not_received";
            row.note=text(input.get("note"),"note",4000,false);
        } else {
            shape(input,List.of("kind","result","note","replacesCheckId"));
            row.kind=SupplementService.choice(input,"kind",KINDS);
            row.result=SupplementService.choice(input,"result",RESULTS);
            row.note=text(input.get("note"),"note",4000,!"pass".equals(row.result));
            row.previousId=nullableId(input,"replacesCheckId");
        }
        long rootId=(Long)repo.row("""
            SELECT v.ho_so_id FROM dbo.mghp_giay_to_ho_so d JOIN dbo.mghp_phien_ban_ho_so v ON v.id=d.phien_ban_ho_so_id
            WHERE d.id=?
            """,id).get("ho_so_id");
        var root=repo.root(rootId);
        reads.match(root,tag);
        if(repo.terminalReview(rootId))throw conflict("DOCUMENT_VERSION_CONFLICT");
        if(hardCopy&&!"approved".equals(root.status))throw conflict("DOCUMENT_VERSION_CONFLICT");
        var document=repo.find(Document.class,id);
        var current=repo.versions(rootId).stream().findFirst().orElseThrow(IntakeProblem::missing);
        configuration.requireReviewOpen(current.roundId==null?root.roundId:current.roundId);
        if(!document.versionId.equals(current.id) || Set.of("withdrawn","rejected").contains(root.status==null?"":root.status))
            throw conflict("DOCUMENT_VERSION_CONFLICT");
        if("not_applicable".equals(row.result) && !notApplicable(row.kind,document))
            throw invalid("result");
        var previous=repo.children(DocumentCheck.class,"documentId",id,"id desc").stream()
            .filter(c -> row.kind.equals(c.kind)).findFirst().orElse(null);
        if(hardCopy && previous!=null)row.previousId=previous.id;
        if(row.previousId!=null && (previous==null || !row.previousId.equals(previous.id)))
            throw conflict("DOCUMENT_VERSION_CONFLICT");
        row.documentId=document.id; row.actorId=actor.userId(); row.checkedAt=received.atOffset(ZoneOffset.UTC);
        repo.save(row);
        repo.change(root,root.status,row.checkedAt,null);
        var event=new ApplicationEvent();
        event.applicationId=root.id; event.versionId=current.id; event.actorId=actor.userId(); event.actorType="user";
        event.action="document_check:"+row.id; event.reason=row.note; event.current=root.status; event.previous=root.status;
        event.createdAt=row.checkedAt; event.requestId=UUID.randomUUID();
        repo.save(event);
        return view(row);
    }

    private boolean notApplicable(String kind,Document document) {
        if(!"validity".equals(kind) || document.expiresOn!=null)return false;
        var requirement=repo.row("SELECT tuoi_toi_da_giay_to_theo_ngay FROM dbo.mghp_giay_to_bat_buoc WHERE id=?",document.requirementId);
        return requirement.get("tuoi_toi_da_giay_to_theo_ngay")==null;
    }

    private static Object view(DocumentCheck row) {
        return map("id",row.id,"documentId",row.documentId,"kind",row.kind,"result",row.result,"note",row.note,
            "previousCheckId",row.previousId,"checkedAt",row.checkedAt);
    }
}
