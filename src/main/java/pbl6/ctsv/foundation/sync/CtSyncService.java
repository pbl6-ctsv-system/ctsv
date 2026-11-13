package pbl6.ctsv.foundation.sync;
import java.util.*;import com.fasterxml.jackson.databind.*;
/** Resource-independent full bootstrap, incremental, bounded recovery; HTTP never inside a target TX. */
public final class CtSyncService {
 private final DurableSyncStore store;private final CtHttpClient client;private final SyncTarget target;private final long source,maxRows;private final int pageSize;
 public CtSyncService(DurableSyncStore store,CtHttpClient client,SyncTarget target,long source,int pageSize,long maxRows){this.store=store;this.client=client;this.target=target;this.source=source;this.pageSize=pageSize;this.maxRows=maxRows;}
 public DurableSyncStore.Outcome sync(){
  try(var lease=store.acquire(source,target.resource())){
   var checkpoint=store.checkpoint(lease);boolean full=checkpoint==null;int expired=0,recovery=0;
   for(int attempt=0;attempt<3;attempt++){
    UUID run=null;
    try{
     lease.beforeHttp();JsonNode page=full?client.full():client.incremental(checkpoint);
     if(!page.path("mode").asText().equals(full?"full":"incremental"))throw new IllegalArgumentException("Unexpected sync mode");
     if(checkpoint==null&&target.hasMappings(lease.connection,source))throw new IllegalStateException("BASELINE_CONFIRMATION_REQUIRED");
     run=store.beginRun(lease,page);int pageNo=0;long count=0;String usedCursor=null;
     while(true){
      if(page.path("items").size()>pageSize||page.path("items").isEmpty()&&!page.path("next_cursor").isNull())throw new IllegalArgumentException("Invalid source page size");
      count+=page.path("items").size();if(count>maxRows)throw new IllegalArgumentException("Source row bound exceeded");
      var result=store.page(lease,run,++pageNo,usedCursor,page,target);if(result.complete())return result;
      usedCursor=page.path("next_cursor").textValue();lease.beforeHttp();page=client.next(usedCursor);
     }
    }catch(Exception e){
     if(run!=null)store.fail(lease,run);
     if(e instanceof CtHttpClient.RemoteFailure r){if("EXPORT_EXPIRED".equals(r.code)&&expired++<1)continue;if("FULL_SYNC_REQUIRED".equals(r.code)&&!full&&recovery++<1){full=true;continue;}}
     if(e instanceof RuntimeException r)throw r;throw new IllegalStateException("CT consumer failed; checkpoint preserved",e);
    }
   }throw new IllegalStateException("Bounded recovery exhausted");
  }catch(java.sql.SQLException e){throw new IllegalStateException("Target database unavailable",e);}
 }
}
