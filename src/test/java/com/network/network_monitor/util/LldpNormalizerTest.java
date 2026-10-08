package com.network.network_monitor.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LldpNormalizerTest {

    @Test
    void normalizeMac_formatsCorrectly() {
        assertThat(LldpNormalizer.normalizeMac("001122334455")).isEqualTo("00:11:22:33:44:55");
        assertThat(LldpNormalizer.normalizeMac("00-11-22-33-44-55")).isEqualTo("00:11:22:33:44:55");
        assertThat(LldpNormalizer.normalizeMac("0011.2233.4455")).isEqualTo("00:11:22:33:44:55");
        assertThat(LldpNormalizer.normalizeMac("00:11:22:33:44:55")).isEqualTo("00:11:22:33:44:55");
        assertThat(LldpNormalizer.normalizeMac("invalid-mac")).isNull();
        assertThat(LldpNormalizer.normalizeMac(null)).isNull();
    }

    @Test
    void macFromBytes_convertsHexRepresentation() {
        byte[] bytes = new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD, (byte) 0xEE, (byte) 0xFF};
        assertThat(LldpNormalizer.macFromBytes(bytes)).isEqualTo("aa:bb:cc:dd:ee:ff");
        assertThat(LldpNormalizer.macFromBytes(new byte[]{1, 2, 3})).isNull();
        assertThat(LldpNormalizer.macFromBytes(null)).isNull();
    }

    @Test
    void normalizeIp_stripsPortAndWhitespace() {
        assertThat(LldpNormalizer.normalizeIp(" 192.168.1.1 ")).isEqualTo("192.168.1.1");
        assertThat(LldpNormalizer.normalizeIp("192.168.1.1:161")).isEqualTo("192.168.1.1");
        assertThat(LldpNormalizer.normalizeIp("")).isNull();
        assertThat(LldpNormalizer.normalizeIp(null)).isNull();
    }

    @Test
    void normalizeHostname_andExtractShortHostname() {
        assertThat(LldpNormalizer.normalizeHostname(" Core-Switch-01.Corp.Local ")).isEqualTo("core-switch-01.corp.local");
        assertThat(LldpNormalizer.extractShortHostname(" Core-Switch-01.Corp.Local ")).isEqualTo("core-switch-01");
        assertThat(LldpNormalizer.extractShortHostname("Router-Main")).isEqualTo("router-main");
        assertThat(LldpNormalizer.extractShortHostname(null)).isNull();
    }
}
