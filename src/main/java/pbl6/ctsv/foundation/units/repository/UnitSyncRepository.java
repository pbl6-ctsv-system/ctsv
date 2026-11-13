package pbl6.ctsv.foundation.units.repository;

import java.sql.*;
import java.util.*;
import pbl6.ctsv.foundation.units.dto.*;
import pbl6.ctsv.foundation.units.mapper.UnitSourceMapper;

/** Only source-owned columns are changed. The caller owns the guarded connection/transaction. */
public class UnitSyncRepository {
    private static final String MAPPING_VERSION="units-1.0.0-delta-20261111";
    private static final String NOW="TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')";
    private final UnitSourceMapper mapper;
    public UnitSyncRepository(UnitSourceMapper mapper){this.mapper=mapper;}
    private record Existing(long id,String hash,Boolean sourceActive) {}

    public void upsertPage(Connection c,long sourceId,List<UnitChangeDto> items)throws SQLException {
        if(c.getAutoCommit())throw new IllegalStateException("Page writes require an explicit transaction");
        for(var item:items) {
            var unit=mapper.map(item);Existing existing=null;
            try(var s=c.prepareStatement("SELECT id,sha256_ban_ghi_nguon,dang_hoat_dong_nguon FROM dbo.don_vi WITH(UPDLOCK,HOLDLOCK) WHERE he_thong_nguon_id=? AND ma_dinh_danh_ben_ngoai=?")) {
                s.setLong(1,sourceId);s.setString(2,unit.sourceId());
                try(var r=s.executeQuery()){if(r.next())existing=new Existing(r.getLong(1),r.getString(2),(Boolean)r.getObject(3));}
            }
            if(unit.deleted()) {
                if(existing!=null&&(!Boolean.FALSE.equals(existing.sourceActive())||!unit.hash().equals(existing.hash()))) {
                    try(var s=c.prepareStatement("UPDATE dbo.don_vi SET dang_hoat_dong_nguon=0,sha256_ban_ghi_nguon=?,thoi_diem_dong_bo="+NOW+",thoi_diem_cap_nhat="+NOW+" WHERE id=?")){
                        s.setString(1,unit.hash());s.setLong(2,existing.id());s.executeUpdate();
                    }
                }
                continue;
            }
            if(existing!=null&&unit.hash().equals(existing.hash())&&Boolean.valueOf(unit.active()).equals(existing.sourceActive()))continue;
            String sql=existing==null
                ?"INSERT dbo.don_vi(ma,ten,ma_bo_phan_nguon,thoi_diem_cap_nhat_nguon,dang_hoat_dong_nguon,luu_tru_nguon,is_us_nguon,sha256_ban_ghi_nguon,phien_ban_anh_xa,he_thong_nguon_id,ma_dinh_danh_ben_ngoai,thoi_diem_dong_bo,thoi_diem_tao,thoi_diem_cap_nhat) VALUES(?,?,?,?,?,?,?,?,?,?,?,"+NOW+","+NOW+","+NOW+")"
                :"UPDATE dbo.don_vi SET ma=?,ten=?,ma_bo_phan_nguon=?,thoi_diem_cap_nhat_nguon=?,dang_hoat_dong_nguon=?,luu_tru_nguon=?,is_us_nguon=?,sha256_ban_ghi_nguon=?,phien_ban_anh_xa=?,thoi_diem_dong_bo="+NOW+",thoi_diem_cap_nhat="+NOW+" WHERE id=?";
            try(var s=c.prepareStatement(sql)) {
                s.setString(1,unit.code());s.setString(2,unit.name());s.setString(3,unit.department());
                s.setObject(4,unit.updatedAt());s.setBoolean(5,unit.active());s.setString(6,unit.archiveStatus());
                s.setString(7,unit.unitClassification());s.setString(8,unit.hash());s.setString(9,MAPPING_VERSION);
                if(existing==null){s.setLong(10,sourceId);s.setString(11,unit.sourceId());}else s.setLong(10,existing.id());
                s.executeUpdate();
            }
        }
    }
    public Map<String,Long> mappings(Connection c,long sourceId)throws SQLException {
        Map<String,Long> result=new HashMap<>();
        try(var s=c.prepareStatement("SELECT ma_dinh_danh_ben_ngoai,id FROM dbo.don_vi WHERE he_thong_nguon_id=? AND ma_dinh_danh_ben_ngoai IS NOT NULL")){
            s.setLong(1,sourceId);try(var r=s.executeQuery()){while(r.next())result.put(r.getString(1),r.getLong(2));}
        }
        return Map.copyOf(result);
    }
    public Map<String,Long> projectedMappings(Connection c,long sourceId,Set<String> incoming)throws SQLException {
        Map<String,Long> result=new HashMap<>();List<String> keys=new ArrayList<>(incoming);
        for(int start=0;start<keys.size();start+=500){
            List<String> part=keys.subList(start,Math.min(start+500,keys.size()));
            String placeholders=String.join(",",Collections.nCopies(part.size(),"?"));
            try(var s=c.prepareStatement("SELECT ma_dinh_danh_ben_ngoai,id FROM dbo.don_vi WHERE he_thong_nguon_id=? AND ma_dinh_danh_ben_ngoai IN("+placeholders+")")){
                s.setLong(1,sourceId);for(int i=0;i<part.size();i++)s.setString(i+2,part.get(i));
                try(var r=s.executeQuery()){while(r.next())result.put(r.getString(1),r.getLong(2));}
            }
        }
        return Map.copyOf(result);
    }
    public void requireSource(Connection c,long sourceId)throws SQLException {
        try(var s=c.prepareStatement("SELECT id FROM dbo.he_thong_ngoai WHERE id=?")) {
            s.setLong(1,sourceId);try(var r=s.executeQuery()){if(!r.next())throw new IllegalStateException("Configured SIS source is missing");}
        }
    }
    public boolean hasMappings(Connection c,long sourceId)throws SQLException {
        try(var s=c.prepareStatement("SELECT TOP(1) id FROM dbo.don_vi WHERE he_thong_nguon_id=? AND ma_dinh_danh_ben_ngoai IS NOT NULL")){
            s.setLong(1,sourceId);try(var r=s.executeQuery()){return r.next();}
        }
    }
    public void progress(Connection c,long runId,int total)throws SQLException {
        try(var s=c.prepareStatement("UPDATE dbo.dot_dong_bo SET tong_so_ban_ghi=?,so_ban_ghi_thanh_cong=? WHERE id=? AND trang_thai=N'running'")){
            s.setInt(1,total);s.setInt(2,total);s.setLong(3,runId);s.executeUpdate();
        }
    }
    public String checkpoint(Connection c,long sourceId)throws SQLException {
        try(var s=c.prepareStatement("SELECT TOP(1) con_tro_sau FROM dbo.dot_dong_bo WHERE he_thong_nguon_id=? AND loai_doi_tuong=N'units' AND trang_thai=N'success' AND con_tro_sau IS NOT NULL ORDER BY thoi_diem_ket_thuc DESC,id DESC")) {
            s.setLong(1,sourceId);try(var r=s.executeQuery()){return r.next()?r.getString(1):null;}
        }
    }
    public void interruptOldRuns(Connection c,long sourceId)throws SQLException {
        try(var s=c.prepareStatement("UPDATE dbo.dot_dong_bo SET trang_thai=N'interrupted',thoi_diem_ket_thuc="+NOW+",noi_dung_loi=N'Interrupted; replay from last successful checkpoint' WHERE he_thong_nguon_id=? AND loai_doi_tuong=N'units' AND trang_thai=N'running'")){
            s.setLong(1,sourceId);s.executeUpdate();
        }
    }
    public long startRun(Connection c,long sourceId,String mode,String lower)throws SQLException {
        try(var s=c.prepareStatement("INSERT dbo.dot_dong_bo(he_thong_nguon_id,loai_doi_tuong,che_do_dong_bo,con_tro_truoc,trang_thai,tong_so_ban_ghi,so_ban_ghi_thanh_cong,so_ban_ghi_loi,thoi_diem_bat_dau) VALUES(?,N'units',?,?,N'running',0,0,0,"+NOW+"); SELECT CAST(SCOPE_IDENTITY() AS bigint);")){
            s.setLong(1,sourceId);s.setString(2,mode);s.setString(3,lower);try(var r=s.executeQuery()){r.next();return r.getLong(1);}
        }
    }
    public void prepareSeen(Connection c)throws SQLException {
        try(var s=c.createStatement()){s.execute("DROP TABLE IF EXISTS #foundation_units_seen; CREATE TABLE #foundation_units_seen(source_id nvarchar(128) COLLATE DATABASE_DEFAULT NOT NULL PRIMARY KEY);");}
    }
    public void addSeen(Connection c,List<UnitChangeDto> items)throws SQLException {
        try(var s=c.prepareStatement("INSERT #foundation_units_seen(source_id) VALUES(?)")){
            for(var row:items){s.setString(1,row.sourceId());s.addBatch();}s.executeBatch();
        }
    }
    public void reconcileMissing(Connection c,long sourceId)throws SQLException {
        try(var s=c.prepareStatement("UPDATE d SET dang_hoat_dong_nguon=0,thoi_diem_cap_nhat="+NOW
                +" FROM dbo.don_vi d WHERE d.he_thong_nguon_id=? AND d.ma_dinh_danh_ben_ngoai IS NOT NULL"
                +" AND NOT EXISTS(SELECT 1 FROM #foundation_units_seen v WHERE v.source_id=d.ma_dinh_danh_ben_ngoai)"
                +" AND (d.dang_hoat_dong_nguon IS NULL OR d.dang_hoat_dong_nguon<>0)")) {
            s.setLong(1,sourceId);s.executeUpdate();
        }
    }
    public void completeRun(Connection c,long runId,String upper,String asOf,int total)throws SQLException {
        try(var s=c.prepareStatement("UPDATE dbo.dot_dong_bo SET con_tro_sau=?,du_lieu_nguon_tinh_den=?,trang_thai=N'success',tong_so_ban_ghi=?,so_ban_ghi_thanh_cong=?,so_ban_ghi_loi=0,thoi_diem_ket_thuc="+NOW+",noi_dung_loi=NULL WHERE id=? AND trang_thai=N'running'")){
            s.setString(1,upper);s.setObject(2,java.time.OffsetDateTime.parse(asOf));s.setInt(3,total);s.setInt(4,total);s.setLong(5,runId);
            if(s.executeUpdate()!=1)throw new IllegalStateException("Sync run is no longer writable");
        }
    }
    public void failRun(Connection c,long runId,String code)throws SQLException {
        try(var s=c.prepareStatement("UPDATE dbo.dot_dong_bo SET trang_thai=N'failed',noi_dung_loi=?,so_ban_ghi_loi=1,thoi_diem_ket_thuc="+NOW+" WHERE id=? AND trang_thai=N'running'")){
            s.setString(1,code);s.setLong(2,runId);s.executeUpdate();
        }
    }
}

