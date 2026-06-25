# Agent 代码执行工具 — 需求与技术设计文档

> 版本：v1.0 | 日期：2026-06-24

---

## 1. 需求背景

### 1.1 业务场景

当前 LightBot Agent 只能通过预定义工具（搜索、知识库、SQL、计算器等）与外部交互，无法执行任意代码。以下场景无法覆盖：

| 场景 | 示例 |
|------|------|
| 获取实时信息 | "现在几点了？" → `LocalDateTime.now()` |
| 数学计算（复杂） | "算一下 2 的 100 次方" → `BigDecimal` 运算 |
| 数据处理 | "帮我把这组数据排序去重" → 代码处理 |
| 格式转换 | "把这段 JSON 转成 CSV" → 代码转换 |
| 日期计算 | "距离 2027 年春节还有多少天？" → 日期运算 |
| 自定义逻辑 | "帮我生成一个随机密码" → `SecureRandom` |

### 1.2 核心需求

- Agent 可调用 `execute_code` 工具执行代码片段
- 支持 **JavaScript**（已有 Nashorn 依赖）+ **Java**（JVM 内原生）
- 代码在安全沙盒中运行，禁止访问系统资源
- 执行结果以 JSON 格式返回（stdout、返回值、错误信息）

### 1.3 非功能需求

| 维度 | 要求 |
|------|------|
| 安全性 | 禁止文件/网络/进程操作，禁止反射绕过 |
| 性能 | 单次执行 ≤ 5 秒超时，内存 ≤ 64MB |
| 可观测性 | 记录执行日志（代码、耗时、结果） |
| 隔离性 | 多用户并发执行互不影响 |

---

## 2. 现状分析

### 2.1 已有能力

| 组件 | 状态 | 说明 |
|------|------|------|
| Nashorn 15.4 | 已引入 | `lightbot-server/pom.xml`，仅 `ScriptNodeProcessor` 使用 |
| SandboxPathValidator | 已实现 | 路径安全校验，与代码执行无关 |
| ScriptNodeProcessor | 已实现 | 工作流节点，非 Agent 工具，安全级别 L0（仅正则拦截） |
| design-skill-sandbox.md | 设计文档 | 提出了 ScriptSandbox 方案（未实现） |

### 2.2 设计文档中的未实现方案

`docs/design/design-skill-sandbox.md` Section 10 描述了 "Level 3.5 Java 原生脚本沙盒"，核心组件：

- `ScriptSandbox.java` — GraalVM Context 沙盒
- `SandboxClassFilter.java` — 类白名单过滤
- `SandboxWatchdog.java` — 超时看门狗
- `SandboxResult.java` — 执行结果封装
- `SafeJsonApi.java` / `SafeHttpApi.java` — 受限 API
- `ExecuteSkillScriptTool.java` — Agent 工具入口

**该方案未实现，可作为本次开发的参考。**

---

## 3. 技术方案对比

### 3.1 候选方案

| 方案 | 语言支持 | 安全性 | 性能 | 复杂度 | 依赖 |
|------|----------|--------|------|--------|------|
| **A. GraalVM Polyglot** | JS/Python/Ruby/LLVM | ★★★★★ 内置沙盒 | ★★★★ 预热后快 | 中 | graalvm-js (轻量) |
| **B. Nashorn + ClassFilter** | 仅 JS | ★★★ 白名单 | ★★★★ 快 | 低 | 已有 |
| **C. 进程隔离** | 任意 | ★★★★ OS 级 | ★★★ 进程开销 | 中高 | 语言运行时 |
| **D. Docker 容器** | 任意 | ★★★★★ 完全隔离 | ★★ 启动慢 | 高 | Docker daemon |

### 3.2 推荐方案：A + B 组合

**JavaScript 走 GraalVM Polyglot**（安全沙盒 + 多语言扩展能力），**Java 走 Janino 编译执行**（轻量级运行时编译器）。

理由：
1. GraalVM 的 `Context` API 提供 `allowIO(false)`、`allowNativeAccess(false)` 等细粒度权限控制，比自己写 ClassFilter 安全得多
2. Janino 是一个嵌入式 Java 编译器（~600KB），可在 JVM 内编译并执行 Java 代码片段，无需 GraalVM 全家桶
3. 两者都是纯 Java 库，不依赖外部进程/容器，符合项目 "Java First" 原则
4. 设计文档推荐的 GraalVM 方案方向一致，可复用其白名单设计

---

## 4. 技术设计

### 4.1 架构总览

```
┌─────────────────────────────────────────────────────────┐
│                    Agent 对话                             │
│                       │                                   │
│              LLM 决定调用 execute_code                     │
│                       │                                   │
│              ┌────────▼────────┐                          │
│              │ ExecuteCodeTool │  ← @Tool 入口             │
│              └────────┬────────┘                          │
│                       │                                   │
│              ┌────────▼────────┐                          │
│              │ CodeExecService │  ← 语言路由 + 结果格式化   │
│              └───┬─────────┬──┘                           │
│                  │         │                               │
│     ┌────────────▼──┐  ┌──▼─────────────┐                │
│     │ GraalVmEngine │  │ JaninoEngine   │                │
│     │ (JavaScript)  │  │ (Java)         │                │
│     └───────────────┘  └────────────────┘                │
│            │                   │                          │
│     ┌──────▼──────┐    ┌──────▼──────┐                   │
│     │ GraalVM     │    │ Janino      │                   │
│     │ Context     │    │ SimpleCompiler│                  │
│     │ (沙盒)      │    │ (沙盒)       │                  │
│     └─────────────┘    └─────────────┘                   │
└─────────────────────────────────────────────────────────┘
```

### 4.2 核心类设计

#### 4.2.1 ExecuteCodeTool（工具入口）

```java
/**
 * 代码执行工具：支持 JavaScript / Java 代码片段执行
 *
 * @author finch
 * @since 2026-06-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SystemTool(displayName = "代码执行", tags = {"code", "execution", "sandbox"})
public class ExecuteCodeTool {

    private final CodeExecService codeExecService;

    @Tool(name = "execute_code", description = "在安全沙盒中执行代码片段并返回结果。支持 JavaScript 和 Java。")
    public String execute(
            @ToolParam(description = "要执行的代码") @ToolParamMeta(example = "return LocalDateTime.now().toString()") String code,
            @ToolParam(description = "编程语言", required = false) @ToolParamMeta(example = "java") String language
    ) {
        // 参数校验
        // 路由到 CodeExecService
        // 返回 JSON 结果
    }
}
```

#### 4.2.2 CodeExecService（执行引擎）

```java
/**
 * 代码执行服务：语言路由、超时控制、结果格式化
 */
public interface CodeExecService {
    CodeExecResult execute(String code, String language, long timeoutMs);
}
```

#### 4.2.3 执行结果 DTO

```java
public class CodeExecResult {
    private boolean success;      // 是否成功
    private String output;        // stdout 输出
    private String returnValue;   // 返回值（toString）
    private String error;         // 错误信息
    private long elapsedMs;       // 执行耗时
    private String language;      // 实际使用的语言
}
```

#### 4.2.4 GraalVmEngine（JavaScript 引擎）

```java
/**
 * GraalVM Polyglot JavaScript 沙盒引擎
 * <p>使用 Context.Builder 配置安全限制</p>
 */
@Component
public class GraalVmEngine implements CodeEngine {

    @Override
    public CodeExecResult execute(String code, long timeoutMs) {
        // 1. 构建 GraalVM Context（沙盒配置）
        //    - allowIO(false)
        //    - allowNativeAccess(false)
        //    - allowCreateThread(false)
        //    - allowHostAccess(HostAccess.EXPLICIT)
        //    - option("engine.max-heap", "64m")
        //    - option("sandbox.MaxCPUTime", timeout)
        // 2. 注入安全的内置对象（print → 捕获输出）
        // 3. 执行代码
        // 4. 提取结果
        // 5. close Context
    }
}
```

#### 4.2.5 JaninoEngine（Java 引擎）

```java
/**
 * Janino Java 编译执行引擎
 * <p>将用户代码包装为一个类的 main 方法，编译后执行</p>
 */
@Component
public class JaninoEngine implements CodeEngine {

    @Override
    public CodeExecResult execute(String code, long timeoutMs) {
        // 1. 将用户代码包装为完整 Java 类
        //    - 添加常用 import（java.time, java.util, java.math 等）
        //    - 包装为 public class Sandbox_Xxx { public static Object run() { ... } }
        // 2. Janino 编译（SimpleCompiler）
        // 3. ClassLoader 加载（自定义 SandboxingClassLoader 限制可加载的包）
        // 4. CompletableFuture.orTimeout() 执行 + 超时控制
        // 5. 捕获 stdout（System.setOut 重定向）和返回值
        // 6. 恢复 System.out
    }
}
```

### 4.3 安全设计

#### 4.3.1 GraalVM 沙盒（JavaScript）

GraalVM Context API 提供细粒度权限控制：

| 配置 | 值 | 说明 |
|------|-----|------|
| `allowIO` | false | 禁止文件读写 |
| `allowNativeAccess` | false | 禁止 JNI 调用 |
| `allowCreateThread` | false | 禁止创建线程 |
| `allowHostAccess` | EXPLICIT | 仅允许显式注册的 Java 类 |
| `allowHostClassLookup` | false | 禁止反射查找类 |
| `engine.max-heap` | 64m | 内存上限 |
| `sandbox.MaxCPUTime` | 5000ms | CPU 时间限制 |

#### 4.3.2 Janino 沙盒（Java）

Java 执行需要更严格的安全控制：

| 层级 | 措施 | 说明 |
|------|------|------|
| 编译期 | 代码预检查 | 正则拦截危险关键字（`Runtime`, `ProcessBuilder`, `System.exit`, `Thread`, `Class.forName` 等） |
| 编译期 | import 白名单 | 仅允许 `java.time`, `java.util`, `java.math`, `java.text`, `java.stream` 等安全包 |
| 类加载 | SandboxingClassLoader | 自定义 ClassLoader，`loadClass()` 拦截非白名单类 |
| 运行时 | System.setOut 重定向 | 捕获 stdout，禁止 stderr 泄露系统信息 |
| 运行时 | CompletableFuture.orTimeout | 5 秒超时强制中断 |
| 运行时 | 独立线程池 | ForkJoinPool.commonPool() 隔离，不占用主业务线程 |

#### 4.3.3 危险关键字黑名单（Java）

```java
private static final List<String> BLOCKED_KEYWORDS = List.of(
    "Runtime", "ProcessBuilder", "System.exit", "System.setOut", "System.setErr",
    "Thread", "ClassLoader", "Class.forName", "Method.invoke", "Field.set",
    "FileInputStream", "FileOutputStream", "RandomAccessFile",
    "Socket", "ServerSocket", "HttpURLConnection", "URL.openConnection",
    "javax.script", "java.lang.reflect", "sun.misc.Unsafe",
    "ProcessHandle", "ProcessBuilder"
);
```

### 4.4 工具输出格式

```json
{
  "language": "java",
  "success": true,
  "output": "Hello World",
  "returnValue": "2026-06-24T10:30:00",
  "elapsedMs": 42
}
```

错误时：

```json
{
  "language": "java",
  "success": false,
  "error": "编译错误: 找不到符号 System.out.pritnln",
  "elapsedMs": 15
}
```

### 4.5 LLM Prompt 设计

工具描述（`@Tool.description`）：

```
在安全沙盒中执行代码片段并返回结果。
支持 JavaScript（language="javascript"）和 Java（language="java"）。
默认使用 Java。

注意事项：
- 代码必须有返回值或 print 输出，否则结果为空
- Java 代码直接写方法体，无需 class/main 声明
- 禁止文件/网络/进程操作
- 执行超时限制为 5 秒

示例：
- 获取当前时间：return java.time.LocalDateTime.now().toString();
- 数学计算：return Math.pow(2, 10);
- 字符串处理：return "Hello " + input.toUpperCase();
```

---

## 5. 难点与风险

### 5.1 安全性（高风险）

| 风险 | 描述 | 缓解措施 |
|------|------|----------|
| 反射绕过 | `Class.forName("java.lang.Runtime")` 绕过关键字检查 | GraalVM `allowHostClassLookup(false)` + Janino SandboxingClassLoader 拦截 |
| 无限循环 | `while(true){}` 耗尽 CPU | GraalVM CPU 时间限制 + CompletableFuture 超时 |
| 内存炸弹 | `new byte[Integer.MAX_VALUE]` OOM | GraalVM `max-heap` + JVM `-Xmx` 限制 |
| 字符串拼接攻击 | 超长字符串导致内存溢出 | 输出截断（maxLength = 10000 字符） |
| 序列化攻击 | `ObjectInputStream` 反序列化 | import 黑名单拦截 `java.io.*` |

**结论**：GraalVM 沙盒提供 OS 级别的安全保证，Janino 方案依赖代码审查 + ClassLoader，安全性略低但仍可接受。建议 Janino 方案作为 "受控环境" 选项，生产环境优先推荐 GraalVM。

### 5.2 Java 沙盒的局限性（中风险）

Janino 是编译器而非运行时，存在以下限制：

1. **不支持匿名内部类/lambda 复杂语法** — Janino 支持有限，复杂代码可能编译失败
2. **不支持 Java 特性如 `var`（部分版本）** — 需要明确类型声明
3. **ClassLoader 隔离非绝对安全** — 高级攻击者可能通过 `sun.misc.Unsafe` 等绕过
4. **类加载泄露** — 每次执行产生新 Class，长期运行可能导致 Metaspace 增长

**缓解**：限制 Janino 执行复杂度，复杂场景建议用户使用 JavaScript（GraalVM 沙盒更安全）。

### 5.3 性能（低风险）

| 引擎 | 首次执行 | 后续执行 | 说明 |
|------|----------|----------|------|
| GraalVM JS | ~200ms（Context 创建） | ~5ms | Context 可复用 |
| Janino | ~100ms（编译） | ~10ms | 编译结果可缓存 |

**优化**：GraalVM Context 和 Janino 编译结果均可缓存（按代码 hash），但需注意缓存大小限制。

---

## 6. 新增依赖

### 6.1 Maven 依赖

```xml
<!-- GraalVM JavaScript 引擎（轻量版，不需要完整 GraalVM） -->
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>polyglot</artifactId>
    <version>24.1.2</version>
</dependency>
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>js</artifactId>
    <version>24.1.2</version>
    <classifier>community</classifier>
</dependency>
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>polyglot</artifactId>
    <version>24.1.2</version>
    <classifier>community</classifier>
</dependency>

<!-- Janino Java 编译器 -->
<dependency>
    <groupId>org.codehaus.janino</groupId>
    <artifactId>janino</artifactId>
    <version>3.1.12</version>
</dependency>
```

**注意**：GraalVM Polyglot 的 `community` classifier 使用开源版本，不需要 GraalVM Enterprise 许可证。

### 6.2 依赖大小

| 依赖 | JAR 大小 | 说明 |
|------|----------|------|
| graalvm-polyglot | ~1.5MB | 核心 API |
| graalvm-js | ~8MB | JavaScript 引擎 |
| janino | ~600KB | Java 编译器 |
| **合计** | **~10MB** | 可接受 |

### 6.3 不需要的中间件

- 不需要 Docker
- 不需要额外数据库表（工具通过 ToolRegistrar 自动注册）
- 不需要 Redis（代码无状态）
- 不需要 MinIO

---

## 7. 工作量评估

### 7.1 后端开发

| 任务 | 文件 | 行数 | 工时 | 说明 |
|------|------|------|------|------|
| CodeExecResult DTO | `dto/CodeExecResult.java` | ~40 | 0.5h | 执行结果封装 |
| CodeEngine 接口 | `service/sandbox/CodeEngine.java` | ~20 | 0.5h | 引擎接口定义 |
| GraalVmEngine | `service/sandbox/GraalVmEngine.java` | ~120 | 3h | GraalVM Context 沙盒 + 配置 |
| JaninoEngine | `service/sandbox/JaninoEngine.java` | ~180 | 4h | 代码包装 + 编译 + ClassLoader + 超时 |
| CodeExecService | `service/sandbox/CodeExecService.java` | ~80 | 2h | 语言路由 + 日志 + 结果格式化 |
| ExecuteCodeTool | `tool/builtin/ExecuteCodeTool.java` | ~60 | 1.5h | @Tool 注册 + 参数校验 |
| 单元测试 | `test/.../ExecuteCodeToolTest.java` | ~150 | 3h | 安全测试 + 边界测试 |
| **后端合计** | | **~650 行** | **~14.5h** | |

### 7.2 前端开发（可选）

| 任务 | 文件 | 工时 | 说明 |
|------|------|------|------|
| ExecuteCodeResult.vue | `components/tools/ExecuteCodeResult.vue` | 2h | 代码执行结果渲染组件 |
| toolRegistry.js 更新 | `components/toolRegistry.js` | 0.5h | 注册渲染组件 |
| **前端合计** | | **~2.5h** | |

### 7.3 总工作量

| 阶段 | 工时 | 说明 |
|------|------|------|
| 后端核心 | 14.5h | 引擎 + 服务 + 工具 |
| 前端渲染 | 2.5h | 结果展示组件 |
| 集成测试 | 2h | 端到端验证 |
| **合计** | **~19h（约 2.5 天）** | |

### 7.4 里程碑

| 阶段 | 交付物 | 工时 |
|------|--------|------|
| M1: JS 沙盒 | GraalVmEngine + ExecuteCodeTool（仅 JS） | 6h |
| M2: Java 沙盒 | JaninoEngine + 安全限制 | 6h |
| M3: 前端 + 测试 | ExecuteCodeResult.vue + 单元/集成测试 | 5h |
| M4: 优化 | 缓存、日志、监控 | 2h |

---

## 8. 后续扩展

### 8.1 多语言支持

GraalVM Polyglot 天然支持多种语言，后续可扩展：

| 语言 | 依赖 | 难度 |
|------|------|------|
| Python | `graalvm-python` (~30MB) | 低（配置即可） |
| Ruby | `graalvm-ruby` (~40MB) | 低 |
| R | `graalvm-r` (~50MB) | 低 |
| LLVM (C/C++/Rust) | `graalvm-llvm` (~20MB) | 中（需要编译步骤） |

### 8.2 工作流集成

将 `ExecuteCodeTool` 同时注册为工作流节点，替换现有的 `ScriptNodeProcessor`（L0 安全级别），实现代码执行安全等级统一。

### 8.3 代码模板

预置常用代码模板，降低 LLM 生成代码的出错率：

```json
{
  "templates": {
    "current_time": "return java.time.LocalDateTime.now().toString();",
    "uuid": "return java.util.UUID.randomUUID().toString();",
    "base64_encode": "return java.util.Base64.getEncoder().encodeToString(input.getBytes());",
    "json_format": "return new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(input);"
  }
}
```
