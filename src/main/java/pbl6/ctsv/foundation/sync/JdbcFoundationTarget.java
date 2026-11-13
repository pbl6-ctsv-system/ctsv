package pbl6.ctsv.foundation.sync;

import java.sql.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;

/** Owned Foundation columns only; verified semester/process/history and local profile columns are untouched. */
public final class JdbcFoundationTarget implements SyncTarget {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final FoundationResourceCatalog.Resource spec;
    public JdbcFoundationTarget(String resource){spec=FoundationResourceCatalog.resource(resource);}
    public String resource(){return spec.resource();}
    public boolean hasMappings(Connection c,long source)throws SQLException {
        try(var s=c.prepareStatement("SELECT TOP(1) id FROM dbo.["+spec.target()+"] WHERE he_thong_nguon_id=? AND ma_dinh_danh_ben_ngoai IS NOT NULL")){
            s.setLong(1,source);try(var r=s.executeQuery()){return r.next();}
        }
    }
    public Long apply(Connection c,long source,JsonNode item)throws SQLException {
        String sourceId=Long.toString(CtSchema.number(item.path("source_id")));
        Long local=find(c,spec.target(),source,sourceId,false,true);
        if(item.path("operation").asText().equals("delete")){markAbsent(c,source,Long.parseLong(sourceId));return local;}
        JsonNode data=item.path("data");var values=new LinkedHashMap<String,Object>();var canonical=JSON.createObjectNode();boolean needsReference=false;
        for(var f:spec.fields()){
            if(f.json().equals("source_id"))continue;
            JsonNode v=data.get(f.json());if(v==null)throw new IllegalArgumentException("Missing source field: "+f.json());
            canonical.set(f.json(),v);
            Object value=null;
            if(!v.isNull()){
                if(f.parent()!=null){
                    String parentId=Long.toString(CtSchema.number(v));
                    requireSameEpoch(c,source,f.parent());
                    value=find(c,f.parent(),source,parentId,true,false);
                    if(value==null)throw new IllegalStateException("FK_MAPPING_REQUIRED: "+f.json());
                }else {
                    value=convert(v,f.localType());
                    if(f.localType().startsWith("varchar("))verifyVarcharRoundTrip(c,f.localType(),value.toString());
                }
                if(f.ref()!=null)needsReference=true;
            }
            values.put(f.target(),value);
            if(f.raw()!=null)values.put(f.raw(),v.isNull()?null:v.asText());
        }
        values.put("dang_hien_dien_nguon",true);
        values.put("sha256_ban_ghi_nguon",hash(sourceId+"\n"+canonical));
        values.put("phien_ban_anh_xa","ct26-0.1.0");
        values.put("trang_thai_chuan_hoa",needsReference?"needs_reference_verification":"normalized");
        values.put("loi_chuan_hoa",needsReference?"Raw REF retained; no inferred local FK":null);
        String assignments=String.join(",",values.keySet().stream().map(k->"["+k+"]=?").toList());
        if(local!=null){
            try(var s=c.prepareStatement("UPDATE dbo.["+spec.target()+"] SET "+assignments+",thoi_diem_dong_bo=SYSUTCDATETIME(),thoi_diem_cap_nhat=SYSUTCDATETIME() WHERE id=?")){
                int i=1;for(var v:values.values())s.setObject(i++,v);s.setLong(i,local);if(s.executeUpdate()!=1)throw new SQLException("Foundation identity changed");
            }
            return local;
        }
        var cols=new ArrayList<>(values.keySet());cols.add("he_thong_nguon_id");cols.add("ma_dinh_danh_ben_ngoai");
        try(var s=c.prepareStatement("INSERT dbo.["+spec.target()+"] ("+String.join(",",cols.stream().map(k->"["+k+"]").toList())+",thoi_diem_tao,thoi_diem_cap_nhat,thoi_diem_dong_bo) OUTPUT INSERTED.id VALUES ("+String.join(",",Collections.nCopies(cols.size(),"?"))+",SYSUTCDATETIME(),SYSUTCDATETIME(),SYSUTCDATETIME())")){
            int i=1;for(var v:values.values())s.setObject(i++,v);s.setLong(i++,source);s.setString(i,sourceId);try(var r=s.executeQuery()){r.next();return r.getLong(1);}
        }
    }
    private static void verifyVarcharRoundTrip(Connection c,String type,String value)throws SQLException {
        try(var s=c.prepareStatement("SELECT CONVERT(nvarchar(max),CONVERT("+type+",?))")){
            s.setNString(1,value);try(var r=s.executeQuery()){
                if(!r.next()||!value.equals(r.getString(1)))throw new IllegalArgumentException("Source text cannot be stored losslessly in local varchar");
            }
        }
    }
    private static Object convert(JsonNode v,String type){
        if(type.startsWith("decimal")){
            BigDecimal n=new BigDecimal(v.asText());
            if(n.scale()>10||n.precision()-n.scale()>18)throw new IllegalArgumentException("Decimal outside baseline; no rounding allowed");
            return n;
        }
        if(type.equals("date"))return java.sql.Date.valueOf(LocalDate.parse(v.asText()));
        if(type.equals("bigint"))return CtSchema.number(v);
        if(type.equals("int"))return Integer.valueOf(v.asText());
        if(type.equals("smallint"))return Short.valueOf(v.asText());
        if(type.startsWith("nvarchar(")||type.startsWith("varchar(")){
            String text=v.asText(),limit=type.substring(type.indexOf('(')+1,type.length()-1);
            if(!limit.equals("max")&&text.length()>Integer.parseInt(limit))throw new IllegalArgumentException("Source exceeds local string capacity; no truncation allowed");return text;
        }
        throw new IllegalArgumentException("Unsupported pinned local type");
    }
    private static Long find(Connection c,String table,long source,String id,boolean present,boolean lock)throws SQLException {
        String presence=table.equals("don_vi")?"dang_hoat_dong_nguon":"dang_hien_dien_nguon";
        try(var s=c.prepareStatement("SELECT id FROM dbo.["+table+"]"+(lock?" WITH(UPDLOCK,HOLDLOCK)":"")+" WHERE he_thong_nguon_id=? AND ma_dinh_danh_ben_ngoai=?"+(present?" AND "+presence+"=1":""))){
            s.setLong(1,source);s.setString(2,id);try(var r=s.executeQuery()){if(!r.next())return null;long result=r.getLong(1);if(r.next())throw new IllegalStateException("AMBIGUOUS_SOURCE_MAPPING");return result;}
        }
    }
    private void requireSameEpoch(Connection c,long source,String parent)throws SQLException {
        String parentResource=parent.equals("don_vi")?"units":FoundationResourceCatalog.all().stream().filter(r->r.target().equals(parent)).findFirst().orElseThrow().resource();
        String current=null,parentEpoch=null;
        try(var s=c.prepareStatement("SELECT r.source_epoch FROM dbo.dong_bo_luot r JOIN dbo.dong_bo_trang_thai t ON t.he_thong_nguon_id=r.he_thong_nguon_id AND t.tai_nguyen=r.tai_nguyen AND t.the_he=r.the_he AND t.chu_so_huu=r.chu_so_huu WHERE r.he_thong_nguon_id=? AND r.tai_nguyen=? AND r.trang_thai='RUNNING'")){
            s.setLong(1,source);s.setString(2,resource());try(var r=s.executeQuery()){if(r.next())current=r.getString(1);if(r.next())throw new IllegalStateException("Ambiguous active export");}
        }
        if(parentResource.equals("units")){
            try(var s=c.prepareStatement("SELECT TOP(1) con_tro_sau FROM dbo.dot_dong_bo WHERE he_thong_nguon_id=? AND loai_doi_tuong=N'units' AND trang_thai=N'success' ORDER BY thoi_diem_ket_thuc DESC,id DESC")){
                s.setLong(1,source);try(var r=s.executeQuery()){if(r.next())parentEpoch=pbl6.ctsv.foundation.units.service.UnitSyncService.Checkpoint.parse(r.getString(1)).epoch().toString();}
            }
        }else try(var s=c.prepareStatement("SELECT source_epoch FROM dbo.dong_bo_trang_thai WHERE he_thong_nguon_id=? AND tai_nguyen=?")){
            s.setLong(1,source);s.setString(2,parentResource);try(var r=s.executeQuery()){if(r.next())parentEpoch=r.getString(1);}
        }
        if(current==null||parentEpoch==null||!current.equalsIgnoreCase(parentEpoch))throw new IllegalStateException("FK_MAPPING_REQUIRED: parent checkpoint/epoch");
    }
    public void markAbsent(Connection c,long source,long id)throws SQLException {
        try(var s=c.prepareStatement("UPDATE dbo.["+spec.target()+"] SET dang_hien_dien_nguon=0,thoi_diem_cap_nhat=SYSUTCDATETIME() WHERE he_thong_nguon_id=? AND ma_dinh_danh_ben_ngoai=?")){
            s.setLong(1,source);s.setString(2,Long.toString(id));s.executeUpdate();
        }
    }
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
