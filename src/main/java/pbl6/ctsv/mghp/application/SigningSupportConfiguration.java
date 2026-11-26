package pbl6.ctsv.mghp.application;
import java.nio.file.Path;import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration(proxyBeanMethods=false) @ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningSupportConfiguration {
 @Bean @ConditionalOnProperty(name="ctsv.mghp.signing.support.monitor.enabled",havingValue="true",matchIfMissing=true)
 SigningSupportMaintenance signingSupportMaintenance(SigningSupportJournal journal){return new SigningSupportMaintenance(journal);}
 @Bean @ConditionalOnProperty(name="ctsv.mghp.signing.support.monitor.enabled",havingValue="true",matchIfMissing=true)
 SigningCertificateMonitor signingCertificateMonitor(SigningCertificateService certificates){return new SigningCertificateMonitor(certificates);}
 @Bean SigningResultSpool signingResultSpool(Environment env,Clock clock){
  String root=env.getProperty("ctsv.mghp.signing.result-spool-dir",Path.of(System.getProperty("user.home"),".ctsv","signing-results").toString());
  return new SigningResultSpool(Path.of(root),clock);
 }
}
