package com.network.network_monitor.discovery;

import com.network.network_monitor.enums.DeviceType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class MacOuiDeviceTypeResolver {

    private static final String DATABASE = "mac-oui-device-types.csv";
    private final Map<String, DeviceType> typesByOui;

    public MacOuiDeviceTypeResolver() {
        typesByOui = loadDatabase();
    }

    public Optional<DeviceType> resolve(String macAddress) {
        if (macAddress == null) {
            return Optional.empty();
        }
        String normalized = macAddress.replaceAll("[^0-9A-Fa-f]", "").toUpperCase();
        if (normalized.length() != 12) {
            return Optional.empty();
        }
        int firstOctet = Integer.parseInt(normalized.substring(0, 2), 16);
        if ((firstOctet & 0x03) != 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(typesByOui.get(normalized.substring(0, 6)));
    }

    private Map<String, DeviceType> loadDatabase() {
        Map<String, DeviceType> mappings = new HashMap<>();
        ClassPathResource resource = new ClassPathResource(DATABASE);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(line -> addMapping(mappings, line));
        } catch (IOException e) {
            throw new IllegalStateException("Không thể đọc bảng MAC OUI " + DATABASE, e);
        }
        return Map.copyOf(mappings);
    }

    private void addMapping(Map<String, DeviceType> mappings, String line) {
        String[] columns = line.split(",", -1);
        if (columns.length != 2 || !columns[0].matches("[0-9A-Fa-f]{6}")) {
            throw new IllegalStateException("Dòng MAC OUI không hợp lệ: " + line);
        }
        String oui = columns[0].toUpperCase();
        DeviceType type = DeviceType.valueOf(columns[1].trim().toUpperCase());
        if (mappings.putIfAbsent(oui, type) != null) {
            throw new IllegalStateException("MAC OUI bị trùng: " + oui);
        }
    }
}
