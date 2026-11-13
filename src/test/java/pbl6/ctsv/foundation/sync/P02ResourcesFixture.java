package pbl6.ctsv.foundation.sync;
import java.sql.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import pbl6.p01.FaultDatabase;

/** Synthetic only; fixed guarded source/target. No cleanup of unrelated rows. */
final class P02ResourcesFixture {
    static final ObjectMapper JSON=new ObjectMapper();
    static final List<JsonNode> RESOURCES=load();
    static List<JsonNode> load(){try(var in=P02ResourcesFixture.class.getResourceAsStream("/fixtures/p02/resources.json")){
        var result=new ArrayList<JsonNode>();for(var r:JSON.readTree(in).path("resources"))result.add(r);return result;
    }catch(Exception e){throw new IllegalStateException(e);}}
    static JsonNode resource(String name){return RESOURCES.stream().filter(r->r.path("resource").asText().equals(name)).findFirst().orElseThrow();}
    static void seed()throws Exception{
        // Exact fixture probe IDs only; no table-wide delete/reset.
        for(int i=RESOURCES.size()-1;i>=0;i--)deleteProbe(RESOURCES.get(i).path("resource").asText());
        for(var spec:RESOURCES)for(int i=0;i<3;i++)write(spec,spec.path("rows").get(i));
    }
    static void write(JsonNode spec,JsonNode row)throws Exception {
        String table=spec.path("table").asText(),key=spec.path("key").asText();long id=row.path(key).asLong();
        try(var c=FaultDatabase.open()){
            FaultDatabase.guard(c);boolean exists;
            try(var s=c.prepareStatement("SELECT COUNT(*) FROM dbo.["+table+"] WHERE ["+key+"]=?")){s.setLong(1,id);try(var r=s.executeQuery()){r.next();exists=r.getInt(1)>0;}}
            var columns=new ArrayList<String>();row.fieldNames().forEachRemaining(columns::add);if(exists)columns.remove(key);
            String sql=exists?"UPDATE dbo.["+table+"] SET "+String.join(",",columns.stream().map(k->"["+k+"]=?").toList())+" WHERE ["+key+"]=?"
                :"INSERT dbo.["+table+"] ("+String.join(",",columns.stream().map(k->"["+k+"]").toList())+") VALUES ("+String.join(",",Collections.nCopies(columns.size(),"?"))+")";
            boolean identity=spec.path("identity").asBoolean()&&!exists;
            if(identity)try(var s=c.createStatement()){s.execute("SET IDENTITY_INSERT dbo.["+table+"] ON");}
            try(var s=c.prepareStatement(sql)){
                int index=1;for(var col:columns){var v=row.path(col);if(v.isNull())s.setObject(index++,null);else if(v.isIntegralNumber())s.setLong(index++,v.longValue());else s.setString(index++,v.asText());}
                if(exists)s.setLong(index,id);s.executeUpdate();
            }finally{if(identity)try(var s=c.createStatement()){s.execute("SET IDENTITY_INSERT dbo.["+table+"] OFF");}}
        }
    }
    static void deleteProbe(String resource)throws Exception {
        var r=resource(resource);try(var c=FaultDatabase.open();var s=c.prepareStatement("DELETE dbo.["+r.path("table").asText()+"] WHERE ["+r.path("key").asText()+"]=?")){
            FaultDatabase.guard(c);s.setLong(1,r.path("rows").get(3).path(r.path("key").asText()).asLong());s.executeUpdate();
        }
    }
}
