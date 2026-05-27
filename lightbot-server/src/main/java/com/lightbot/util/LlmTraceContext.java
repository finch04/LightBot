package com.lightbot.util;

import java.util.function.Supplier;

/**
 * LLM Trace 记录开关：辅助能力（生成提示词、向量化、TTS 等）调用期间禁止写入 trace。
 */
public final class LlmTraceContext {

    private static final ThreadLocal<Integer> SUPPRESS_DEPTH = ThreadLocal.withInitial(() -> 0);

    private LlmTraceContext() {
    }

    public static boolean isSuppressed() {
        return SUPPRESS_DEPTH.get() > 0;
    }

    public static void runWithoutTrace(Runnable action) {
        enterSuppress();
        try {
            action.run();
        } finally {
            exitSuppress();
        }
    }

    public static <T> T callWithoutTrace(Supplier<T> supplier) {
        enterSuppress();
        try {
            return supplier.get();
        } finally {
            exitSuppress();
        }
    }

    private static void enterSuppress() {
        SUPPRESS_DEPTH.set(SUPPRESS_DEPTH.get() + 1);
    }

    private static void exitSuppress() {
        int depth = SUPPRESS_DEPTH.get() - 1;
        if (depth <= 0) {
            SUPPRESS_DEPTH.remove();
        } else {
            SUPPRESS_DEPTH.set(depth);
        }
    }
}
