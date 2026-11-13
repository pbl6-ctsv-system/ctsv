package pbl6.ctsv.dev;
import java.util.*;
/** Direct guard checks, no database connections or mock engine. */
public final class SyncDevChecks {
    public static void main(String[] args){
        var row=new HashMap<String,Object>(Map.of("db","sis_dev","instance","DEV2022","owner","local-user","login","local-user","compat",(short)160,"collation","Vietnamese_100_CI_AS_SC"));
        SyncDev.verifyIdentity("sis_dev",row);int checks=1;
        for(var entry:Map.<String,Object>of("db","sis_test_20261111","instance","SQLEXPRESS","owner","another-user","compat",(short)150,"collation","other").entrySet()){
            var bad=new HashMap<>(row);bad.put(entry.getKey(),entry.getValue());boolean rejected=false;
            try{SyncDev.verifyIdentity("sis_dev",bad);}catch(IllegalStateException expected){rejected=true;}
            if(!rejected)throw new AssertionError("Unsafe identity accepted: "+entry.getKey());checks++;
        }
        System.out.println("Sync Java guard checks: "+checks+" PASS");
    }
}
