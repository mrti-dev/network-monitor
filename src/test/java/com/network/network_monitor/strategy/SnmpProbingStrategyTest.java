package com.network.network_monitor.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.network.network_monitor.config.SnmpProperties;
import com.network.network_monitor.util.SnmpCredentialEncryptor;

class SnmpProbingStrategyTest {

    private SnmpProbingStrategy strategy;

    @BeforeEach
    void setUp() {
        SnmpCredentialEncryptor encryptor = new SnmpCredentialEncryptor();
        SnmpProperties props = new SnmpProperties();
        strategy = new SnmpProbingStrategy(encryptor, props);
    }

    @Test
    void counterDelta_normalIncrement() {
        long delta = ReflectionTestUtils.invokeMethod(strategy, "counterDelta", 1500L, 1000L);
        assertThat(delta).isEqualTo(500L);
    }

    @Test
    void counterDelta_32BitWrap_correctlyCalculated() {
        // COUNTER32_WRAP = 4294967296L
        // previous = 4294967200, current = 100 => delta = (4294967296 - 4294967200) + 100 = 96 + 100 = 196
        long delta = ReflectionTestUtils.invokeMethod(strategy, "counterDelta", 100L, 4294967200L);
        assertThat(delta).isEqualTo(196L);
    }

    @Test
    void counterDelta_counterResetAnomaly_returnsMinusOne() {
        // previous = 1000000, current = 10 => delta = 4294967296 - 1000000 + 10 > COUNTER32_WRAP / 2
        long delta = ReflectionTestUtils.invokeMethod(strategy, "counterDelta", 10L, 1000000L);
        assertThat(delta).isEqualTo(-1L);
    }
}
