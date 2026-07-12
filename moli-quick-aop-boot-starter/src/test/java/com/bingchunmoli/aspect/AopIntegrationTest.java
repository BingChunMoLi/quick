package com.bingchunmoli.aspect;

import com.bingchunmoli.annotation.EnableAop;
import com.bingchunmoli.annotation.ExecutionTime;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AopIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class))
            .withUserConfiguration(TestAopConfiguration.class);

    @Test
    void enableAopShouldImportAspectsAndProxyAnnotatedMethodsForBoot406() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LogAspect.class);
            assertThat(context).hasSingleBean(ExecutionAspect.class);
            assertThat(context).hasSingleBean(TimedService.class);

            TimedService service = context.getBean(TimedService.class);
            assertThat(AopUtils.isAopProxy(service)).isTrue();
            assertThat(service.execute("moli")).isEqualTo("hello moli");
        });
    }

    @EnableAop
    @Configuration(proxyBeanMethods = false)
    static class TestAopConfiguration {

        @Bean
        TimedService timedService() {
            return new TimedService();
        }
    }

    static class TimedService {

        @ExecutionTime("timedService")
        String execute(String name) {
            return "hello " + name;
        }
    }
}
