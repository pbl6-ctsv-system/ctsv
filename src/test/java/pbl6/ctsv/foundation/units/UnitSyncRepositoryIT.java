package pbl6.ctsv.foundation.units;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import pbl6.ctsv.foundation.units.dto.*;
import pbl6.ctsv.foundation.units.mapper.UnitSourceMapper;
import pbl6.ctsv.foundation.units.repository.UnitSyncRepository;
import pbl6.p00.SliceATestDatabaseSupport;
import static org.assertj.core.api.Assertions.*;

class UnitSyncRepositoryIT {
    private final UnitSyncRepository repository=new UnitSyncRepository(new UnitSourceMapper());
    long source,localChild;String prefix="P01-REPO-"+UUID.randomUUID();
    UnitChangeDto row(String id,String code,String archive,boolean active) {
        return new UnitChangeDto(id,"upsert",null,new UnitDataDto(code,"Source name",null,"2026-11-11T00:00:00Z",active,archive,"classification"));
    }
    @BeforeAll static void migrate()throws Exception{SliceATestDatabaseSupport.migrate();}
    @BeforeEach void seed()throws Exception {
        try(var c=SliceATestDatabaseSupport.open();var s=c.prepareStatement("INSERT dbo.he_thong_ngoai(ma,ten) VALUES(?,N'Synthetic source P01'); SELECT CAST(SCOPE_IDENTITY() AS bigint)")){
            s.setString(1,prefix);try(var r=s.executeQuery()){r.next();source=r.getLong(1);}
        }
    }
    @AfterEach void cleanup()throws Exception {
        if(localChild!=0)try(var c=SliceATestDatabaseSupport.open();var s=c.prepareStatement("DELETE dbo.don_vi WHERE id=? AND ma=?")){
            s.setLong(1,localChild);s.setString(2,prefix+"-child");s.executeUpdate();
        }
        try(var c=SliceATestDatabaseSupport.open();var s=c.prepareStatement("DELETE dbo.don_vi WHERE he_thong_nguon_id=?")){s.setLong(1,source);s.executeUpdate();}
    }
    @Test void sa011_013_sae03_idMappingAndRawReplayPreserveLocalFields()throws Exception {
        try(var c=SliceATestDatabaseSupport.open()){
            c.setAutoCommit(false);repository.upsertPage(c,source,List.of(row("9007199254740993",prefix,"raw",true)));c.commit();
            var mapping=repository.mappings(c,source);assertThat(mapping).hasSize(1);
            long id=mapping.get("9007199254740993");assertThat(id).isNotEqualTo(9007199254740993L);
            try(var s=c.prepareStatement("UPDATE dbo.don_vi SET loai_don_vi=N'LOCAL',dang_hoat_dong=0 WHERE id=?")){s.setLong(1,id);s.executeUpdate();}
            c.commit();
            repository.upsertPage(c,source,List.of(row("9007199254740993",prefix,null,false)));c.commit();
            repository.upsertPage(c,source,List.of(row("9007199254740993",prefix,null,false)));c.commit();
            try(var s=c.prepareStatement("SELECT id,loai_don_vi,dang_hoat_dong,luu_tru_nguon,is_us_nguon,dang_hoat_dong_nguon FROM dbo.don_vi WHERE id=?")){
                s.setLong(1,id);try(var r=s.executeQuery()){
                    assertThat(r.next()).isTrue();assertThat(r.getLong(1)).isEqualTo(id);
                    assertThat(r.getString(2)).isEqualTo("LOCAL");assertThat(r.getBoolean(3)).isFalse();
                    assertThat(r.getString(4)).isNull();assertThat(r.getString(5)).isEqualTo("classification");assertThat(r.getBoolean(6)).isFalse();
                }
            }
            assertThat(repository.mappings(c,source)).hasSize(1);
        }
    }
    @Test void sa012_014_conflictingCodeRollsBackEntirePageAndNeverMergesIdentity()throws Exception {
        try(var c=SliceATestDatabaseSupport.open()){
            c.setAutoCommit(false);repository.upsertPage(c,source,List.of(row("11",prefix+"-existing",null,true)));c.commit();
            c.setAutoCommit(false);
            assertThatThrownBy(()->repository.upsertPage(c,source,List.of(row("12",prefix+"-first",null,true),row("13",prefix+"-existing",null,true))))
                .isInstanceOf(SQLException.class);
            c.rollback();
            assertThat(repository.mappings(c,source)).containsOnlyKeys("11");
        }
    }
    @Test void sa020_021_unknownDeleteCreatesNothingAndKnownDeleteKeepsPrimaryKey()throws Exception {
        try(var c=SliceATestDatabaseSupport.open()){
            c.setAutoCommit(false);repository.upsertPage(c,source,List.of(row("11",prefix,null,true)));c.commit();
            var before=repository.mappings(c,source);
            try(var s=c.prepareStatement("INSERT dbo.don_vi(ma,don_vi_cha_id) VALUES(?,?); SELECT CAST(SCOPE_IDENTITY() AS bigint)")){
                s.setString(1,prefix+"-child");s.setLong(2,before.get("11"));try(var r=s.executeQuery()){r.next();localChild=r.getLong(1);}
            }
            long audit=new pbl6.ctsv.shared.audit.AuditWriter().append(c,source,"FIXTURE_DELETE_HISTORY","don_vi",
                before.get("11").toString(),"Synthetic history to verify delete preservation",Map.of("fixture",prefix));
            c.commit();
            repository.upsertPage(c,source,List.of(new UnitChangeDto("11","delete","123",null),new UnitChangeDto("12","delete","123",null)));c.commit();
            assertThat(repository.mappings(c,source)).isEqualTo(before).hasSize(1);
            try(var s=c.prepareStatement("SELECT dang_hoat_dong_nguon FROM dbo.don_vi WHERE id=?")){
                s.setLong(1,before.get("11"));try(var r=s.executeQuery()){r.next();assertThat(r.getBoolean(1)).isFalse();}
            }
            try(var s=c.prepareStatement("SELECT don_vi_cha_id FROM dbo.don_vi WHERE id=?")){
                s.setLong(1,localChild);try(var r=s.executeQuery()){r.next();assertThat(r.getLong(1)).isEqualTo(before.get("11"));}
            }
            assertThat(SliceATestDatabaseSupport.scalar(c,"SELECT COUNT(*) FROM dbo.nhat_ky_thao_tac WHERE id="+audit)).isEqualTo(1);
        }
    }
}

