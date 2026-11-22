package pbl6.ctsv.mghp.configuration;

import java.time.*;
import java.util.*;
import pbl6.ctsv.mghp.configuration.entity.*;

/** Round-owned persistence. History snapshots are distinct entities, never managed round references. */
final class RoundTimelineRepository {
    private final ConfigurationRepository r;
    RoundTimelineRepository(ConfigurationRepository r) { this.r=r; }

    static TimelineModels.Schedule plan(RoundSchedule p) {
        if(p==null)return TimelineModels.Schedule.empty();
        return new TimelineModels.Schedule(instant(p.supplementPlannedStartAt),instant(p.supplementPlannedEndAt),
            instant(p.reviewPlannedStartAt),p.councilPlannedDate,instant(p.signingPlannedStartAt),
            instant(p.signingPlannedEndAt),instant(p.publicationPlannedStartAt),instant(p.publicationPlannedEndAt));
    }
    static RoundSchedule entity(TimelineModels.Schedule p) {
        var e=new RoundSchedule();
        e.supplementPlannedStartAt=offset(p.supplementPlannedStartAt()); e.supplementPlannedEndAt=offset(p.supplementPlannedEndAt());
        e.reviewPlannedStartAt=offset(p.reviewPlannedStartAt()); e.councilPlannedDate=p.councilPlannedDate();
        e.signingPlannedStartAt=offset(p.signingPlannedStartAt()); e.signingPlannedEndAt=offset(p.signingPlannedEndAt());
        e.publicationPlannedStartAt=offset(p.publicationPlannedStartAt()); e.publicationPlannedEndAt=offset(p.publicationPlannedEndAt());
        return e;
    }
    static LinkedHashMap<String,Object> columns(TimelineModels.Schedule p) {
        var e=entity(p); var v=new LinkedHashMap<String,Object>();
        v.put("bo_sung_du_kien_tu",e.supplementPlannedStartAt); v.put("bo_sung_du_kien_den",e.supplementPlannedEndAt);
        v.put("ra_soat_du_kien_tu",e.reviewPlannedStartAt); v.put("ngay_hop_du_kien",e.councilPlannedDate);
        v.put("ky_du_kien_tu",e.signingPlannedStartAt); v.put("ky_du_kien_den",e.signingPlannedEndAt);
        v.put("cong_bo_du_kien_tu",e.publicationPlannedStartAt); v.put("cong_bo_du_kien_den",e.publicationPlannedEndAt);
        return v;
    }
    void baseline(long id,long actor,OffsetDateTime now) {
        if(r.jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_lich_su_lich_trinh WHERE dot_xet_id=?",Integer.class,id)==0)
            snapshot(id,actor,"BASELINE","Ghi nhận lịch hiện có trước thay đổi",now,0);
    }
    void changed(long id,long actor,String action,String reason,OffsetDateTime now) {
        int revision=r.jdbc.queryForObject("SELECT COALESCE(MAX(lan_thay_doi),0)+1 FROM dbo.mghp_lich_su_lich_trinh WITH(UPDLOCK,HOLDLOCK) WHERE dot_xet_id=?",Integer.class,id);
        snapshot(id,actor,action,reason,now,revision);
    }
    private void snapshot(long id,long actor,String action,String reason,OffsetDateTime now,int revision) {
        var round=r.find(MghpDotXet.class,id); var h=new MghpLichSuLichTrinh();
        h.roundId=id; h.revision=revision; h.action=action; h.reason=reason; h.actorId=actor; h.recordedAt=now;
        h.opensAt=round.getThoiDiemMo(); h.submissionDeadline=round.getHanNop(); h.reviewDeadline=round.getHanRaSoat();
        h.plan=entity(plan(round.getLichTrinh())); h.reviewClosedAt=round.getThoiDiemDongXet();
        h.reviewClosedBy=round.getNguoiDongXetId(); h.sourceSha256=round.getSha256NguonDongXet();
        h.auditId=r.jdbc.queryForObject("""
            DECLARE @ids TABLE(id bigint);
            INSERT dbo.nhat_ky_thao_tac(su_kien_id,loai_chu_the_thao_tac,nguoi_dung_id,hanh_dong,loai_doi_tuong,doi_tuong_id,ly_do,thoi_diem_tao)
            OUTPUT INSERTED.id INTO @ids VALUES(?,N'user',?,?,N'mghp_dot_xet',?,?,?); SELECT id FROM @ids
            """,Long.class,UUID.randomUUID().toString(),actor,"ROUND_TIMELINE_"+action,Long.toString(id),reason,now);
        r.em.persist(h); r.em.flush();
    }
    Object history(long id,int[] page) {
        r.find(MghpDotXet.class,id);
        var list=r.em.createQuery("from MghpLichSuLichTrinh where roundId=:id order by revision desc,id desc",MghpLichSuLichTrinh.class)
            .setParameter("id",id).setFirstResult(page[0]*page[1]).setMaxResults(page[1]).getResultList();
        var items=list.stream().map(h->{var v=new LinkedHashMap<String,Object>();
            v.put("id",h.id.toString());v.put("revision",h.revision);v.put("action",h.action);v.put("reason",h.reason);
            v.put("actorUserId",h.actorId.toString());v.put("recordedAt",instant(h.recordedAt));
            v.put("opensAt",instant(h.opensAt));v.put("submissionDeadline",instant(h.submissionDeadline));v.put("reviewDeadline",instant(h.reviewDeadline));
            v.put("schedule",plan(h.plan).view());v.put("reviewClosedAt",instant(h.reviewClosedAt));return v;}).toList();
        long count=r.em.createQuery("select count(h) from MghpLichSuLichTrinh h where roundId=:id",Long.class).setParameter("id",id).getSingleResult();
        return Map.of("items",items,"page",page[0],"size",page[1],"totalElements",count);
    }
    static Instant instant(OffsetDateTime d){return d==null?null:d.toInstant();}
    static OffsetDateTime offset(Instant d){return d==null?null:d.atOffset(ZoneOffset.UTC);}
}
