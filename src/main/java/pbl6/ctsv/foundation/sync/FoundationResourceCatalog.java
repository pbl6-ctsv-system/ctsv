package pbl6.ctsv.foundation.sync;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Internal, pinned SQL/JSON/local whitelist. This does not approve Staff or expand Portal DTOs. */
public final class FoundationResourceCatalog {
    public record Field(String sql,String json,String target,String sqlType,String localType,
                        boolean nullable,String parent,String raw,String ref){}
    public record Resource(String resource,String table,String target,List<Field> fields){}
    private static final List<Resource> RESOURCES=load();
    private FoundationResourceCatalog(){}
    private static List<Resource> load(){
        try(var in=FoundationResourceCatalog.class.getResourceAsStream("/contracts/foundation-resources.json")){
            var rows=List.of(new ObjectMapper().readValue(in,Resource[].class));
            if(rows.size()!=23||rows.stream().map(Resource::resource).distinct().count()!=23)throw new IllegalStateException("Invalid P02 mapping inventory");
            for(var r:rows){identifier(r.table);identifier(r.target);for(var f:r.fields){identifier(f.sql);identifier(f.target);if(f.raw!=null)identifier(f.raw);if(f.parent!=null)identifier(f.parent);}}
            return rows;
        }catch(java.io.IOException e){throw new IllegalStateException("Pinned mapping unavailable",e);}
    }
    static void identifier(String value){if(value==null||!value.matches("[A-Za-z_][A-Za-z_0-9]*"))throw new IllegalArgumentException("Invalid mapping identifier");}
    public static List<Resource> all(){return RESOURCES;}
    public static Resource resource(String name){return RESOURCES.stream().filter(r->r.resource.equals(name)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown P02 resource"));}
}
