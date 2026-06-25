package com.lightbot.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 哈希计算工具类：统一 MD5 / SHA-256 实现
 *
 * @author finch
 * @since 2026-06-25
 */
public final class HashUtil {

    private HashUtil() {}

    /**
     * 计算字节数组的 MD5 哈希（十六进制小写）
     *
     * @param data 原始字节
     * @return 32位十六进制哈希字符串
     */
    public static String md5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(data);
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("MD5 计算失败", e);
        }
    }

    /**
     * 计算字符串的 SHA-256 哈希（十六进制小写）
     *
     * @param input 原始字符串
     * @return 64位十六进制哈希字符串
     */
    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
