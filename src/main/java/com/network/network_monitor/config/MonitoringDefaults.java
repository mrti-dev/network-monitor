package com.network.network_monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.monitoring")
public class MonitoringDefaults {
    @Positive @Max(86400)
    private int pingInterval = 15;
    @Positive @Max(60000)
    private int timeoutMs = 2000;
    @Positive @Max(60000)
    private double latencyThreshold = 150;
    @Positive @Max(3650)
    private int retentionDays = 7;
}
