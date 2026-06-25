package com.lightbot.service.sandbox;

import java.util.Set;

/**
 * 沙盒类加载器：仅允许加载白名单包中的类
 * <p>用于 Janino 编译的 Java 代码执行时的类加载隔离。</p>
 *
 * @author finch
 * @since 2026-06-24
 */
public class SandboxingClassLoader extends ClassLoader {

    private static final Set<String> ALLOWED_PACKAGES = Set.of(
            "java.lang.",
            "java.util.",
            "java.time.",
            "java.math.",
            "java.text.",
            "java.util.stream.",
            "java.util.regex.",
            "java.lang.reflect.Array",
            "org.codehaus.janino."
    );

    public SandboxingClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (isAllowed(name)) {
            return super.loadClass(name);
        }
        throw new SecurityException("沙盒禁止加载类: " + name);
    }

    private boolean isAllowed(String className) {
        // 基本类型和数组始终允许
        if (className.startsWith("[")) return true;
        for (String pkg : ALLOWED_PACKAGES) {
            if (className.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }
}
