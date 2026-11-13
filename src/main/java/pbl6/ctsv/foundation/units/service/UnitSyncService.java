package pbl6.ctsv.foundation.units.service;

import java.sql.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import pbl6.ctsv.foundation.units.client.SisUnitsClient;
import pbl6.ctsv.foundation.units.dto.UnitExportResponse;
import pbl6.ctsv.foundation.units.dto.BaselineApproval;
import pbl6.ctsv.foundation.units.repository.UnitSyncRepository;
import pbl6.ctsv.foundation.units.validation.UnitContractValidator;
import pbl6.ctsv.shared.audit.AuditWriter;

/** No public sync endpoint and no database transaction is held during source HTTP. */
public class UnitSyncService {
    public record Result(long runId,String checkpoint,int processed) {}
    public record BaselineScope(long sourceSystemId,UUID previousEpoch,UUID sourceEpoch,Map<String,String> sourceIdentities,Map<String,Long> targetMappings) {}
    public record Checkpoint(UUID epoch,long version) {
        public Checkpoint {if(epoch==null||version<0)throw new IllegalArgumentException("Invalid checkpoint");}
        public static Checkpoint parse(String value) {
            if(value==null)return null;
            try {
                String[] parts=value.split(":",-1);
                if(parts.length!=2||parts[0].length()!=36||!parts[1].matches("0|[1-9][0-9]{0,18}"))throw new IllegalArgumentException();
                return new Checkpoint(UUID.fromString(parts[0]),Long.parseLong(parts[1]));
            }catch(RuntimeException e){throw new IllegalStateException("Stored units checkpoint requires inspection");}
        }
        @Override public String toString(){return epoch+":"+version;}
    }
    protected final UnitSyncGuard guard;
    protected final UnitSyncRepository repository;
    protected final SisUnitsClient client;
    protected final long sourceId;
    protected final int pageSize;
    private final UnitContractValidator validator=new UnitContractValidator();
    private final ObjectMapper mapper=new ObjectMapper();
    public UnitSyncService(UnitSyncGuard guard,UnitSyncRepository repository,SisUnitsClient client,long sourceId,int pageSize) {
        if(sourceId<1||pageSize<1||pageSize>500)throw new IllegalArgumentException("Invalid units sync configuration");
        this.guard=guard;this.repository=repository;this.client=client;this.sourceId=sourceId;this.pageSize=pageSize;
    }
    protected void fault(String stage,Connection connection)throws SQLException {}
    public Result sync(){return sync(null);}
    public Result sync(BaselineApproval approval) {
        try(var locked=guard.acquire(sourceId)) {
            Connection connection=locked.connection();
            repository.requireSource(connection,sourceId);repository.interruptOldRuns(connection,sourceId);
            String initial=repository.checkpoint(connection,sourceId);
            Checkpoint checkpoint=Checkpoint.parse(initial);
            UUID trustedEpoch=approval!=null?approval.sourceEpoch():checkpoint==null?null:checkpoint.epoch();
            boolean requireConfirmation=checkpoint==null&&repository.hasMappings(connection,sourceId);
            boolean full=checkpoint==null||approval!=null;int expiredRestarts=0,fullRecoveries=0;
            for(int attempt=0;attempt<3;attempt++) {
                long run=repository.startRun(connection,sourceId,full?"full":"incremental",initial);
                try {
                    repository.prepareSeen(connection);
                    locked.beforeHttp();fault("http.beforeRequest",connection);
                    var page=full?client.full():client.incremental(checkpoint.epoch(),checkpoint.version());
                    validatePage(page);
                    UUID epoch=UUID.fromString(page.sourceEpoch());
                    if(trustedEpoch!=null&&!trustedEpoch.equals(epoch))throw new IllegalStateException("SOURCE_EPOCH_CHANGED");
                    if(checkpoint!=null&&checkpoint.epoch().equals(epoch)&&Long.parseLong(page.toVersion())<checkpoint.version())
                        throw new IllegalStateException("SOURCE_LINEAGE_INVALID");
                    if(requireConfirmation&&approval==null)throw new IllegalStateException("BASELINE_CONFIRMATION_REQUIRED");
                    if(trustedEpoch==null)trustedEpoch=epoch;
                    if(!page.mode().equals(full?"full":"incremental"))throw new IllegalArgumentException("Unexpected sync mode");
                    if(!full&&!page.fromVersion().equals(Long.toString(checkpoint.version())))throw new IllegalArgumentException("Unexpected lower version");
                    if(approval!=null){
                        var scope=scope(locked,page,checkpoint);
                        if(approval.sourceSystemId()!=sourceId||!Objects.equals(approval.previousEpoch(),scope.previousEpoch())
                                ||!approval.sourceEpoch().equals(scope.sourceEpoch())
                                ||!approval.sourceIdentities().equals(scope.sourceIdentities())||!approval.targetMappings().equals(scope.targetMappings()))
                            throw new IllegalStateException("BASELINE_CONFIRMATION_SCOPE_CHANGED");
                        locked.begin();
                        try {
                            Map<String,String> details=new LinkedHashMap<>();
                            details.put("source_epoch.old",checkpoint==null?null:checkpoint.epoch().toString());
                            details.put("source_epoch.new",epoch.toString());details.put("operator_alias",approval.approvedBy());
                            details.put("operator_confirmed_at",approval.approvedAt().toString());
                            approval.sourceIdentities().forEach((id,code)->details.put("source_identity:"+id,code));
                            approval.targetMappings().forEach((id,local)->details.put("local_mapping:"+id,local.toString()));
                            new AuditWriter().append(connection,sourceId,"CONFIRM_BASELINE","dot_dong_bo",Long.toString(run),approval.reason(),details);
                            connection.commit();
                        }catch(Exception e){connection.rollback();throw e;}
                        finally{connection.setAutoCommit(true);}
                    }
                    UnitExportResponse anchor=new UnitExportResponse(page.mode(),page.sourceEpoch(),page.exportId(),
                            page.fromVersion(),page.toVersion(),page.asOf(),List.of(),null);
                    Set<String> seen=new HashSet<>();long previous=0;int processed=0;
                    while(true) {
                        validatePage(page);sameExport(anchor,page);
                        for(var row:page.items()){
                            long id=Long.parseLong(row.sourceId());
                            if(id<=previous||!seen.add(row.sourceId())||seen.size()>10000)throw new IllegalArgumentException("Source order/count changed");
                            previous=id;
                            if(approval!=null&&!Objects.equals(approval.sourceIdentities().get(row.sourceId()),row.data().unitCode()))
                                throw new IllegalStateException("BASELINE_CONFIRMATION_SCOPE_CHANGED");
                        }
                        int total=processed+page.items().size();boolean last=page.nextCursor()==null;
                        locked.begin();
                        try {
                            if(last&&!Objects.equals(initial,repository.checkpoint(connection,sourceId)))
                                throw new IllegalStateException("Successful checkpoint changed during sync");
                            repository.upsertPage(connection,sourceId,page.items());
                            if(full)repository.addSeen(connection,page.items());
                            fault("page.afterWrite",connection);
                            if(last) {
                                if(approval!=null&&!seen.equals(approval.sourceIdentities().keySet()))throw new IllegalStateException("BASELINE_CONFIRMATION_SCOPE_CHANGED");
                                if(full)repository.reconcileMissing(connection,sourceId);
                                fault("final.afterReconcile",connection);
                                String upper=new Checkpoint(epoch,Long.parseLong(anchor.toVersion())).toString();
                                repository.completeRun(connection,run,upper,anchor.asOf(),total);
                                fault("final.afterCheckpoint",connection);
                            } else repository.progress(connection,run,total);
                            connection.commit();
                        }catch(Exception e){connection.rollback();throw e;}
                        finally{connection.setAutoCommit(true);}
                        processed=total;
                        fault("page.afterCommit",connection);
                        if(last)return new Result(run,new Checkpoint(epoch,Long.parseLong(anchor.toVersion())).toString(),processed);
                        // Only the opaque cursor and fixed metadata survive to the next page.
                        locked.beforeHttp();fault("http.beforeRequest",connection);
                        page=client.next(page.nextCursor());
                    }
                }catch(Exception e){
                    markFailure(connection,run,e instanceof SisUnitsClient.RemoteFailure remote?remote.code():"Sync failed; inspect evidence");
                    if(e instanceof SisUnitsClient.RemoteFailure remote) {
                        if(remote.code().equals("EXPORT_EXPIRED")&&expiredRestarts++<1)continue;
                        if(remote.code().equals("FULL_SYNC_REQUIRED")&&!full&&fullRecoveries++<1){full=true;continue;}
                    }
                    if(e instanceof RuntimeException runtime)throw runtime;
                    throw new IllegalStateException("Units sync failed",e);
                }
            }
            throw new IllegalStateException("Units recovery attempts exhausted");
        }catch(SQLException e){throw new IllegalStateException("Units database connection unavailable",e);}
    }
    public BaselineScope preview() {
        try(var locked=guard.acquire(sourceId)){
            repository.requireSource(locked.connection(),sourceId);locked.beforeHttp();
            return scope(locked,client.full(),Checkpoint.parse(repository.checkpoint(locked.connection(),sourceId)));
        }catch(SQLException e){throw new IllegalStateException("Cannot inspect units baseline",e);}
    }
    private BaselineScope scope(UnitSyncGuard.Handle locked,UnitExportResponse first,Checkpoint previous)throws SQLException {
        if(!"full".equals(first.mode()))throw new IllegalArgumentException("Identity review requires full baseline");
        UnitExportResponse anchor=new UnitExportResponse(first.mode(),first.sourceEpoch(),first.exportId(),first.fromVersion(),first.toVersion(),first.asOf(),List.of(),null);
        Map<String,String> identities=new HashMap<>();UnitExportResponse page=first;long last=0;
        while(true){
            validatePage(page);sameExport(anchor,page);
            for(var row:page.items()){
                long id=Long.parseLong(row.sourceId());
                if(id<=last||identities.size()>=10000)throw new IllegalArgumentException("Invalid full identity scope");
                last=id;identities.put(row.sourceId(),row.data().unitCode());
            }
            if(page.nextCursor()==null)break;
            locked.beforeHttp();fault("http.beforeRequest",locked.connection());page=client.next(page.nextCursor());
        }
        return new BaselineScope(sourceId,previous==null?null:previous.epoch(),UUID.fromString(anchor.sourceEpoch()),Map.copyOf(identities),
                repository.projectedMappings(locked.connection(),sourceId,identities.keySet()));
    }
    private void validatePage(UnitExportResponse page) {
        if(page==null||page.items().size()>pageSize||(page.nextCursor()!=null&&page.items().isEmpty()))
            throw new IllegalArgumentException("Invalid SIS page size");
        validator.validate(mapper.valueToTree(page));
    }
    private static void sameExport(UnitExportResponse expected,UnitExportResponse page) {
        if(!Objects.equals(expected.mode(),page.mode())||!Objects.equals(expected.sourceEpoch(),page.sourceEpoch())
                ||!Objects.equals(expected.exportId(),page.exportId())||!Objects.equals(expected.fromVersion(),page.fromVersion())
                ||!Objects.equals(expected.toVersion(),page.toVersion())||!Objects.equals(expected.asOf(),page.asOf()))
            throw new IllegalArgumentException("SIS export metadata changed between pages");
    }
    private void markFailure(Connection c,long run, String reason) {
        try {
            if(!c.isClosed()&&c.isValid(2)){
                if(!c.getAutoCommit()){c.rollback();c.setAutoCommit(true);}
                repository.failRun(c,run,reason);
            }
        }catch(SQLException ignored){/* A lost guarded connection is never replaced. Next run marks interruption. */}
    }
}

