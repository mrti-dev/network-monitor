package com.network.network_monitor.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class MonitoringDefaultsTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MonitoringDefaults.class)
    static class Config {}

    @ParameterizedTest
    @ValueSource(strings = {"retention-days=0", "retention-days=-1", "ping-interval=0", "timeout-ms=-1", "latency-threshold=0", "timeout-ms=60001"})
    void invalidConfigurationRejectsStartup(String property) {
        runner.withPropertyValues("app.monitoring." + property)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void defaultsAreValid() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MonitoringDefaults.class).getRetentionDays()).isEqualTo(7);
        });
    }
}
