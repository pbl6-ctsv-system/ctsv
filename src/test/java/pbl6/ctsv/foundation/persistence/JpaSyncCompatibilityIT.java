package pbl6.ctsv.foundation.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.foundation.entity.*;
import pbl6.ctsv.foundation.sync.*;
import static org.assertj.core.api.Assertions.*;

/** REAL SQL and production durable consumer/target. JSON envelopes are SYNTHETIC; no claim of SIS provider/CT runtime. */
@SpringBootTest(properties={"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.open-in-view=false"})
@Import(FoundationJpaIT.Database.class)
class JpaSyncCompatibilityIT {
    @Autowired DataSource ds;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager tx;
    private static final ObjectMapper JSON=new ObjectMapper();
    long source() { return new TransactionTemplate(tx).execute(s->{var e=new HeThongNgoai();e.setMa("SYNTHETIC-JPA-SYNC-"+UUID.randomUUID());em.persist(e);em.flush();return e.getId();}); }
    ObjectNode data(String resource) {
        var node=JSON.createObjectNode();
        for(var f:FoundationResourceCatalog.resource(resource).fields())if(!f.json().equals("source_id"))node.putNull(f.json());
        return node;
    }
    ObjectNode item(long id,JsonNode data,Long version) {
        var n=JSON.createObjectNode().put("source_id",Long.toString(id)).put("operation",data==null?"delete":"upsert");
        if(version==null)n.putNull("change_version");else n.put("change_version",version.toString());
        if(data==null)n.putNull("data");else n.set("data",data);return n;
    }
    ObjectNode page(UUID epoch,Long lower,long upper,JsonNode...items) {
        var n=JSON.createObjectNode().put("mode",lower==null?"full":"incremental").put("source_epoch",epoch.toString()).put("export_id",UUID.randomUUID().toString());
        if(lower==null)n.putNull("from_version");else n.put("from_version",lower.toString());
        n.put("to_version",Long.toString(upper)).put("as_of","2026-11-13T01:00:00Z").putNull("next_cursor");n.set("items",JSON.valueToTree(items));return n;
    }
    void apply(DurableSyncStore store,long source,String resource,ObjectNode page) throws Exception {
        try(var lease=store.acquire(source,resource)) { var run=store.beginRun(lease,page);assertThat(store.page(lease,run,1,null,page,new JdbcFoundationTarget(resource)).complete()).isTrue(); }
    }
    DurableSyncStore store() { return new DurableSyncStore(ds,RefactorDatabase.NAME,"DEV2022"); }
    long local(String table,long source,long external) { return jdbc.queryForObject("SELECT id FROM dbo."+table+" WHERE he_thong_nguon_id=? AND ma_dinh_danh_ben_ngoai=?",Long.class,source,Long.toString(external)); }
    @Test void fullIncrementalAndDeleteKeepCalendarLocalFieldsAndIdentity() throws Exception {
        long source=source();var epoch=UUID.randomUUID();var payload=data("plans").put("code","JPA-HK-"+source).put("name","SYNTHETIC raw semester");
        apply(store(),source,"plans",page(epoch,null,10,item(91001,payload,null)));long id=local("hoc_ky",source,91001);
        new TransactionTemplate(tx).executeWithoutResult(s->{var h=em.find(HocKy.class,id);h.setCanCuXacMinhMoc("SYNTHETIC local evidence");h.setNgayBatDau(java.time.LocalDate.of(2026,9,1));});
        apply(store(),source,"plans",page(epoch,10L,11,item(91001,payload.deepCopy().put("name","Changed source"),11L)));
        new TransactionTemplate(tx).executeWithoutResult(s->{var h=em.find(HocKy.class,id);assertThat(h.getTen()).isEqualTo("Changed source");assertThat(h.getCanCuXacMinhMoc()).isEqualTo("SYNTHETIC local evidence");assertThat(h.getNgayBatDau()).isEqualTo(java.time.LocalDate.of(2026,9,1));});
        apply(store(),source,"plans",page(epoch,11L,12,item(91001,null,12L)));
        assertThat(local("hoc_ky",source,91001)).isEqualTo(id).isNotEqualTo(91001);
        new TransactionTemplate(tx).executeWithoutResult(s->assertThat(em.find(HocKy.class,id).getDangHienDienNguon()).isFalse());
        try(var lease=store().acquire(source,"plans")){assertThat(store().checkpoint(lease).version()).isEqualTo(12);}
    }
    @Test void foreignKeyMappingAndFailedPagePreserveCheckpoint() throws Exception {
        long source=source();var epoch=UUID.randomUUID();var parent=data("national-majors").put("code","JPA-QG-"+source);
        apply(store(),source,"national-majors",page(epoch,null,20,item(92001,parent,null)));
        var child=data("internal-majors").put("code","JPA-TQ-"+source).put("national_major_source_id","92001");
        apply(store(),source,"internal-majors",page(epoch,null,20,item(92002,child,null)));
        long parentId=local("nganh_quoc_gia",source,92001),childId=local("nganh_noi_bo",source,92002);
        new TransactionTemplate(tx).executeWithoutResult(s->assertThat(em.find(NganhNoiBo.class,childId).getNganhQuocGia().getId()).isEqualTo(parentId));
        var bad=child.deepCopy().put("national_major_source_id","92999");
        assertThatThrownBy(()->apply(store(),source,"internal-majors",page(epoch,20L,21,item(92003,child.deepCopy().put("code","GOOD-FIRST-"+source),21L),item(92004,bad,21L)))).hasMessageContaining("FK_MAPPING_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.nganh_noi_bo WHERE he_thong_nguon_id=? AND ma_dinh_danh_ben_ngoai IN('92003','92004')",Integer.class,source)).isZero();
        try(var lease=store().acquire(source,"internal-majors")){assertThat(store().checkpoint(lease).version()).isEqualTo(20);}
    }
}
