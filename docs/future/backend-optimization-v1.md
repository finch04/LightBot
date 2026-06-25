# 后端优化文档 v1.0

> 基于完整代码审查（全量扫描 lightbot-server/src 下所有 .java 文件），聚焦安全漏洞、并发问题、重复逻辑、性能优化、资源管理
>
> 生成时间：2026-06-25
> 前序版本：v0.9（2026-06-23，已完成项标记 ✅）

---

## 一、需求分析

### 1.1 安全漏洞（高优先级）

#### 1.1.1 SSRF — WebFetchUtil 无内网 IP 过滤

**文件**：`lightbot-server/.../util/WebFetchUtil.java`，line 120-131

**问题**：`isValidUrl()` 仅检查 scheme 为 http/https，不解析 DNS、不拦截内网/私有 IP（127.0.0.1、10.x、172.16-31.x、192.168.x、169.254.x）。攻击者可通过文档 URL 预览功能探测内部服务、云元数据端点（169.254.169.254）。

**影响**：SSRF 攻击面暴露，可访问内部 API、数据库、云元数据。

**修复**：解析 hostname 到 IP 后拒绝私有/保留 IP 段，复用 `ApiToolExecutionService.validateUrl()` 的校验逻辑。

#### 1.1.2 日志/任务端点未鉴权

**文件**：`lightbot-server/.../config/SaTokenConfig.java`，line 23-24

**问题**：`/api/logs/**` 和 `/api/tasks/stream` 被排除在认证之外。未认证用户可读取应用日志（含堆栈、内部路径、用户数据）。

**影响**：敏感信息泄露。

**修复**：移除排除规则或改为仅 ADMIN 角色可访问。SSE 流若需无 cookie 认证，改为手动校验 query token。

#### 1.1.3 MinIO Bucket 公开读策略

**文件**：`lightbot-server/.../util/MinioUtil.java`，约 line 354-366

**问题**：`ensureBucketOnce()` 设置 bucket policy 授予 `principal: *`（所有人）`s3:GetObject` 权限。所有上传文件（文档、头像、敏感内容）均可通过 MinIO URL 直接访问。

**影响**：任何发现 URL 规则的用户可无需认证访问所有文件。

**修复**：改用 presigned URL 做限时访问，移除公开读策略。

#### 1.1.4 SSE 任务流接受 userId 查询参数

**文件**：`lightbot-server/.../controller/TaskEventController.java`，line 37-38

**问题**：`@RequestParam Long userId` 从请求参数获取，而非从认证会话获取。已认证用户可传入其他用户 ID 接收其任务通知。

**影响**：跨用户信息泄露。

**修复**：改为 `StpUtil.getLoginIdAsLong()`。

#### 1.1.5 登录/注册无限速限制

**文件**：`lightbot-server/.../controller/AuthController.java`

**问题**：`/api/auth/login` 和 `/api/auth/register` 无速率限制、账户锁定、验证码。

**影响**：暴力破解密码、批量注册攻击。

**修复**：基于 Redis 的计数器实现速率限制，N 次失败后锁定账户。

#### 1.1.6 Chat/LLM 端点无限速限制

**文件**：`lightbot-server/.../controller/ChatController.java`

**问题**：`/api/chat` 和 `/api/chat/stream` 无每用户速率限制或并发上限。

**影响**：单用户可耗尽 LLM API 配额和服务器资源。

**修复**：添加每用户速率限制和并发上限。

#### 1.1.7 application.yml 硬编码默认凭证

**文件**：`lightbot-server/src/main/resources/application.yml`

**问题**：数据库密码 `postgres`、MinIO 密钥 `minioadmin`、Neo4j 密码 `neo4j123` 均为知名默认值。

**修复**：所有凭证使用环境变量，无默认值或默认值为空。

#### 1.1.8 Sa-Token 无活跃超时

**文件**：`application.yml`，line 88-95

**问题**：token 有效期 7 天，`active-timeout: -1`（无活跃超时），无限并发会话。

**修复**：设置 `active-timeout: 3600`（1 小时），限制并发会话数。

#### 1.1.9 Swagger 生产环境暴露

**文件**：`application.yml`，line 178-184

**问题**：Swagger UI 在所有环境启用，无 profile 条件控制。

**修复**：生产 profile 下禁用，或限制为 ADMIN 认证可访问。

---

### 1.2 并发 / 数据一致性（高优先级）

#### 1.2.1 UserServiceImpl.initAdmin() TOCTOU 竞态

**文件**：`lightbot-server/.../service/impl/UserServiceImpl.java`，line 363-384

**问题**：先 `hasAnyUser()` 检查，再 insert。并发启动时两个实例均可通过检查，创建重复管理员。

**修复**：数据库唯一约束 + `INSERT ... WHERE NOT EXISTS`，或 advisory lock。

#### 1.2.2 AgentServiceImpl.setDefaultAgent() 非原子切换

**文件**：`lightbot-server/.../service/impl/AgentServiceImpl.java`，line 575-593

**问题**：先清除所有默认（step 2），再设置新默认（step 3）。进程崩溃在两步之间时，用户无默认 Agent。

**修复**：`@Transactional` + 单条 SQL CASE 表达式，或乐观锁。

#### 1.2.3 KnowledgeServiceImpl.updateStats() 读-改-写竞态

**文件**：`lightbot-server/.../service/impl/KnowledgeServiceImpl.java`，line 207-217

**问题**：读取当前统计 → Java 内存累加 → `updateById()`。并发上传文档时计数丢失。

**修复**：SQL 原子递增 `UPDATE knowledge SET document_count = document_count + ? WHERE id = ?`。

#### 1.2.4 ChatSessionServiceImpl.updateStats() 读-改-写竞态

**文件**：`lightbot-server/.../service/impl/ChatSessionServiceImpl.java`，line 190-202

**问题**：同 1.2.3 模式。高频聊天消息导致 messageCount / totalTokens 丢失更新。

**修复**：SQL 原子递增。

---

### 1.3 重复逻辑（中优先级）

#### 1.3.1 RagServiceImpl.ask() 与 askStream() 90% 重复

**文件**：`lightbot-server/.../service/impl/RagServiceImpl.java`，line 72-183

**问题**：知识库校验、权限检查、参数解析、embedding、向量搜索、上下文构建完全重复，仅最终 LLM 调用不同（同步 vs 流式）。

**修复**：提取公共 pipeline 方法返回 prepared context，`ask`/`askStream` 仅差异在最终调用。

#### 1.3.2 deleteOldAvatar 重复

**文件**：`AgentServiceImpl.java`（line 555-564）、`UserServiceImpl.java`

**问题**：两个 Service 包含几乎相同的头像删除逻辑（URL 解析 + MinIO 删除）。

**修复**：提取到 `MinioUtil.deleteAvatar(String url)`。

#### 1.3.3 toVectorString 重复

**文件**：`EmbeddingServiceImpl.java`、`QaPairServiceImpl.java`

**问题**：两个 Service 各自实现 `float[] → PostgreSQL vector 字符串` 转换。

**修复**：提取到 `VectorUtil.toVectorString(float[])`。

#### 1.3.4 parseJson 重复

**文件**：`RagServiceImpl.java`、`KnowledgeServiceImpl.java`

**问题**：两个 Service 各有 `parseJson(String)` 安全解析 JSON 为 JsonNode。

**修复**：提取到 `JsonUtil.parseJson(String)`。

#### 1.3.5 calculateHash / sha256 重复

**文件**：`DocumentServiceImpl.java`（line 835-847）、`SkillServiceImpl.java`

**问题**：多个 Service 各自实现哈希计算，算法不一致（MD5 vs SHA-256）。

**修复**：提取到 `HashUtil`。

#### 1.3.6 cleanStaleToolIds 重复

**文件**：`SkillServiceImpl.java`、`SubAgentServiceImpl.java`

**问题**：两个 Service 包含相同的过滤过期 toolId 逻辑。

**修复**：提取到共享工具方法。

---

### 1.4 性能问题（中优先级）

#### 1.4.1 QaPairServiceImpl.estimateTokenCount() 逐字符正则

**文件**：`lightbot-server/.../service/impl/QaPairServiceImpl.java`，line 343-355

**问题**：对每个字符调用 `Character.toString(c).matches("[\\u4e00-\\u9fa5]")`，创建 String 对象 + 编译运行正则。O(n) 次正则操作。

**影响**：万字文本触发万次正则匹配，严重拖慢 token 估算。

**修复**：`c >= '一' && c <= '龥'` 直接字符比较，O(1)。

#### 1.4.2 DashboardServiceImpl.getBasicStats() 全表扫描无缓存

**文件**：`lightbot-server/.../service/impl/DashboardServiceImpl.java`，line 41-52

**问题**：`selectCount(null)` 对每张表做全表扫描，无缓存。管理员频繁访问仪表盘时产生不必要的数据库负载。

**修复**：`@Cacheable` + 短 TTL（30-60s）。

#### 1.4.3 GraphServiceImpl.writeTriplesToNeo4j() 逐条 Cypher

**文件**：`lightbot-server/.../service/impl/GraphServiceImpl.java`，line 681-746

**问题**：每个 triple 3 次 Neo4j round trip（head MERGE、tail MERGE、relationship MERGE）。N 个 triple = 3N 次调用。

**修复**：`UNWIND $triples AS triple MERGE ...` 批量写入，3N → 1。

#### 1.4.4 MemoryLogAppender.getRecentLogs() O(n) 遍历

**文件**：`lightbot-server/.../log/MemoryLogAppender.java`，line 63-66

**问题**：`stream().skip(fromIndex).toList()` 从头遍历跳过，请求最后 50 条需遍历前 1950 条。

**修复**：换用 `ArrayList` + `subList(fromIndex, size)` 或支持随机访问的数据结构。

#### 1.4.5 KnowledgeController.generateQuestions() 同步阻塞

**文件**：`lightbot-server/.../controller/KnowledgeController.java`，line 181-190

**问题**：遍历所有文档逐个调用 LLM 生成问题，同步阻塞 Servlet 线程。文档多时 HTTP 请求超时。

**修复**：改为异步操作，立即返回 taskId，后台处理。

#### 1.4.6 ModelServiceImpl.syncCache() 全量重载

**文件**：`lightbot-server/.../service/impl/ModelServiceImpl.java`，line 86-88

**问题**：每次模型增删都全量加载整个 model 表覆盖缓存。

**修复**：仅更新受影响 providerId 的缓存条目。

---

### 1.5 错误处理（中优先级）

#### 1.5.1 AgentServiceImpl 绑定更新静默吞异常（2 处）

**文件**：`AgentServiceImpl.java`，line 390-392（`updateKnowledgeBindings`）、line 510-512（`writeBindingIdsToConfig`）

**问题**：catch 块仅日志记录不抛出，调用方认为更新成功，实际数据未变更。

**修复**：rethrow 为 `BizException`。

#### 1.5.2 EvalExperimentServiceImpl 验证静默跳过

**文件**：`EvalExperimentServiceImpl.java`，line 403-414

**问题**：evaluator config JSON 解析失败时 catch 块 warn + return，绕过数量校验。

**修复**：throw `BizException`。

#### 1.5.3 DocumentServiceImpl.calculateHash() 失败返回时间戳

**文件**：`DocumentServiceImpl.java`，line 835-847

**问题**：MD5 计算失败时返回 `System.currentTimeMillis()` 作为 hash。语义错误，去重机制失效。

**修复**：rethrow 或 throw `BizException`。

#### 1.5.4 GlobalExceptionHandler 缺少常见异常处理器

**文件**：`GlobalExceptionHandler.java`

**问题**：缺少 `HttpMessageNotReadableException`（400）、`NoHandlerFoundException`（404）、`HttpRequestMethodNotSupportedException`（405）、`MaxUploadSizeExceededException` 等处理。客户端错误返回 500 而非正确的 4xx。

**修复**：添加对应异常处理器。

---

### 1.6 资源管理（中优先级）

#### 1.6.1 DocumentServiceImpl.INGEST_EXECUTOR 静态线程池未关闭

**文件**：`DocumentServiceImpl.java`，line 81-85

**问题**：`Executors.newFixedThreadPool(3)` 静态创建，无 `@PreDestroy` shutdown。优雅停机时进行中的 ingest 任务被强制终止，文档卡在 PROCESSING 状态。

**修复**：添加 `@PreDestroy` shutdown + `awaitTermination`，或改用 `lightBotExecutor` bean。

#### 1.6.2 EmbeddingServiceImpl 缓存无驱逐

**文件**：`EmbeddingServiceImpl.java`，line 57-60

**问题**：`collectionExistsCache` 和 `routingCache` 为无界 `ConcurrentHashMap`。知识库删除后条目永不清理，可能导致错误路由。

**修复**：改用 Caffeine cache + TTL，或监听知识库删除事件驱逐。

#### 1.6.3 KnowledgeDocController InputStream 未显式关闭

**文件**：`KnowledgeDocController.java`，line 139-148、152-166

**问题**：MinIO InputStream 包装在 `InputStreamResource` 中无 close 机制。客户端断连时流可能泄漏。

**修复**：使用 `InputStreamResource` 的 closeable 参数。

#### 1.6.4 ChatAttachmentServiceImpl 全文件读入内存

**文件**：`ChatAttachmentServiceImpl.java`，line 61

**问题**：`file.getBytes()` 将整个上传文件加载到 byte[]。大文件可导致 OOM。

**修复**：改用 `file.getInputStream()` 流式上传到 MinIO。

#### 1.6.5 SensitiveWordFilter Pattern 缓存无界

**文件**：`SensitiveWordFilter.java`，line 28

**问题**：`PATTERN_CACHE` 为无界 `ConcurrentHashMap`，敏感词多时内存压力大。

**修复**：改用 Caffeine cache + max size。

---

### 1.7 代码质量（低优先级）

#### 1.7.1 Controller 直接使用 Entity 作为 @RequestBody

**文件**：`AgentController.java`（line 44, 50）、`KnowledgeController.java`（line 42, 48）

**问题**：Entity 直接暴露为 API 输入/输出，客户端可设置 `id`、`userId`、`createTime` 等内部字段。

**修复**：创建专用 Request DTO。

#### 1.7.2 RagServiceImpl 接口转具体实现类

**文件**：`RagServiceImpl.java`，line 95, 152

**问题**：`((EmbeddingServiceImpl) embeddingService).searchSimilarSql(...)` 破坏抽象，无法 mock 测试。

**修复**：将 `searchSimilarSql` 加入 `EmbeddingService` 接口。

#### 1.7.3 GraphServiceImpl.snowflakeId() 非雪花算法

**文件**：`GraphServiceImpl.java`，line 912-914

**问题**：`ThreadLocalRandom` 生成随机 long，无唯一性保证。并发时可能 ID 碰撞。

**修复**：使用 `IdWorker.getId()` 或 UUID。

#### 1.7.4 LlmTraceServiceImpl @Async 未指定线程池

**文件**：`LlmTraceServiceImpl.java`，line 20

**问题**：`@Async` 无 executor 参数，默认使用 `SimpleAsyncTaskExecutor`（每次创建新线程，无界）。

**修复**：`@Async("lightBotExecutor")`。

#### 1.7.5 DocumentEditServiceImpl.saveContent() 同步触发重建

**文件**：`DocumentEditServiceImpl.java`，line 149-153

**问题**：注释写 "async trigger" 但实际同步调用 `ingestDocument()`。保存响应被 ingest 延迟，失败时用户无感知。

**修复**：提交到 `INGEST_EXECUTOR` 或 `@Async`，立即返回。

#### 1.7.6 DocumentServiceImpl.estimateTokens() 与 TokenUtil 不一致

**文件**：`DocumentServiceImpl.java`，line 849-851

**问题**：`text.length() * 1.2` 粗略估算，中文 token 通常 1.5-2x，误差大。

**修复**：统一使用 `TokenUtil.countTokens()`。

#### 1.7.7 LogController 静态 ExecutorService 未关闭

**文件**：`LogController.java`，line 42-47

**问题**：`ScheduledExecutorService` 静态创建，无 `@PreDestroy` shutdown。

**修复**：添加 `@PreDestroy` 方法。

#### 1.7.8 application.yml 硬编码 Windows OCR 路径

**文件**：`application.yml`，line 135

**问题**：`model-path: D:\models\RapidOCR\PP-OCRv4` 硬编码 Windows 路径，Linux 部署失败。

**修复**：使用相对路径或环境变量。

---

## 二、技术设计

### 2.1 SSRF 防护（统一校验）

```java
public static void validateNotInternalIp(String host) throws UnknownHostException {
    InetAddress addr = InetAddress.getByName(host);
    if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
        || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
        throw new BizException(ErrorCode.SSRF_BLOCKED);
    }
    // 检查 169.254.x.x 元数据端点
    byte[] ip = addr.getAddress();
    if (ip[0] == (byte)169 && ip[1] == (byte)254) {
        throw new BizException(ErrorCode.SSRF_BLOCKED);
    }
}
```

### 2.2 原子更新（统一模式）

```java
// Mapper 层
@Update("UPDATE knowledge SET document_count = document_count + #{docDelta}, " +
        "chunk_count = document_count + #{chunkDelta}, " +
        "total_tokens = total_tokens + #{tokenDelta} WHERE id = #{id}")
int incrementStats(@Param("id") Long id,
                   @Param("docDelta") int docDelta,
                   @Param("chunkDelta") int chunkDelta,
                   @Param("tokenDelta") long tokenDelta);
```

### 2.3 Neo4j 批量写入

```java
String cypher = """
    UNWIND $triples AS triple
    MERGE (h:Entity {name: triple.head})
    ON CREATE SET h.createdAt = timestamp()
    MERGE (t:Entity {name: triple.tail})
    ON CREATE SET t.createdAt = timestamp()
    MERGE (h)-[r:RELATION {type: triple.relation}]->(t)
    ON CREATE SET r.createdAt = timestamp()
    """;
session.run(cypher, Values.parameters("triples", tripleList));
```

### 2.4 速率限制（Redis 计数器）

```java
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements Filter {
    private final RedisUtil redisUtil;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        String key = "rate:" + getClientIp((HttpServletRequest) req);
        Long count = redisUtil.incr(key, 1);
        if (count == 1) redisUtil.expire(key, 60); // 60 秒窗口
        if (count > 100) { // 每分钟 100 次
            ((HttpServletResponse) res).setStatus(429);
            return;
        }
        chain.doFilter(req, res);
    }
}
```

### 2.5 GlobalExceptionHandler 补全

```java
@ExceptionHandler(HttpMessageNotReadableException.class)
public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
    return Result.fail(ErrorCode.BAD_REQUEST, "请求体格式错误");
}

@ExceptionHandler(NoHandlerFoundException.class)
public Result<Void> handleNotFound(NoHandlerFoundException e) {
    return Result.fail(ErrorCode.NOT_FOUND, "接口不存在");
}

@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
public Result<Void> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
    return Result.fail(ErrorCode.METHOD_NOT_ALLOWED, "请求方法不支持");
}
```

---

## 三、难点分析

| 优化项 | 难度 | 说明 |
|--------|------|------|
| SSRF 防护 | **低** | DNS 解析 + IP 范围检查，复用已有逻辑 |
| 日志端点鉴权 | **低** | SaTokenConfig 修改排除规则 |
| MinIO 公开读 → presigned URL | **中** | 需改造所有文件访问路径 |
| initAdmin 竞态 | **低** | 数据库唯一约束 |
| updateStats 原子化 | **低** | 改为 SQL 原子递增 |
| setDefaultAgent 原子化 | **中** | 需 @Transactional + SQL 改写 |
| ChatServiceImpl 拆分 | **高** | 核心链路，需保证流式/非流式均正确（v0.9 遗留） |
| Neo4j 批量写入 | **中** | UNWIND 语法 + 参数映射 |
| 速率限制 | **中** | Redis 计数器 + Filter/注解方案选择 |
| 全文 CSS 提取 | **中** | 需确认各页面差异点 |
| SensitiveWordFilter 缓存有界化 | **低** | 替换为 Caffeine |

---

## 四、工作量评估

### P0 — 安全 & 数据一致性（3-4 天）

| 任务 | 预估 | 状态 |
|------|------|------|
| SSRF 防护（WebFetchUtil） | 0.5d | 新增 |
| 日志端点鉴权 | 0.5d | 新增 |
| MinIO 公开读 → presigned URL | 1d | 新增 |
| SSE userId 参数 → 会话获取 | 0.5h | 新增 |
| 登录速率限制 | 1d | 新增 |
| initAdmin 竞态修复 | 0.5d | 新增 |
| updateStats 原子化（2 处） | 0.5d | 新增 |
| setDefaultAgent 原子化 | 0.5d | 新增 |

### P1 — 性能优化（2-3 天）

| 任务 | 预估 | 状态 |
|------|------|------|
| estimateTokenCount 字符比较 | 0.5h | 新增 |
| Dashboard 缓存 | 0.5d | 新增 |
| Neo4j 批量写入 | 1d | 新增 |
| MemoryLogAppender O(n) → O(limit) | 0.5h | 新增 |
| generateQuestions 异步化 | 0.5d | 新增 |
| ModelCache 精细化驱逐 | 0.5d | 新增 |
| ChatServiceImpl 拆分 | 3d | v0.9 遗留 |

### P2 — 代码质量 & 资源管理（2-3 天）

| 任务 | 预估 | 状态 |
|------|------|------|
| 重复逻辑提取（6 处） | 1.5d | 新增 |
| 错误处理修复（4 处） | 0.5d | 新增 |
| GlobalExceptionHandler 补全 | 0.5d | 新增 |
| INEST_EXECUTOR shutdown | 0.5h | 新增 |
| EmbeddingServiceImpl 缓存有界化 | 0.5h | 新增 |
| InputStream 关闭 | 0.5h | 新增 |
| ChatAttachment 流式上传 | 0.5d | 新增 |
| SensitiveWordFilter 缓存有界化 | 0.5h | 新增 |
| @Async 指定线程池 | 0.1h | 新增 |
| estimateTokens 统一 | 0.5h | 新增 |
| 硬编码路径/凭证修复 | 0.5d | 新增 |

**总预估（新增项）**：约 8-10 个工作日（不含 v0.9 遗留的 ChatServiceImpl 拆分）

---

## 五、涉及文件清单

| 文件 | 优化项 | 优先级 |
|------|--------|--------|
| `util/WebFetchUtil.java` | SSRF 防护 | P0 |
| `config/SaTokenConfig.java` | 日志端点鉴权 | P0 |
| `util/MinioUtil.java` | 公开读策略 → presigned URL | P0 |
| `controller/TaskEventController.java` | userId 参数修复 | P0 |
| `controller/AuthController.java` | 速率限制 | P0 |
| `controller/ChatController.java` | 速率限制 | P0 |
| `service/impl/UserServiceImpl.java` | initAdmin 竞态 | P0 |
| `service/impl/AgentServiceImpl.java` | setDefaultAgent 原子化、绑定异常吞没 | P0-P1 |
| `service/impl/KnowledgeServiceImpl.java` | updateStats 原子化、parseJson 重复 | P0-P2 |
| `service/impl/ChatSessionServiceImpl.java` | updateStats 原子化 | P0 |
| `service/impl/QaPairServiceImpl.java` | estimateTokenCount 性能、toVectorString 重复 | P1-P2 |
| `service/impl/DashboardServiceImpl.java` | 全表扫描缓存 | P1 |
| `service/impl/GraphServiceImpl.java` | Neo4j 批量写入、snowflakeId | P1-P2 |
| `log/MemoryLogAppender.java` | O(n) 遍历 | P1 |
| `controller/KnowledgeController.java` | generateQuestions 同步阻塞 | P1 |
| `service/impl/ModelServiceImpl.java` | 缓存全量重载 | P1 |
| `service/impl/ChatServiceImpl.java` | 拆分（v0.9 遗留） | P1 |
| `service/impl/RagServiceImpl.java` | ask/askStream 重复、接口转实现类 | P2 |
| `service/impl/DocumentServiceImpl.java` | hash 返回时间戳、estimateTokens 不一致、线程池未关闭 | P2 |
| `service/impl/SkillServiceImpl.java` | hash 重复、cleanStaleToolIds 重复 | P2 |
| `service/impl/SubAgentServiceImpl.java` | cleanStaleToolIds 重复 | P2 |
| `service/impl/EvalExperimentServiceImpl.java` | 验证静默跳过 | P2 |
| `service/impl/DocumentEditServiceImpl.java` | 同步触发重建 | P2 |
| `service/impl/LlmTraceServiceImpl.java` | @Async 未指定线程池 | P2 |
| `service/impl/EmbeddingServiceImpl.java` | 缓存无驱逐、toVectorString 重复 | P2 |
| `service/impl/ChatAttachmentServiceImpl.java` | 全文件读入内存 | P2 |
| `service/impl/UserServiceImpl.java` | deleteOldAvatar 重复 | P2 |
| `controller/KnowledgeDocController.java` | InputStream 未关闭 | P2 |
| `controller/AgentController.java` | Entity 作为 RequestBody | P2 |
| `common/GlobalExceptionHandler.java` | 缺少常见异常处理器 | P2 |
| `controller/LogController.java` | ExecutorService 未关闭 | P2 |
| `util/SensitiveWordFilter.java` | Pattern 缓存无界 | P2 |
| `application.yml` | 硬编码凭证、OCR 路径、Swagger、Sa-Token 超时 | P0-P2 |
| `pom.xml` | — | — |
| 新增 `util/VectorUtil.java` | toVectorString 提取 | P2 |
| 新增 `util/JsonUtil.java` | parseJson 提取 | P2 |
| 新增 `util/HashUtil.java` | 哈希计算提取 | P2 |
| 新增 `filter/RateLimitFilter.java` | 速率限制 | P0 |
