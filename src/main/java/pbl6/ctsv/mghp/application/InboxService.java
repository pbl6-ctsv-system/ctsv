package pbl6.ctsv.mghp.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import static pbl6.ctsv.mghp.application.ApplicationEntities.*;
import static pbl6.ctsv.mghp.application.IntakeMapping.map;

@Service
@ConditionalOnProperty(name="ctsv.mghp.applications.enabled",havingValue="true")
class InboxService {
    private final IntakeRepository repo;
    private final Clock clock;

    InboxService(IntakeRepository repo,Clock clock) { this.repo=repo; this.clock=clock; }

    void publish(Application application,long actor,long owner,String title,OffsetDateTime now) {
        publish(application,actor,owner,title,now,null);
    }

    void publish(Application application,long actor,long owner,String title,OffsetDateTime now,Supplement supplement) {
        var notice=new Notification();
        notice.module="MGHP"; notice.title=title; notice.content=title+" "+application.code+".";
        notice.category="mghp"; notice.objectType="mghp_ho_so"; notice.objectId=application.id.toString();
        notice.actionUrl="/mghp/ho-so?ho-so="+application.id; notice.status="published"; notice.publishedAt=now; notice.createdAt=now; notice.createdBy=actor;
        if(supplement!=null) {
            notice.objectType="mghp_yeu_cau_bo_sung";
            notice.objectId=supplement.id.toString();
            notice.actionUrl="/mghp/ho-so?bo-sung="+supplement.id;
            notice.deadline=supplement.deadline;
        }
        repo.save(notice);
        var recipient=new Recipient();
        recipient.notificationId=notice.id; recipient.userId=owner; recipient.createdAt=now;
        repo.save(recipient);
    }

    Object list(long actor,boolean unread,int page,int size) {
        String filter=" from P06Notification n,P06Recipient r where n.id=r.notificationId and r.userId=:actor "
            +"and n.status='published' and n.publishedAt is not null and n.publishedAt<=:now and (:unread=false or r.readAt is null)";
        var now=OffsetDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);
        var query=repo.query("select n,r"+filter+" order by n.createdAt desc,n.id desc",Object[].class)
            .setParameter("actor",actor).setParameter("unread",unread).setParameter("now",now);
        long offset=(long)page*size;
        var items=offset>Integer.MAX_VALUE?java.util.List.of():query.setFirstResult((int)offset).setMaxResults(size).getResultList()
            .stream().map(pair -> item((Notification)pair[0],(Recipient)pair[1])).toList();
        long count=repo.query("select count(n)"+filter,Long.class)
            .setParameter("actor",actor).setParameter("unread",unread).setParameter("now",now).getSingleResult();
        return Map.of("items",items,"page",page,"size",size,"totalElements",count);
    }

    Object read(long actor,long id) {
        var now=OffsetDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);
        var rows=repo.rows("""
            SELECT r.id FROM dbo.nguoi_nhan_thong_bao r WITH(UPDLOCK,HOLDLOCK) JOIN dbo.thong_bao n ON n.id=r.thong_bao_id
            WHERE r.nguoi_dung_id=? AND n.id=? AND n.trang_thai='published' AND n.thoi_diem_cong_bo<=?
            """,actor,id,now);
        if(rows.size()!=1)throw IntakeProblem.missing();
        var recipient=repo.find(Recipient.class,(Long)rows.getFirst().get("id"));
        if(recipient.readAt==null) {
            recipient.readAt=now.withNano((now.getNano()/100)*100);
            repo.flush();
        }
        return map("notificationId",id,"readAt",recipient.readAt);
    }

    private Map<String,Object> item(Notification n,Recipient r) {
        String supplementAction="mghp_yeu_cau_bo_sung".equals(n.objectType) && n.objectId!=null
            && n.objectId.matches("[1-9][0-9]*")?"/mghp/ho-so?bo-sung="+n.objectId:null;
        String applicationAction="mghp_ho_so".equals(n.objectType)&&n.objectId!=null&&n.objectId.matches("[1-9][0-9]*")?"/mghp/ho-so?ho-so="+n.objectId:null;
        String action=applicationAction!=null&&(applicationAction.equals(n.actionUrl)||"/mghp".equals(n.actionUrl))?applicationAction:supplementAction!=null&&supplementAction.equals(n.actionUrl)?supplementAction:null;
        return map("id",n.id,"title",n.title,"content",n.content,"category",n.category,
            "actionUrl",action,"deadline",n.deadline,"important",n.important,
            "publishedAt",n.publishedAt,"createdAt",n.createdAt,"readAt",r.readAt);
    }
}
