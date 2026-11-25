package pbl6.ctsv;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CtsvApplicationTests {

    @Autowired ApplicationContext context;

    @Test
    void contextLoads() {
    }

    @Test
    void excludesTestOnlyComponentsFromApplicationScan() {
        assertThat(context.getBeansWithAnnotation(TestComponent.class)).isEmpty();
        assertThat(context.containsBean("multipartParsingAttempts")).isFalse();
        assertThat(context.containsBean("multipartProbe")).isFalse();
    }

}
