package com.network.network_monitor.util;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mã hóa/giải mã credential SNMP bằng AES-GCM (v2) và hỗ trợ giải mã ngược AES-CBC (legacy).
 * Fail-fast khi SNMP_ENCRYPTION_KEY thiếu hoặc không hợp lệ (độ dài < 16 hoặc chứa changeme).
 * Tuyệt đối không log, không trả về plaintext trong DTO, API hay HTML.
 */
@Component
public class SnmpCredentialEncryptor {

    private static final String GCM_ALGORITHM = "AES/GCM/NoPadding";
    private static final String LEGACY_CBC_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";

    private static final int GCM_IV_LENGTH = 12; // 96-bit IV chuẩn cho AES-GCM
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int CBC_IV_LENGTH = 16;
    private static final String VERSION_PREFIX = "v2:";

    @Value("${app.snmp.encryption-key:}")
    private String encryptionKeyRaw;

    private byte[] keyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public synchronized void validateAndInitKey() {
        if (keyBytes != null) return;

        if (encryptionKeyRaw == null || encryptionKeyRaw.trim().isEmpty()) {
            throw new IllegalStateException("Cấu hình SNMP_ENCRYPTION_KEY bị thiếu: Vui lòng cấu hình biến môi trường SNMP_ENCRYPTION_KEY hợp lệ.");
        }
        String trimmed = encryptionKeyRaw.trim();
        if (trimmed.length() < 16) {
            throw new IllegalStateException("SNMP_ENCRYPTION_KEY không an toàn: Độ dài khóa mã hóa phải tối thiểu 16 ký tự (hiện tại: " + trimmed.length() + ").");
        }
        if (trimmed.toLowerCase().contains("changeme")) {
            throw new IllegalStateException("SNMP_ENCRYPTION_KEY không an toàn: Không được sử dụng khóa mặc định chứa 'changeme'.");
        }

        byte[] raw = trimmed.getBytes(StandardCharsets.UTF_8);
        keyBytes = new byte[16];
        System.arraycopy(raw, 0, keyBytes, 0, Math.min(raw.length, 16));
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        validateAndInitKey();

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(GCM_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, KEY_ALGORITHM), spec);

            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[GCM_IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, GCM_IV_LENGTH, encrypted.length);

            return VERSION_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Lỗi mã hóa SNMP credential với AES-GCM", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) return null;
        validateAndInitKey();

        try {
            if (ciphertext.startsWith(VERSION_PREFIX)) {
                // Giải mã AES-GCM định dạng mới v2
                String payload = ciphertext.substring(VERSION_PREFIX.length());
                byte[] combined = Base64.getDecoder().decode(payload);
                if (combined.length < GCM_IV_LENGTH) {
                    throw new IllegalArgumentException("Dữ liệu ciphertext v2 không hợp lệ");
                }

                byte[] iv = new byte[GCM_IV_LENGTH];
                byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
                System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
                System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

                Cipher cipher = Cipher.getInstance(GCM_ALGORITHM);
                GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, KEY_ALGORITHM), spec);

                return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            } else {
                // Tương thích ngược: Giải mã AES-CBC dữ liệu cũ
                byte[] combined = Base64.getDecoder().decode(ciphertext);
                if (combined.length < CBC_IV_LENGTH) {
                    throw new IllegalArgumentException("Dữ liệu ciphertext legacy không hợp lệ");
                }

                byte[] iv = new byte[CBC_IV_LENGTH];
                byte[] encrypted = new byte[combined.length - CBC_IV_LENGTH];
                System.arraycopy(combined, 0, iv, 0, CBC_IV_LENGTH);
                System.arraycopy(combined, CBC_IV_LENGTH, encrypted, 0, encrypted.length);

                Cipher cipher = Cipher.getInstance(LEGACY_CBC_ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, KEY_ALGORITHM), new IvParameterSpec(iv));

                return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Lỗi giải mã SNMP credential", e);
        }
    }
}
