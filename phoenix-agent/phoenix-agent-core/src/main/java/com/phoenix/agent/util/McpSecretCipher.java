package com.phoenix.agent.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * MCP 敏感配置加解密与脱敏（R-05）。
 * AES-GCM，密钥=SHA-256(环境变量 PHOENIX_MCP_CIPHER_KEY，缺省 dev 键——生产必须配置)；
 * 密文形态 enc:v1:<b64(iv+ct)>；掩码与平台 api_key 先例一致（前3+****+后4，短值 ****）。
 */
public final class McpSecretCipher {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private McpSecretCipher() {
    }

    private static SecretKeySpec key() {
        String secret = System.getenv().getOrDefault("PHOENIX_MCP_CIPHER_KEY", "phoenix-mcp-default-dev-key");
        try {
            byte[] k = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(k, "AES");
        }
        catch (Exception e) {
            throw new IllegalStateException("MCP cipher key init failed", e);
        }
    }

    public static String encrypt(String plain) {
        if (plain == null || plain.isBlank()) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        }
        catch (Exception e) {
            throw new IllegalStateException("MCP secret encrypt failed", e);
        }
    }

    public static String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(in, 0, iv, 0, IV_LEN);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = c.doFinal(in, IV_LEN, in.length - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        }
        catch (Exception e) {
            throw new IllegalStateException("MCP secret decrypt failed（密钥变更？配置 PHOENIX_MCP_CIPHER_KEY）", e);
        }
    }

    /** 与平台 api_key 掩码先例一致（ModelConfigOpsService.maskApiKey 同构）。 */
    public static String mask(String plain) {
        if (plain == null || plain.isBlank()) {
            return plain;
        }
        String t = plain.trim();
        return t.length() <= 7 ? "****" : t.substring(0, 3) + "****" + t.substring(t.length() - 4);
    }

    public static boolean isMasked(String v) {
        return v != null && v.contains("****");
    }
}
