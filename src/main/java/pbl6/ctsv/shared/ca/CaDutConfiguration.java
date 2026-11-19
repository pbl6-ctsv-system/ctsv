package pbl6.ctsv.shared.ca;

import java.net.URI;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** Opt-in adapter, independent of datasource and business signing orchestration (P11). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name="ca-dut.enabled",havingValue="true")
public class CaDutConfiguration {
    @Bean
    CaDutClient caDutClient(@Value("${CA_DUT_URL:https://chukyso.dut.udn.vn}") String url,
                            @Value("${CA_DUT_CLIENT_ID:}") String id,
                            @Value("${CA_DUT_CLIENT_SECRET:}") String secret) {
        return new CaDutClient(URI.create(url),id,secret,Clock.systemUTC());
    }
}
