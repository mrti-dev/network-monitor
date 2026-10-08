package com.network.network_monitor.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SnmpCredentialEncryptorTest {

    private SnmpCredentialEncryptor encryptor;
    private static final String VALID_KEY = "12345678901234567890123456789012";

    @BeforeEach
    void setUp() {
        encryptor = new SnmpCredentialEncryptor();
        ReflectionTestUtils.setField(encryptor, "encryptionKeyRaw", VALID_KEY);
    }

    @Test
    void encryptAndDecrypt_successWithVersionPrefix() {
        String original = "public-secret-community";
        String encrypted = encryptor.encrypt(original);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted).startsWith("v2:");
        assertThat(encrypted).isNotEqualTo(original);

        String decrypted = encryptor.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void encrypt_twoCallsGenerateDifferentCiphertext_dueToRandomIv() {
        String text = "cisco-community";
        String enc1 = encryptor.encrypt(text);
        String enc2 = encryptor.encrypt(text);

        assertThat(enc1).isNotEqualTo(enc2);
        assertThat(enc1).startsWith("v2:");
        assertThat(enc2).startsWith("v2:");
        assertThat(encryptor.decrypt(enc1)).isEqualTo(text);
        assertThat(encryptor.decrypt(enc2)).isEqualTo(text);
    }

    @Test
    void decrypt_legacyAesCbc_backwardCompatible() throws Exception {
        // Tạo ciphertext legacy AES-CBC thủ công bằng key VALID_KEY
        byte[] keyBytes = new byte[16];
        System.arraycopy(VALID_KEY.getBytes(StandardCharsets.UTF_8), 0, keyBytes, 0, 16);
        byte[] iv = new byte[16];
        new java.security.SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(iv));
        byte[] enc = cipher.doFinal("legacy-secret".getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[16 + enc.length];
        System.arraycopy(iv, 0, combined, 0, 16);
        System.arraycopy(enc, 0, combined, 16, enc.length);
        String legacyCiphertext = Base64.getEncoder().encodeToString(combined);

        // Giải mã qua encryptor mới
        String decrypted = encryptor.decrypt(legacyCiphertext);
        assertThat(decrypted).isEqualTo("legacy-secret");
    }

    @Test
    void missingOrInvalidKey_failFast() {
        SnmpCredentialEncryptor missingKeyEncryptor = new SnmpCredentialEncryptor();
        ReflectionTestUtils.setField(missingKeyEncryptor, "encryptionKeyRaw", "");
        assertThatThrownBy(() -> missingKeyEncryptor.encrypt("secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SNMP_ENCRYPTION_KEY");

        SnmpCredentialEncryptor shortKeyEncryptor = new SnmpCredentialEncryptor();
        ReflectionTestUtils.setField(shortKeyEncryptor, "encryptionKeyRaw", "short-key");
        assertThatThrownBy(() -> shortKeyEncryptor.encrypt("secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("không an toàn");

        SnmpCredentialEncryptor changemeKeyEncryptor = new SnmpCredentialEncryptor();
        ReflectionTestUtils.setField(changemeKeyEncryptor, "encryptionKeyRaw", "changeme-32char-default-dev-key!");
        assertThatThrownBy(() -> changemeKeyEncryptor.encrypt("secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changeme");
    }

    @Test
    void nullOrBlank_returnsNull() {
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.encrypt("  ")).isNull();
        assertThat(encryptor.decrypt(null)).isNull();
        assertThat(encryptor.decrypt("  ")).isNull();
    }
}
