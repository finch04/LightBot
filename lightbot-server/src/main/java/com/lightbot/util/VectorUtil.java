package com.lightbot.util;

/**
 * 向量工具类：float[] 与 pgvector 字符串格式互转
 *
 * @author finch
 * @since 2026-06-25
 */
public final class VectorUtil {

    private VectorUtil() {}

    /**
     * 将float数组转换为pgvector可识别的字符串格式 "[0.1,0.2,...]"
     *
     * @param vector float数组
     * @return pgvector格式字符串
     */
    public static String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
