package pbl6.ctsv.foundation.sync;

import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import static org.assertj.core.api.Assertions.*;

/** Literal synthetic input; production target, SQL constraints and durable transactions are real. */
class FoundationTargetIT {
    static final ObjectMapper JSON=new ObjectMapper();
    @BeforeAll static void prepare()throws Exception{P02Database.migrate();DurableStoreIT.ds=P02Database.source();}
    static SyncTarget target(String resource)throws Exception{
        Class<?> type=null;
        try{type=Class.forName("pbl6.ctsv.foundation.sync.JdbcFoundationTarget");}catch(ClassNotFoundException ignored){}
        assertThat(type).as("P02 production consumer must exist").isNotNull();
        return (SyncTarget)type.getConstructor(String.class).newInstance(resource);
    }
    static ObjectNode data(String resource)throws Exception{
        var root=JSON.readTree(FoundationTargetIT.class.getResourceAsStream("/contracts/foundation-resources.json"));
        var data=JSON.createObjectNode();for(var r:root)if(r.path("resource").asText().equals(resource))for(var f:r.path("fields"))if(!f.path("json").asText().equals("source_id"))data.putNull(f.path("json").asText());return data;
    }
    static ObjectNode change(long id,ObjectNode data){var n=JSON.createObjectNode().put("source_id",Long.toString(id)).put("operation","upsert");n.putNull("change_version");n.set("data",data);return n;}
    static void apply(long source,String resource,long version,JsonNode... changes)throws Exception{
        var store=DurableStoreIT.store();try(var lease=store.acquire(source,resource)){
            var page=DurableStoreIT.page(UUID.randomUUID(),version,null,changes);var run=store.beginRun(lease,page);store.page(lease,run,1,null,page,target(resource));
        }
    }
    static String value(String query)throws Exception{try(var c=P02Database.open();var s=c.createStatement();var r=s.executeQuery(query)){assertThat(r.next()).isTrue();return r.getString(1);}}
    static long local(long source,String table,long id)throws Exception{return Long.parseLong(value("SELECT id FROM dbo.["+table+"] WHERE he_thong_nguon_id="+source+" AND ma_dinh_danh_ben_ngoai=N'"+id+"'"));}

    @Test void replayKeepsLocalKeyAndHashAndDeleteDoesNotRemoveHistory()throws Exception{
        long source=DurableStoreIT.source();var d=data("ethnicities").put("code","P02-ĐT").put("name","");
        apply(source,"ethnicities",1,change(90001,d));long id=local(source,"dan_toc",90001);
        String hash=value("SELECT sha256_ban_ghi_nguon FROM dbo.dan_toc WHERE id="+id);
        apply(source,"ethnicities",2,change(90001,d));assertThat(local(source,"dan_toc",90001)).isEqualTo(id).isNotEqualTo(90001);
        assertThat(value("SELECT sha256_ban_ghi_nguon FROM dbo.dan_toc WHERE id="+id)).isEqualTo(hash);
        assertThat(value("SELECT ten FROM dbo.dan_toc WHERE id="+id)).isEmpty();
        try(var c=P02Database.open()){target("ethnicities").markAbsent(c,source,90001);}
        assertThat(value("SELECT dang_hien_dien_nguon FROM dbo.dan_toc WHERE id="+id)).isEqualTo("0");
        assertThat(value("SELECT COUNT(*) FROM dbo.dan_toc WHERE id="+id)).isEqualTo("1");
    }

    @Test void parentIsResolvedBySourceMappingAndMissingOrAbsentParentRollsBackPage()throws Exception{
        long source=DurableStoreIT.source();var parent=data("national-majors").put("code","NQG").put("name","Giả lập");
        apply(source,"national-majors",1,change(80001,parent));long parentId=local(source,"nganh_quoc_gia",80001);
        var good=data("internal-majors").put("code","NTQ").put("national_major_source_id","80001");
        apply(source,"internal-majors",2,change(80002,good));
        assertThat(value("SELECT nganh_quoc_gia_id FROM dbo.nganh_noi_bo WHERE id="+local(source,"nganh_noi_bo",80002))).isEqualTo(Long.toString(parentId));
        var bad=good.deepCopy().put("code","MISSING").put("national_major_source_id","89999");
        assertThatThrownBy(()->apply(source,"internal-majors",3,change(80003,good.deepCopy().put("code","FIRST")),change(80004,bad))).hasMessageContaining("FK_MAPPING_REQUIRED");
        assertThat(value("SELECT COUNT(*) FROM dbo.nganh_noi_bo WHERE he_thong_nguon_id="+source+" AND ma_dinh_danh_ben_ngoai IN(N'80003',N'80004')")).isEqualTo("0");
        try(var c=P02Database.open()){target("national-majors").markAbsent(c,source,80001);}
        assertThatThrownBy(()->apply(source,"internal-majors",4,change(80005,good.deepCopy().put("code","ABSENT")))).hasMessageContaining("FK_MAPPING_REQUIRED");
    }

    @Test void studentSyncNeverRewritesVerifiedProcessOrLocalContactFields()throws Exception{
        long source=DurableStoreIT.source();
        var a=data("programs").put("code","PROG-A-"+source);var b=data("programs").put("code","PROG-B-"+source);
        apply(source,"programs",1,change(77001,a));
        apply(source,"cohorts",1,change(77003,data("cohorts").put("code","COHORT-"+source)));
        long originalProgram=local(source,"chuong_trinh_dao_tao",77001);
        var d=data("students").put("student_code","SV-"+UUID.randomUUID()).put("full_name","SV giả lập").put("program_source_id","77001").put("cohort_source_id","77003");
        apply(source,"students",1,change(70001,d));long id=local(source,"sinh_vien",70001);
        try(var c=P02Database.open();var s=c.createStatement()){
            s.execute("UPDATE dbo.sinh_vien SET ten_ngan_hang=N'LOCAL BANK' WHERE id="+id);
            s.execute("INSERT dbo.qua_trinh_theo_hoc(sinh_vien_id,chuong_trinh_dao_tao_id,ma_qua_trinh_hoc,trang_thai_doi_chieu,can_cu_xac_minh) VALUES("+id+","+originalProgram+",N'QT-"+source+"',N'verified',N'RETAINED')");
        }
        apply(source,"programs",2,change(77001,a),change(77002,b));
        apply(source,"students",2,change(70001,d.deepCopy().put("education_system_code","NEW RAW").put("program_source_id","77002")));
        assertThat(value("SELECT ten_ngan_hang FROM dbo.sinh_vien WHERE id="+id)).isEqualTo("LOCAL BANK");
        assertThat(value("SELECT can_cu_xac_minh FROM dbo.qua_trinh_theo_hoc WHERE sinh_vien_id="+id)).isEqualTo("RETAINED");
        assertThat(value("SELECT ma_qua_trinh_hoc FROM dbo.qua_trinh_theo_hoc WHERE sinh_vien_id="+id)).isEqualTo("QT-"+source);
        assertThat(value("SELECT trang_thai_hoc_tap_nguon FROM dbo.sinh_vien WHERE id="+id)).isNull();
        assertThat(value("SELECT chuong_trinh_hien_tai_id FROM dbo.sinh_vien WHERE id="+id)).isEqualTo(Long.toString(local(source,"chuong_trinh_dao_tao",77002)));
        assertThat(value("SELECT chuong_trinh_dao_tao_id FROM dbo.qua_trinh_theo_hoc WHERE sinh_vien_id="+id)).isEqualTo(Long.toString(originalProgram));
    }

    @Test void decimalExactnessNullAndUnresolvedReferenceRemainDistinct()throws Exception{
        long source=DurableStoreIT.source();var d=data("academic-results").put("credit_ratio","123456789012345678.1234567890").put("study_average_10","0.0000000000");
        apply(source,"academic-results",1,change(60001,d));long id=local(source,"du_lieu_hoc_vu_dong_bo",60001);
        assertThat(value("SELECT CONVERT(varchar(64),tc_ty_le) FROM dbo.du_lieu_hoc_vu_dong_bo WHERE id="+id)).isEqualTo("123456789012345678.1234567890");
        assertThat(value("SELECT hp_dang_ky FROM dbo.du_lieu_hoc_vu_dong_bo WHERE id="+id)).isNull();
        var ref=data("room-usage").put("room_code_reference","NO-SUCH-ROOM");apply(source,"room-usage",1,change(60002,ref));
        assertThat(value("SELECT ma_phong_tham_chieu FROM dbo.lich_su_dung_phong WHERE id="+local(source,"lich_su_dung_phong",60002))).isEqualTo("NO-SUCH-ROOM");
        assertThat(value("SELECT trang_thai_chuan_hoa FROM dbo.lich_su_dung_phong WHERE id="+local(source,"lich_su_dung_phong",60002))).isNotEqualTo("normalized");
    }

    @Test void varcharCannotSilentlyReplaceUnsupportedCharacters()throws Exception{
        long source=DurableStoreIT.source();var d=data("rooms").put("room_code","😀");
        assertThatThrownBy(()->apply(source,"rooms",1,change(50001,d))).isInstanceOf(IllegalArgumentException.class);
        assertThat(value("SELECT COUNT(*) FROM dbo.phong_hoc WHERE he_thong_nguon_id="+source)).isEqualTo("0");
    }

    @Test void twoCourseRolesResolveDistinctLocalParentsAndDoNotCopyOneRoleIntoTheOther()throws Exception{
        long source=DurableStoreIT.source();apply(source,"courses",1,change(88001,data("courses").put("course_code","A")),change(88002,data("courses").put("course_code","B")));
        apply(source,"curriculum-items",1,change(88003,data("curriculum-items").put("course_source_id","88001").put("replaced_course_source_id","88002")));
        long child=local(source,"khung_chuong_trinh",88003);
        assertThat(value("SELECT hoc_phan_id FROM dbo.khung_chuong_trinh WHERE id="+child)).isEqualTo(Long.toString(local(source,"hoc_phan",88001)));
        assertThat(value("SELECT hoc_phan_duoc_thay_the_id FROM dbo.khung_chuong_trinh WHERE id="+child)).isEqualTo(Long.toString(local(source,"hoc_phan",88002)));
        assertThat(local(source,"hoc_phan",88001)).isNotEqualTo(local(source,"hoc_phan",88002));
    }
}
