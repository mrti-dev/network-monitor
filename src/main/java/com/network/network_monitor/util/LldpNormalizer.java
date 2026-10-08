package com.network.network_monitor.util;

import java.util.Locale;

public final class LldpNormalizer {

    private LldpNormalizer() {}

    public static String normalizeMac(String mac) {
        if (mac == null) return null;
        String clean = mac.trim().replaceAll("[^a-fA-F0-9]", "").toLowerCase(Locale.ROOT);
        if (clean.length() != 12) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i += 2) {
            if (i > 0) sb.append(':');
            sb.append(clean, i, i + 2);
        }
        return sb.toString();
    }

    public static String macFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != 6) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format(Locale.ROOT, "%02x", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    public static String normalizeIp(String ip) {
        if (ip == null) return null;
        String trimmed = ip.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.contains(":") && !trimmed.contains("::")) {
            String[] parts = trimmed.split(":");
            if (parts.length == 2 && !parts[0].isEmpty()) {
                trimmed = parts[0];
            }
        }
        return trimmed;
    }

    public static String normalizeHostname(String name) {
        if (name == null) return null;
        String trimmed = name.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String extractShortHostname(String name) {
        String normalized = normalizeHostname(name);
        if (normalized == null) return null;
        int dotIdx = normalized.indexOf('.');
        return dotIdx > 0 ? normalized.substring(0, dotIdx) : normalized;
    }

    public static String normalizePort(String port) {
        if (port == null) return "";
        return port.trim();
    }
}
