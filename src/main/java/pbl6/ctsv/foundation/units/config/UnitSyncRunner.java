package pbl6.ctsv.foundation.units.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import pbl6.ctsv.foundation.units.dto.BaselineApproval;
import pbl6.ctsv.foundation.units.service.UnitSyncService;

/** Explicit internal operation, no public HTTP trigger, automatic schedule or implicit confirmation. */
public final class UnitSyncRunner implements ApplicationRunner {
    private final UnitSyncService service;
    private final UnitSyncProperties properties;
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    public UnitSyncRunner(UnitSyncService service,UnitSyncProperties properties){this.service=service;this.properties=properties;}
    @Override public void run(ApplicationArguments args)throws Exception {
        switch(properties.operation()){
            case SYNC -> service.sync();
            case PREVIEW -> {
                var scope=service.preview();
                var draft=new LinkedHashMap<String,Object>();
                draft.put("sourceSystemId",scope.sourceSystemId());draft.put("previousEpoch",scope.previousEpoch());
                draft.put("sourceEpoch",scope.sourceEpoch());draft.put("sourceIdentities",scope.sourceIdentities());
                draft.put("targetMappings",scope.targetMappings());draft.put("approvedBy","");
                draft.put("reason","");draft.put("approvedAt",null);
                Files.writeString(properties.reviewFile(),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(draft),
                        StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            }
            case CONFIRMED -> {
                if(!Files.isRegularFile(properties.reviewFile())||Files.size(properties.reviewFile())>8*1024*1024)
                    throw new IllegalArgumentException("Invalid private baseline confirmation file");
                service.sync(mapper.readValue(Files.readAllBytes(properties.reviewFile()),BaselineApproval.class));
            }
        }
    }
}
