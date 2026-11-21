package pbl6.ctsv.ca;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import pbl6.ctsv.shared.ca.*;
import static org.assertj.core.api.Assertions.*;

class CaDutConfigurationTest {
    final ApplicationContextRunner context=new ApplicationContextRunner().withUserConfiguration(CaDutConfiguration.class);
    @Test void disabledByDefaultWithoutCredentials() { context.run(c->assertThat(c).doesNotHaveBean(CaDutClient.class)); }
    @Test void enabledRequiresServerSideConfiguration() {context.withPropertyValues("ca-dut.enabled=true").run(c->assertThat(c).hasFailed());}
    @Test void validConfigurationCreatesSingleAdapterWithoutNetworkOrDatabase() {
        context.withPropertyValues("ca-dut.enabled=true","CA_DUT_URL=https://chukyso.dut.udn.vn","CA_DUT_CLIENT_ID=synthetic-id","CA_DUT_CLIENT_SECRET=synthetic-secret")
            .run(c->assertThat(c).hasSingleBean(CaDutClient.class));
    }
}
