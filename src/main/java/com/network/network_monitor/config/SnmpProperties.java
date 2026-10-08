package com.network.network_monitor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.snmp")
public class SnmpProperties {
    private int timeoutMs = 3000;
    private int retries = 2;
    private int pollingInterval = 60;
    private int maxConcurrency = 20;
    private String encryptionKey = "changeme-32char-default-dev-key!";
}
