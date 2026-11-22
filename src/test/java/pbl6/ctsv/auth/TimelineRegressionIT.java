package pbl6.ctsv.auth;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;

/** Targeted inherited P06–P08 regression, opt-in DS with the independent timeline owner/marker guard. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 classes={P06FileHttpIT.Config.class,TimelineRegressionIT.Config.class},
 properties={"ctsv.auth.enabled=true","ctsv.foundation.enabled=true","ctsv.files.enabled=true",
 "ctsv.mghp.configuration.enabled=true","ctsv.mghp.applications.enabled=true","server.address=127.0.0.1"})
class TimelineRegressionIT extends P06FileHttpIT {
 @Configuration(proxyBeanMethods=false)
 @org.springframework.boot.autoconfigure.EnableAutoConfiguration
 @org.springframework.boot.autoconfigure.domain.EntityScan({"pbl6.ctsv.foundation","pbl6.ctsv.mghp"})
 @ComponentScan(basePackages={"pbl6.ctsv.auth","pbl6.ctsv.foundation","pbl6.ctsv.mghp","pbl6.ctsv.shared"},excludeFilters={
  @ComponentScan.Filter(type=FilterType.CUSTOM,classes=org.springframework.boot.context.TypeExcludeFilter.class),
  @ComponentScan.Filter(type=FilterType.REGEX,pattern=".*(?:Test|IT)(?:\\$.*)?")})
 static class Config {}
}
