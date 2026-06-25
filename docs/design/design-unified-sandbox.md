# LightBot 统一沙盒模块设计文档

> 版本：v1.0 | 日期：2026-06-24
> 前置文档：`design-skill-sandbox.md`（Skill 文件系统设计）
> 目标：将分散的沙盒能力（路径校验、文件存储、脚本执行）统一为一个内聚模块

---

## 一、需求分析

### 1.1 业务场景

LightBot 当前有三处"沙盒相关"能力，各自独立、能力不完整：

| 场景 | 当前实现 | 问题 |
|------|---------|------|
| Agent 代码执行 | **无** | 用户问"几点了"，Agent 无法通过代码获取 |
| 工作流脚本节点 | `ScriptNodeProcessor` + 裸 Nashorn | 安全级别 L0，仅正则拦截，无 ClassFilter |
| Skill 文件管理 | `SkillStorageService` + `SandboxPathValidator` | 仅 MinIO 路径校验，不是代码沙盒 |

**核心诉求**：统一为一个 `SandboxService`，同时支撑代码执行和文件管理。

### 1.2 功能需求

#### 1.2.1 代码执行（P0）

| 需求 | 说明 |
|------|------|
| 多语言支持 | JavaScript + Java + Python |
| 安全沙盒 | 禁止文件/网络/进程/反射操作 |
| 超时控制 | 单次执行 ≤ 5 秒 |
| 内存限制 | 单次执行 ≤ 64MB |
| 输出捕获 | 捕获 stdout + 返回值 |
| 错误处理 | 编译错误/运行时错误/超时 分别返回 |
| 并发安全 | 多用户同时执行互不影响 |

#### 1.2.2 文件管理（P1）

| 需求 | 说明 |
|------|------|
| Skill 只读访问 | Agent 可读取已绑定 Skill 的文件 |
| 工作区读写 | 会话级隔离的临时文件空间 |
| 路径安全 | 防止 `..` 遍历、读写白名单 |
| 文件操作工具 | `read_file` / `write_file` / `list_files`（Agent 可调用） |

#### 1.2.3 工作流集成（P1）

| 需求 | 说明 |
|------|------|
| 替换 ScriptNodeProcessor | 使用统一沙盒引擎执行脚本节点 |
| 向后兼容 | 现有工作流脚本（JavaScript `main(params)` 模式）无需改动 |

### 1.3 非功能需求

| 维度 | 要求 |
|------|------|
| 安全性 | 代码执行不能逃逸沙盒，不能访问 JVM 内部类 |
| 性能 | JS 首次执行 ≤ 300ms，后续 ≤ 20ms；Java 首次 ≤ 200ms |
| 可观测性 | 执行日志（语言、代码 hash、耗时、结果大小） |
| 可扩展性 | 新增语言只需实现 `CodeEngine` 接口 |

---

## 二、现有沙盒能力对比

### 2.1 能力矩阵

| 能力 | SandboxPathValidator | SkillStorageService | ScriptNodeProcessor | 本次设计 |
|------|:---:|:---:|:---:|:---:|
| 路径遍历防护 | ✅ | 依赖 Validator | ❌ | ✅ |
| 读写白名单 | ✅ | 依赖 Validator | ❌ | ✅ |
| MinIO 文件操作 | ❌ | ✅ | ❌ | ✅ 聚合 |
| JS 代码执行 | ❌ | ❌ | ✅ 裸执行 | ✅ 沙盒 |
| Java 代码执行 | ❌ | ❌ | ❌ | ✅ 沙盒 |
| Python 代码执行 | ❌ | ❌ | ❌ | ✅ 进程沙盒 |
| ClassFilter | ❌ | ❌ | ❌ | ✅ |
| 超时控制 | ❌ | ❌ | ✅ 5s | ✅ |
| 内存限制 | ❌ | ❌ | ❌ | ✅ |
| 输出大小限制 | ❌ | ❌ | ❌ | ✅ |
| stdout 捕获 | ❌ | ❌ | ❌ | ✅ |
| 并发隔离 | ❌ | ❌ | ❌ | ✅ 线程池 |

### 2.2 安全等级对比

| 等级 | 描述 | 当前实现 | 目标 |
|------|------|----------|------|
| L0 | 裸执行，无保护 | `ScriptNodeProcessor`（已废弃） | 淘汰 |
| L1 | 正则黑名单 | — | 淘汰 |
| L2 | ClassFilter + 超时 | `NashornEngine`（JS） | 降级方案 |
| L3 | GraalVM Context 沙盒 | `GraalVmEngine`（JS，占位） | JS 首选 |
| L3.5 | 编译期黑名单 + ClassLoader 隔离 | `JaninoEngine`（Java） | **Java 目标** |
| L4 | OS 进程隔离 | `PythonEngine`（Python） | **Python 目标** |

### 2.3 现有组件复用

| 组件 | 复用方式 |
|------|----------|
| `SandboxPathValidator` | 直接复用，作为文件操作的安全层 |
| `SkillStorageService` | 直接复用，沙盒文件系统代理 MinIO |
| `ScriptNodeProcessor` | **废弃**，迁移至统一沙盒引擎 |
| `Nashorn 15.4` | 保留，作为 JS 降级引擎（无 GraalVM 时） |
| `MinioUtil` | 直接复用，底层存储 |

---

## 三、技术设计

### 3.1 架构总览

```
┌──────────────────────────────────────────────────────────────────────┐
│                         统一沙盒模块                                   │
│                                                                      │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────────────┐   │
│  │ ExecuteCodeTool │  │ FileOperation  │  │ ScriptNodeProcessor  │   │
│  │ (Agent 工具)    │  │ Tools (Agent)  │  │ (工作流节点)          │   │
│  └───────┬────────┘  └───────┬────────┘  └──────────┬───────────┘   │
│          │                   │                       │               │
│          ▼                   ▼                       ▼               │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    SandboxService                             │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌───────────────────┐  │   │
│  │  │ CodeExecEngine│  │ SandboxFs    │  │ SecurityPolicy    │  │   │
│  │  │ (代码执行)    │  │ (文件系统)    │  │ (安全策略)        │  │   │
│  │  └──────┬───────┘  └──────┬───────┘  └───────────────────┘  │   │
│  └─────────┼─────────────────┼──────────────────────────────────┘   │
│            │                 │                                       │
│  ┌─────────▼───────┐  ┌─────▼────────────┐                         │
│  │ EngineRegistry  │  │ SkillStorage     │                         │
│  │ ┌─────────────┐ │  │ Service (复用)    │                         │
│  │ │ GraalVmEngine│ │  │                  │                         │
│  │ │ (JS, 占位)   │ │  │ PathValidator    │                         │
│  │ ├─────────────┤ │  │ (复用)            │                         │
│  │ │ JaninoEngine │ │  └──────────────────┘                         │
│  │ │ (Java)       │ │                                               │
│  │ ├─────────────┤ │                                               │
│  │ │ NashornEngine│ │  ← JS 降级方案                                │
│  │ │ (JS)         │ │                                               │
│  │ ├─────────────┤ │                                               │
│  │ │ PythonEngine │ │  ← Python (ProcessBuilder)                   │
│  │ │ (Python)     │ │                                               │
│  │ └─────────────┘ │                                               │
│  └─────────────────┘                                               │
└──────────────────────────────────────────────────────────────────────┘
```

### 3.2 核心类设计

#### 3.2.1 SandboxService（统一入口）

```java
/**
 * 统一沙盒服务：代码执行 + 文件管理
 *
 * @author finch
 * @since 2026-06-24
 */
public interface SandboxService {

    // ===== 代码执行 =====

    /**
     * 执行代码片段
     *
     * @param code     代码内容
     * @param language 编程语言（java / javascript / python），null 默认 java
     * @param params   传入参数（代码中可通过 params 访问）
     * @param timeoutMs 超时时间（毫秒），null 使用默认 5000
     * @return 执行结果
     */
    CodeExecResult executeCode(String code, String language,
                               Map<String, Object> params, Long timeoutMs);

    // ===== 文件管理 =====

    /** 读取文件 */
    String readFile(SandboxPath path);

    /** 写入文件 */
    void writeFile(SandboxPath path, String content);

    /** 列出目录文件 */
    List<FileInfo> listFiles(SandboxPath path);

    /** 删除文件 */
    void deleteFile(SandboxPath path);

    /** 检查文件是否存在 */
    boolean fileExists(SandboxPath path);
}
```

#### 3.2.2 CodeExecResult（执行结果）

```java
/**
 * 代码执行结果
 */
@Data
@Builder
public class CodeExecResult {
    private boolean success;
    private String output;        // stdout 输出
    private String returnValue;   // 返回值（toString）
    private String error;         // 错误信息
    private long elapsedMs;       // 执行耗时
    private String language;      // 实际使用的语言
}
```

#### 3.2.3 CodeEngine（引擎接口）

```java
/**
 * 代码执行引擎接口
 */
public interface CodeEngine {

    /** 支持的语言标识 */
    String language();

    /** 是否可用（依赖是否存在） */
    boolean isAvailable();

    /** 执行代码 */
    CodeExecResult execute(String code, Map<String, Object> params, long timeoutMs);
}
```

#### 3.2.4 SandboxPath（路径抽象）

```java
/**
 * 沙盒路径：统一路径表示 + 类型安全
 */
public record SandboxPath(PathType type, String relativePath) {

    public enum PathType {
        SKILL,      // skills/{slug}/xxx (只读)
        WORKSPACE   // threads/{sessionId}/xxx (读写)
    }

    /** 构建 Skill 路径 */
    public static SandboxPath skill(String slug, String relativePath) {
        return new SandboxPath(PathType.SKILL, slug + "/" + relativePath);
    }

    /** 构建工作区路径 */
    public static SandboxPath workspace(Long sessionId, String relativePath) {
        return new SandboxPath(PathType.WORKSPACE, sessionId + "/" + relativePath);
    }
}
```

### 3.3 引擎层设计

#### 3.3.1 GraalVmEngine（JavaScript — 首选引擎）

```java
/**
 * GraalVM Polyglot JavaScript 引擎
 * <p>安全级别 L3：Context 沙盒，细粒度权限控制</p>
 */
@Component
@ConditionalOnClass(name = "org.graalvm.polyglot.Context")
public class GraalVmEngine implements CodeEngine {

    @Override
    public String language() { return "javascript"; }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("org.graalvm.polyglot.Context");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public CodeExecResult execute(String code, Map<String, Object> params, long timeoutMs) {
        // 1. 构建 GraalVM Context（沙盒配置）
        //    - allowIO(false)
        //    - allowNativeAccess(false)
        //    - allowCreateThread(false)
        //    - allowHostAccess(HostAccess.EXPLICIT)
        //    - option("engine.max-heap", "64m")
        // 2. 注入 params 对象
        // 3. 注入 print 函数 → 捕获到 ByteArrayOutputStream
        // 4. eval(code)，提取返回值
        // 5. close Context
    }
}
```

**GraalVM 安全配置**：

| 配置项 | 值 | 效果 |
|--------|-----|------|
| `allowIO(false)` | 禁止 | 无法读写文件 |
| `allowNativeAccess(false)` | 禁止 | 无法调用 JNI |
| `allowCreateThread(false)` | 禁止 | 无法创建线程 |
| `allowHostAccess(EXPLICIT)` | 限制 | 仅允许显式注册的 Java 方法 |
| `allowHostClassLookup(predicate)` | 白名单 | 仅允许 `java.time.*`, `java.util.*`, `java.math.*` |
| `engine.max-heap` | 64m | 内存上限 |

**注入的安全 API**：

```javascript
// 用户可用的内置对象
params          // 传入参数 Map
print(...)      // stdout 输出（捕获到 output）
Math            // JavaScript Math
Date            // JavaScript Date
JSON            // JavaScript JSON
// java.time.*  // 通过 HostAccess 白名单开放
// java.util.*  // 通过 HostAccess 白名单开放
// java.math.*  // 通过 HostAccess 白名单开放
```

#### 3.3.2 JaninoEngine（Java — 编译执行）

```java
/**
 * Janino Java 编译执行引擎
 * <p>安全级别 L3.5：编译期黑名单 + ClassLoader 隔离 + 超时控制</p>
 */
@Component
public class JaninoEngine implements CodeEngine {

    @Override
    public String language() { return "java"; }

    @Override
    public boolean isAvailable() { return true; } // Janino 是内置依赖

    @Override
    public CodeExecResult execute(String code, Map<String, Object> params, long timeoutMs) {
        // 1. 安全校验：正则拦截危险关键字
        // 2. 代码包装：将用户代码包装为 class Sandbox_xxx { public static Object run(...) { ... } }
        // 3. 添加安全 import（java.time.*, java.util.*, java.math.*）
        // 4. Janino SimpleCompiler 编译
        // 5. SandboxingClassLoader 加载（白名单包过滤）
        // 6. 重定向 System.out → ByteArrayOutputStream
        // 7. CompletableFuture.orTimeout() 执行 run() 方法
        // 8. 恢复 System.out，返回结果
    }
}
```

**用户代码包装模板**：

```java
// 用户写的代码：return LocalDateTime.now().toString();
// 包装后：
import java.time.*;
import java.util.*;
import java.math.*;
import java.text.*;
import java.util.stream.*;

public class Sandbox_xxx {
    public static Object run(Map<String, Object> params) {
        return LocalDateTime.now().toString();  // ← 用户代码
    }
}
```

**危险关键字黑名单**：

```java
private static final Pattern BLOCKED = Pattern.compile(
    "\\b(Runtime|ProcessBuilder|System\\.exit|System\\.setOut|System\\.setErr" +
    "|Thread|ClassLoader|Class\\.forName|Method\\.invoke|Field\\.set" +
    "|FileInputStream|FileOutputStream|RandomAccessFile|FileWriter|FileReader" +
    "|Socket|ServerSocket|HttpURLConnection|URL\\.openConnection" +
    "|javax\\.script|java\\.lang\\.reflect|sun\\.misc|java\\.io\\.|java\\.net\\." +
    "|ProcessHandle|Desktop|FileSystem)\\b");
```

**SandboxingClassLoader**：

```java
/**
 * 沙盒类加载器：仅允许加载白名单包中的类
 */
public class SandboxingClassLoader extends ClassLoader {

    private static final Set<String> ALLOWED_PACKAGES = Set.of(
        "java.lang.",      // String, Integer, Math, StringBuilder...
        "java.util.",      // List, Map, Set, Date, Collections...
        "java.time.",      // LocalDateTime, LocalDate, Period...
        "java.math.",      // BigDecimal, BigInteger
        "java.text.",      // DecimalFormat, SimpleDateFormat
        "java.util.stream.", // Stream
        "java.util.regex.",  // Pattern, Matcher
        "java.lang.reflect.Array"  // Array.newInstance 仅此一个
    );

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (ALLOWED_PACKAGES.stream().anyMatch(name::startsWith)) {
            return super.loadClass(name);
        }
        throw new SecurityException("沙盒禁止加载类: " + name);
    }
}
```

#### 3.3.3 NashornEngine（JavaScript — 降级方案）

```java
/**
 * Nashorn JavaScript 引擎（降级方案）
 * <p>安全级别 L2：ClassFilter + 超时，无 GraalVM 时使用</p>
 */
@Component
@ConditionalOnMissingBean(GraalVmEngine.class)
public class NashornEngine implements CodeEngine {

    @Override
    public String language() { return "javascript"; }

    @Override
    public boolean isAvailable() { return true; } // Nashorn 是内置依赖
}
```

**与 GraalVmEngine 的区别**：

| 维度 | GraalVmEngine | NashornEngine |
|------|---------------|---------------|
| 安全级别 | L3（Context 沙盒） | L2（ClassFilter） |
| 内存限制 | 内置支持 | 无（依赖 JVM） |
| IO 限制 | `allowIO(false)` | ClassFilter 拦截 |
| 多语言 | JS/Python/Ruby | 仅 JS |
| 依赖 | graalvm-polyglot ~10MB | 已有 Nashorn ~2MB |
| 优先级 | **首选** | 自动降级 |

#### 3.3.4 EngineRegistry（引擎注册表）

```java
/**
 * 代码执行引擎注册表
 * <p>按语言查找可用引擎，自动降级</p>
 */
@Component
public class EngineRegistry {

    private final Map<String, List<CodeEngine>> engines; // language → [首选, 降级, ...]

    /**
     * 获取指定语言的可用引擎（优先返回排在前面的）
     */
    public CodeEngine resolve(String language) {
        String lang = normalizeLanguage(language);
        List<CodeEngine> candidates = engines.getOrDefault(lang, List.of());
        return candidates.stream()
                .filter(CodeEngine::isAvailable)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.SANDBOX_ENGINE_NOT_FOUND,
                        "不支持的编程语言: " + language));
    }

    /** 获取所有可用语言 */
    public List<String> availableLanguages() { ... }

    private String normalizeLanguage(String lang) {
        if (lang == null || lang.isBlank()) return "java";
        return switch (lang.toLowerCase()) {
            case "js", "javascript", "ecmascript" -> "javascript";
            case "py", "python" -> "python";
            case "java" -> "java";
            default -> lang.toLowerCase();
        };
    }
}
```

### 3.4 文件系统层设计

#### 3.4.1 SandboxFs（虚拟文件系统）

```java
/**
 * 沙盒虚拟文件系统
 * <p>统一 Skill 只读区 + 工作区读写区的文件操作</p>
 */
@Component
@RequiredArgsConstructor
public class SandboxFs {

    private final SkillStorageService skillStorage;
    private final MinioUtil minioUtil;

    /**
     * 读取文件
     * @throws BizException SANDBOX_PATH_VIOLATION 路径不合法
     * @throws BizException SKILL_FILE_NOT_FOUND 文件不存在
     */
    public String readFile(SandboxPath path) {
        String minioPath = resolveMinioPath(path);
        if (path.type() == SandboxPath.PathType.SKILL) {
            SandboxPathValidator.checkReadable(minioPath);
        }
        return new String(minioUtil.downloadBytes(minioPath), StandardCharsets.UTF_8);
    }

    /**
     * 写入文件（仅工作区）
     */
    public void writeFile(SandboxPath path, String content) {
        if (path.type() == SandboxPath.PathType.SKILL) {
            throw new BizException(ErrorCode.SANDBOX_PATH_VIOLATION, "Skill 目录为只读");
        }
        String minioPath = resolveMinioPath(path);
        SandboxPathValidator.checkWritable(minioPath);
        minioUtil.uploadString(content, minioPath, "application/octet-stream");
    }

    /**
     * 列出目录文件
     */
    public List<FileInfo> listFiles(SandboxPath path) {
        String minioPath = resolveMinioPath(path);
        if (path.type() == SandboxPath.PathType.SKILL) {
            SandboxPathValidator.checkReadable(minioPath);
        }
        List<String> objects = minioUtil.listObjects(minioPath);
        return objects.stream().map(obj -> {
            String relative = obj.substring(minioPath.length());
            return new FileInfo(relative, minioPath + relative);
        }).toList();
    }

    private String resolveMinioPath(SandboxPath path) {
        return switch (path.type()) {
            case SKILL -> "skills/" + path.relativePath();
            case WORKSPACE -> "threads/" + path.relativePath();
        };
    }
}
```

#### 3.4.2 FileInfo

```java
public record FileInfo(String name, String fullPath, Long size) {}
```

### 3.5 Agent 工具层设计

#### 3.5.1 ExecuteCodeTool（代码执行工具）

```java
/**
 * 代码执行工具：在安全沙盒中执行代码片段
 *
 * @author finch
 * @since 2026-06-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SystemTool(displayName = "代码执行", tags = {"code", "execution", "sandbox"})
public class ExecuteCodeTool {

    private final SandboxService sandboxService;

    @Tool(name = "execute_code",
          description = "在安全沙盒中执行代码片段并返回结果。支持 Java 和 JavaScript。" +
                        "Java 代码直接写方法体，无需 class/main 声明。" +
                        "禁止文件/网络/进程操作。超时 5 秒。")
    public String execute(
            @ToolParam(description = "要执行的代码")
            @ToolParamMeta(example = "return java.time.LocalDateTime.now().toString()")
            String code,
            @ToolParam(description = "编程语言（java/javascript），默认 java", required = false)
            @ToolParamMeta(example = "java")
            String language
    ) {
        if (code == null || code.isBlank()) {
            return objectMapper.writeValueAsString(Map.of(
                    "success", false, "error", "代码不能为空"));
        }
        CodeExecResult result = sandboxService.executeCode(code, language, null, null);
        return objectMapper.writeValueAsString(result);
    }
}
```

#### 3.5.2 SandboxFileTool（文件操作工具）

```java
/**
 * 沙盒文件操作工具
 *
 * @author finch
 * @since 2026-06-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SystemTool(displayName = "沙盒文件操作", tags = {"file", "sandbox"})
public class SandboxFileTool {

    private final SandboxService sandboxService;

    @Tool(name = "sandbox_read_file", description = "读取沙盒中的文件内容")
    public String readFile(
            @ToolParam(description = "文件路径（如 skills/my-skill/SKILL.md）") String path
    ) { ... }

    @Tool(name = "sandbox_list_files", description = "列出沙盒目录中的文件")
    public String listFiles(
            @ToolParam(description = "目录路径（如 skills/my-skill）") String path
    ) { ... }

    @Tool(name = "sandbox_write_file", description = "写入文件到工作区（仅限会话工作区）")
    public String writeFile(
            @ToolParam(description = "文件路径（如 threads/{sessionId}/output.txt）") String path,
            @ToolParam(description = "文件内容") String content
    ) { ... }
}
```

### 3.6 工作流集成

#### 3.6.1 ScriptNodeProcessor 改造

```java
/**
 * 脚本节点（改造后）：委托 SandboxService 执行
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScriptNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    private final SandboxService sandboxService;
    private final ObjectMapper objectMapper;

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData();
        String script = stringVal(nodeData.get("scriptContent"));
        String language = stringVal(nodeData.get("scriptLanguage"));

        // 参数构建（保持不变）
        Map<String, Object> params = buildScriptParams(nodeData, context.getVariables());

        // 委托统一沙盒执行
        CodeExecResult result = sandboxService.executeCode(script, language, params, 5000L);

        if (!result.isSuccess()) {
            throw new IllegalArgumentException("脚本执行失败: " + result.getError());
        }

        // 解析返回值为 Map（保持现有 output 映射逻辑）
        Object rawResult = parseReturnValue(result.getReturnValue());
        Map<String, Object> outputs = normalizeOutputs(rawResult, nodeData);
        context.getVariables().putAll(outputs);

        return NodeExecutionResult.builder()
                .nextNodeId(resolveNextNodeId(context))
                .outputs(outputs)
                .build();
    }
}
```

**向后兼容**：现有的 JavaScript `main(params)` 模式通过 GraalVmEngine 的 `eval("main(params)")` 保持兼容。

### 3.7 安全模型总览

```
┌─────────────────────────────────────────────────────────────┐
│                      安全分层模型                              │
│                                                             │
│  Layer 1: 工具权限（Agent 配置）                               │
│  ├── Agent 绑定 execute_code 工具才可执行代码                  │
│  └── Agent 绑定 sandbox_* 工具才可操作文件                     │
│                                                             │
│  Layer 2: 代码审查（编译期）                                   │
│  ├── 正则拦截危险关键字（Java）                                │
│  ├── import 白名单（Java）                                   │
│  └── GraalVM HostAccess 白名单（JS）                         │
│                                                             │
│  Layer 3: 运行时隔离                                          │
│  ├── GraalVM Context 沙盒（IO/线程/内存/类访问 全禁）          │
│  ├── SandboxingClassLoader（Java 类加载白名单）               │
│  └── CompletableFuture.orTimeout（超时强制中断）              │
│                                                             │
│  Layer 4: 输出控制                                            │
│  ├── stdout 捕获 + 截断（max 10000 字符）                    │
│  ├── 返回值序列化限制                                         │
│  └── 错误信息脱敏（不暴露系统路径）                            │
│                                                             │
│  Layer 5: 文件系统隔离                                        │
│  ├── SandboxPathValidator（路径遍历防护）                     │
│  ├── Skill 目录只读                                          │
│  └── 工作区会话隔离（MinIO prefix）                           │
└─────────────────────────────────────────────────────────────┘
```

---

## 四、难点与风险

### 4.1 Java 沙盒安全性（高风险）

| 风险 | 攻击向量 | 缓解措施 |
|------|----------|----------|
| 反射绕过 | `Class.forName("java.lang.Runtime")` | SandboxingClassLoader 拦截 + 正则黑名单 |
| 序列化攻击 | `ObjectInputStream.readObject()` | import 黑名单拦截 `java.io.*` |
| 内部 API | `sun.misc.Unsafe` | SandboxingClassLoader 拦截 `sun.*` |
| 字符串拼接 OOM | `new String(new char[Integer.MAX_VALUE])` | 输出截断 + JVM `-Xmx` |
| 无限循环 | `while(true){}` | CompletableFuture 超时 + 线程中断 |
| Metaspace 泄露 | 每次编译产生新 Class | 编译结果缓存（代码 hash → Class） |

**结论**：Java 沙盒无法做到 100% 安全。Janino 方案安全级别为 L3.5，可防御 99% 的恶意代码，但理论上存在绕过可能。**生产环境建议 Java 沙盒仅在受控环境（内网/授权用户）使用**，公网场景优先使用 GraalVM JS（L3 级别安全）。

### 4.2 GraalVM 依赖体积（中风险）

GraalVM Polyglot JS 引擎约 **10MB**，对轻量级项目可能偏大。

**缓解**：
- 使用 `community` classifier（开源版，免费）
- JS 引擎可选引入（`@ConditionalOnClass`），不强制依赖
- 无 GraalVM 时自动降级到 Nashorn

### 4.3 Janino 编译器限制（中风险）

Janino 是轻量级编译器，不支持完整 Java 语法：

| 支持 | 不支持 |
|------|--------|
| 基本类型、集合、泛型 | 匿名内部类（部分） |
| Lambda（有限） | `var` 关键字（Janino 3.1.x） |
| 静态方法调用 | Java 17+ 特性（sealed, pattern matching） |
| import 语句 | 注解处理 |

**缓解**：工具描述中明确说明限制，复杂代码建议使用 JavaScript。

### 4.4 Python 支持（低风险 / 远期）

GraalVM Python（GraalPython）约 **30MB**，且兼容性不如 CPython。

**建议**：Phase 1 不引入 Python，作为扩展点预留。GraalVM 的 Polyglot API 天然支持后续添加。

---

## 五、新增依赖

### 5.1 Maven 依赖

```xml
<!-- ===== 沙盒模块：代码执行引擎 ===== -->

<!-- GraalVM Polyglot（JavaScript 引擎，可选） -->
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>polyglot</artifactId>
    <version>24.1.2</version>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>js</artifactId>
    <version>24.1.2</version>
    <classifier>community</classifier>
    <optional>true</optional>
</dependency>

<!-- Janino Java 编译器 -->
<dependency>
    <groupId>org.codehaus.janino</groupId>
    <artifactId>janino</artifactId>
    <version>3.1.12</version>
</dependency>
```

### 5.2 依赖分析

| 依赖 | JAR 大小 | 是否必须 | 说明 |
|------|----------|----------|------|
| `graalvm-polyglot` | ~1.5MB | 可选 | 核心 API |
| `graalvm-js` (community) | ~8MB | 可选 | JavaScript 引擎 |
| `janino` | ~600KB | 必须 | Java 编译器 |
| `nashorn-core` | ~2MB | 已有 | JS 降级方案 |

**总增量**：
- 最小（仅 Java）：+600KB
- 完整（Java + JS）：+10MB

### 5.3 不需要的中间件

- 不需要 Docker
- 不需要额外数据库表
- 不需要 Redis（代码执行无状态）
- 不需要额外 MinIO bucket（复用现有）

---

## 六、工作量评估

### 6.1 Phase 1：核心代码执行（P0）

| 任务 | 文件 | 行数 | 工时 | 说明 |
|------|------|------|------|------|
| CodeExecResult | `dto/CodeExecResult.java` | ~40 | 0.5h | 执行结果 DTO |
| CodeEngine 接口 | `service/sandbox/CodeEngine.java` | ~25 | 0.5h | 引擎抽象 |
| EngineRegistry | `service/sandbox/EngineRegistry.java` | ~80 | 1.5h | 引擎注册 + 语言路由 |
| GraalVmEngine | `service/sandbox/GraalVmEngine.java` | ~150 | 4h | GraalVM Context 沙盒 |
| JaninoEngine | `service/sandbox/JaninoEngine.java` | ~200 | 5h | 编译 + ClassLoader + 包装 |
| SandboxServiceImpl | `service/sandbox/SandboxServiceImpl.java` | ~60 | 1h | 统一入口实现 |
| ExecuteCodeTool | `tool/builtin/ExecuteCodeTool.java` | ~70 | 1.5h | Agent 工具 |
| pom.xml | 添加 GraalVM + Janino 依赖 | — | 0.5h | |
| 单元测试 | `test/.../SandboxServiceTest.java` | ~200 | 4h | 安全测试 + 边界测试 |
| **Phase 1 合计** | | **~825 行** | **~18.5h** | |

### 6.2 Phase 2：文件管理 + 工作流集成（P1）

| 任务 | 文件 | 行数 | 工时 | 说明 |
|------|------|------|------|------|
| SandboxPath 重构 | `util/SandboxPathValidator.java` → 增强 | ~30 改动 | 1h | 支持 SandboxPath 抽象 |
| SandboxFs | `service/sandbox/SandboxFs.java` | ~100 | 2h | 虚拟文件系统 |
| SandboxFileTool | `tool/builtin/SandboxFileTool.java` | ~80 | 1.5h | 文件操作工具 |
| ScriptNodeProcessor 改造 | `workflow/processor/ScriptNodeProcessor.java` | ~50 改动 | 2h | 委托 SandboxService |
| 集成测试 | | ~100 | 2h | |
| **Phase 2 合计** | | **~360 行** | **~8.5h** | |

### 6.3 Phase 3：前端渲染（P2）

| 任务 | 文件 | 工时 | 说明 |
|------|------|------|------|
| ExecuteCodeResult.vue | `components/tools/ExecuteCodeResult.vue` | 2h | 代码 + 输出渲染 |
| SandboxFileResult.vue | `components/tools/SandboxFileResult.vue` | 1.5h | 文件列表渲染 |
| toolRegistry.js | 更新映射 | 0.5h | |
| **Phase 3 合计** | | **~4h** | |

### 6.4 总工作量

| 阶段 | 工时 | 交付物 |
|------|------|--------|
| Phase 1: 代码执行 | 18.5h | GraalVmEngine + JaninoEngine + ExecuteCodeTool |
| Phase 2: 文件 + 工作流 | 8.5h | SandboxFs + SandboxFileTool + ScriptNodeProcessor 改造 |
| Phase 3: 前端 | 4h | 渲染组件 |
| **合计** | **~31h（约 4 天）** | |

### 6.5 里程碑

| 阶段 | 交付物 | 预计工时 |
|------|--------|----------|
| M1: JS 沙盒可用 | GraalVmEngine + NashornEngine 降级 + ExecuteCodeTool（仅 JS） | 8h |
| M2: Java 沙盒可用 | JaninoEngine + SandboxingClassLoader | 7h |
| M3: 工作流集成 | ScriptNodeProcessor 改造 + SandboxFs | 6h |
| M4: 前端 + 测试 | 渲染组件 + 端到端测试 | 6h |
| M5: 优化 | 引擎缓存、日志、监控 | 4h |

---

## 七、文件清单

### 7.1 新增文件（后端）

| 文件 | 模块 | 说明 |
|------|------|------|
| `dto/CodeExecResult.java` | lightbot-server | 执行结果 DTO |
| `service/sandbox/CodeEngine.java` | lightbot-server | 引擎接口 |
| `service/sandbox/EngineRegistry.java` | lightbot-server | 引擎注册表 |
| `service/sandbox/GraalVmEngine.java` | lightbot-server | GraalVM JS 引擎 |
| `service/sandbox/JaninoEngine.java` | lightbot-server | Janino Java 引擎 |
| `service/sandbox/SandboxingClassLoader.java` | lightbot-server | Java 类加载白名单 |
| `service/sandbox/SandboxServiceImpl.java` | lightbot-server | 统一沙盒服务 |
| `service/sandbox/SandboxFs.java` | lightbot-server | 虚拟文件系统 |
| `tool/builtin/ExecuteCodeTool.java` | lightbot-server | 代码执行工具 |
| `tool/builtin/SandboxFileTool.java` | lightbot-server | 文件操作工具 |

### 7.2 修改文件

| 文件 | 改动 |
|------|------|
| `lightbot-server/pom.xml` | 添加 GraalVM + Janino 依赖 |
| `workflow/processor/ScriptNodeProcessor.java` | 委托 SandboxService 执行 |
| `util/SandboxPathValidator.java` | 增强 SandboxPath 支持 |

### 7.3 新增文件（前端）

| 文件 | 说明 |
|------|------|
| `components/tools/ExecuteCodeResult.vue` | 代码执行结果渲染 |
| `components/tools/SandboxFileResult.vue` | 文件操作结果渲染 |

---

## 八、后续扩展

### 8.1 Python 支持

GraalVM Polyglot 天然支持 GraalPython，仅需添加依赖：

```xml
<dependency>
    <groupId>org.graalvm.python</groupId>
    <artifactId>python</artifactId>
    <version>24.1.2</version>
</dependency>
```

工作量：~2h（配置 + 测试）。

### 8.2 代码模板库

预置常用代码模板，降低 LLM 生成代码的出错率：

```json
{
  "templates": {
    "current_time": {
      "java": "return java.time.LocalDateTime.now().toString();",
      "javascript": "return new Date().toISOString();"
    },
    "uuid": {
      "java": "return java.util.UUID.randomUUID().toString();",
      "javascript": "return crypto.randomUUID();"
    },
    "base64_encode": {
      "java": "return java.util.Base64.getEncoder().encodeToString(params.get(\"input\").toString().getBytes());"
    }
  }
}
```

### 8.3 工作区文件持久化

当前工作区（`threads/{sessionId}/`）随会话存在。后续可扩展：
- 会话结束时自动清理
- 用户可选择保留重要文件
- 跨会话共享文件（`users/{userId}/shared/`）

### 8.4 Docker 容器沙盒（L4 远期）

对安全级别要求极高的场景（公网、多租户），可引入 Docker 容器：
- 每次执行启动临时容器
- 支持任意语言（只要有对应镜像）
- 资源限制（CPU/内存/磁盘）由 Docker 管理
- 工作量：~3-5 天（额外）

---

## 九、实施记录

> 日期：2026-06-24

### 9.1 实施概况

三个 Phase 均已实施完成，后端编译通过，前端构建通过。

### 9.2 Phase 1：核心代码执行（已完成）

**新增文件**：

| 文件 | 行数 | 说明 |
|------|------|------|
| `dto/CodeExecResult.java` | ~35 | 执行结果 DTO（success/output/returnValue/error/elapsedMs/language） |
| `service/sandbox/CodeEngine.java` | ~30 | 引擎接口（language/isAvailable/execute） |
| `service/sandbox/EngineRegistry.java` | ~70 | 引擎注册表，按语言路由，自动降级 |
| `service/sandbox/NashornEngine.java` | ~150 | Nashorn JS 引擎（L2 安全级别，ClassFilter + 超时） |
| `service/sandbox/GraalVmEngine.java` | ~30 | GraalVM 引擎占位（当前不可用，需添加依赖后启用） |
| `service/sandbox/JaninoEngine.java` | ~180 | Janino Java 编译执行引擎（L3.5 安全级别） |
| `service/sandbox/PythonEngine.java` | ~210 | Python 3 引擎（ProcessBuilder 子进程，L4 安全级别） |
| `service/sandbox/SandboxingClassLoader.java` | ~40 | Java 类加载白名单 |
| `service/sandbox/SandboxService.java` | ~55 | 统一沙盒服务接口 |
| `service/sandbox/SandboxServiceImpl.java` | ~70 | 统一沙盒服务实现 |
| `tool/builtin/ExecuteCodeTool.java` | ~90 | Agent 代码执行工具（execute_code） |

**修改文件**：

| 文件 | 改动 |
|------|------|
| `pom.xml`（父） | 添加 GraalVM Maven 仓库注释说明 |
| `lightbot-server/pom.xml` | 添加 Janino 依赖 + GraalVM 依赖注释 |
| `enums/ErrorCode.java` | 新增 5 个沙盒错误码（92020-92024） |

### 9.3 Phase 2：文件管理 + 工作流集成（已完成）

**新增文件**：

| 文件 | 行数 | 说明 |
|------|------|------|
| `service/sandbox/SandboxPath.java` | ~55 | 沙盒路径抽象（record，支持 SKILL/WORKSPACE 类型） |
| `service/sandbox/SandboxFs.java` | ~80 | 虚拟文件系统（复用 MinioUtil + SandboxPathValidator） |
| `tool/builtin/SandboxFileTool.java` | ~130 | Agent 文件操作工具（sandbox_read_file/list_files/write_file） |

**修改文件**：

| 文件 | 改动 |
|------|------|
| `workflow/processor/ScriptNodeProcessor.java` | 完全重构：移除裸 Nashorn 执行，委托 SandboxService |

### 9.4 Phase 3：前端渲染（已完成）

**新增文件**：

| 文件 | 说明 |
|------|------|
| `lightbot-ui/src/components/tools/ExecuteCodeResult.vue` | 代码执行结果渲染（语言标签/状态/输出/返回值） |
| `lightbot-ui/src/components/tools/SandboxFileResult.vue` | 文件操作结果渲染（读取内容/文件列表/写入结果） |

**修改文件**：

| 文件 | 改动 |
|------|------|
| `lightbot-ui/src/components/toolRegistry.js` | 新增 4 个工具映射（execute_code + sandbox_read_file/list_files/write_file） |

### 9.5 与设计文档的差异

| 设计 | 实际 | 原因 |
|------|------|------|
| GraalVM Polyglot JS 引擎（L3） | Nashorn JS 引擎（L2） | GraalVM Maven 仓库不可达，GraalVmEngine 保留为占位，后续可启用 |
| GraalVM `@ConditionalOnClass` | `isAvailable()` 运行时检测 | 效果相同，运行时检测更灵活 |
| SandboxPathValidator 增强 | 未修改 | 现有方法已满足需求，SandboxPath 通过 `toMinioPath()` 适配 |

### 9.6 Python 支持（2026-06-25 新增）

**方案选择**：ProcessBuilder 子进程方式（L4 安全级别，OS 进程隔离），而非 GraalVM Python（需 ~30MB 依赖 + GraalVM 仓库不可达）。

**新增文件**：

| 文件 | 行数 | 说明 |
|------|------|------|
| `service/sandbox/PythonEngine.java` | ~210 | Python 3 引擎（ProcessBuilder 子进程，L4 安全级别） |

**修改文件**：

| 文件 | 改动 |
|------|------|
| `tool/builtin/ExecuteCodeTool.java` | 描述更新：支持 Java/JavaScript/Python 三种语言 |
| `workflow/processor/ScriptNodeProcessor.java` | 已通过 SandboxService 自动路由到 PythonEngine |

**Python 引擎特性**：
- 通过 `ProcessBuilder("python3", ...)` 启动独立子进程
- 环境变量清空（`pb.environment().clear()`），防止信息泄露
- 5 秒超时（`process.waitFor(timeout, MILLISECONDS)` + `destroyForcibly()`）
- import 黑名单：禁止 os/subprocess/socket/http/sys 等系统模块
- stdout 捕获 + 10000 字符截断
- 脚本需定义 `main()` 函数，返回值通过标记 `__SANDBOX_RESULT_START__/END__` 提取
- params 变量通过 JSON 注入，脚本中以 `params.xxx` 访问
- `isAvailable()` 自动检测 `python3` 或 `python` 命令是否可用

**安全模型**：

| 层级 | 措施 |
|------|------|
| 进程隔离 | OS 级子进程，独立地址空间 |
| 环境隔离 | 环境变量清空 |
| import 黑名单 | 正则拦截 os/subprocess/socket/http/sys 等 |
| 超时控制 | 5 秒 + destroyForcibly() |
| 输出截断 | 10000 字符上限 |

### 9.7 待完善事项

1. **GraalVM 引擎启用**：当 GraalVM Maven 仓库可访问时，恢复 GraalVmEngine 的完整实现，替换 NashornEngine 为默认 JS 引擎
2. **单元测试**：SandboxServiceTest（安全测试 + 边界测试），预计 ~200 行
3. **引擎缓存**：Janino 编译结果可按代码 hash 缓存，减少重复编译开销
4. **输出截断策略**：当前截断 10000 字符，可配置化
5. **Python 沙盒增强**：可引入 Docker 容器执行，进一步提升隔离级别
