package pbl6.ctsv.foundation.sync;
import java.nio.file.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import pbl6.p01.FaultDatabase;

/** Actual SIS application and production bindings. Restarts retain the same epoch and real SQL data. */
final class P02ResourceProvider implements AutoCloseable {
    static final Path OUT=Path.of("target","p02-resources");
    final String key=UUID.randomUUID().toString(),denied=UUID.randomUUID().toString();
    final UUID epoch;final Path spool=OUT.resolve("spool-"+UUID.randomUUID());
    Process process;URI uri;
    P02ResourceProvider(){this(UUID.randomUUID());}
    P02ResourceProvider(UUID epoch){this.epoch=epoch;}
    void start()throws Exception {
        close();Files.createDirectories(OUT);
        String cp=Path.of("..","daotao","target","classes").toAbsolutePath()+java.io.File.pathSeparator+System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        Path log=OUT.resolve("provider-"+UUID.randomUUID()+".log");
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Xmx512m","-cp",cp,"pbl6.daotao.DaotaoApplication","--spring.profiles.active=local","--server.address=127.0.0.1","--server.port=0","--ct26.enabled=true");
        String jdbc=((org.springframework.jdbc.datasource.SimpleDriverDataSource)FaultDatabase.dataSource()).getUrl()+"integratedSecurity=true;encrypt=true;trustServerCertificate=true;connectRetryCount=0;";
        builder.environment().put("SIS_JDBC_URL",jdbc);builder.environment().put("SIS_SOURCE_EPOCH",epoch.toString());builder.environment().put("SIS_SERVICE_KEY",key);
        builder.environment().put("SPRING_APPLICATION_JSON",new ObjectMapper().writeValueAsString(Map.of("sis",Map.of("ct26",Map.of("sourceEpoch",epoch.toString(),"spoolDirectory",spool.toAbsolutePath().toString(),"serviceKeys",Map.of("ctsv",key,"denied",denied),"serviceResources",Map.of("ctsv",FoundationResourceCatalog.all().stream().map(FoundationResourceCatalog.Resource::resource).toList(),"denied",List.of()))))));
        process=builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        long deadline=System.nanoTime()+Duration.ofSeconds(45).toNanos();
        while(process.isAlive()&&System.nanoTime()<deadline){
            if(Files.exists(log)){String text=Files.readString(log);var matcher=Pattern.compile("Tomcat started on port (\\d+)").matcher(text);if(matcher.find()){
                if(text.contains(key)||text.contains(denied))throw new IllegalStateException("Credential leaked");uri=URI.create("http://127.0.0.1:"+matcher.group(1));return;
            }}Thread.sleep(100);
        }throw new IllegalStateException("SIS startup failed; inspect "+log);
    }
    public void close()throws Exception{if(process!=null&&process.isAlive()){process.destroy();if(!process.waitFor(10,TimeUnit.SECONDS))process.destroyForcibly().waitFor();}}
}
