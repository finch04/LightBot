# 网页内容抓取方案：Playwright vs Jsoup 技术分析

## 1. 需求分析

### 业务场景

知识库详情页 → 上传文件 → 选择 Web URL → 输入 URL → 后端抓取网页正文 → 预览 → 确认上传。

### 核心痛点

当前使用 Jsoup 抓取，**只获取初始 HTML 响应，不执行 JavaScript**。导致：

| 问题场景 | 表现 | 根因 |
|----------|------|------|
| SPA 页面（Vue/React/Next.js） | 拿到空壳 `<div id="app"></div>` | JS 渲染的内容 Jsoup 看不到 |
| AJAX 动态加载 | 拿到 loading 占位符或骨架屏 | 数据通过接口异步返回，HTML 中无内容 |
| 延迟渲染 | 内容部分缺失 | 页面分批加载，Jsoup 只抓一次 |
| 反爬机制 | 拿到 403/验证码页 | 无头浏览器指纹缺失 |

### 目标

抓取经过 JavaScript 渲染后的**完整页面正文**，等同于用户在浏览器中看到的最终效果。

---

## 2. 方案对比

### 2.1 Jsoup（当前方案）

```java
Document doc = Jsoup.connect(url)
    .userAgent(USER_AGENT)
    .timeout(30000)
    .get();
```

| 维度 | 评价 |
|------|------|
| JS 执行 | ❌ 不支持 |
| 渲染等待 | ❌ 无 |
| 资源占用 | ✅ 极低（纯 HTTP） |
| 速度 | ✅ 快（毫秒级） |
| 部署依赖 | ✅ 无额外依赖 |
| 动态内容 | ❌ 无法获取 |

### 2.2 Playwright（推荐方案）

```java
Playwright playwright = Playwright.create();
Browser browser = playwright.chromium().launch();
Page page = browser.newPage();
page.navigate(url);
page.waitForLoadState(LoadState.NETWORKIDLE);
String html = page.content();
```

| 维度 | 评价 |
|------|------|
| JS 执行 | ✅ 完整 Chromium 内核 |
| 渲染等待 | ✅ 支持 networkidle / load / domcontentloaded |
| 资源占用 | ⚠️ 较高（Chromium 进程 ~200MB） |
| 速度 | ⚠️ 较慢（秒级，需等页面渲染） |
| 部署依赖 | ⚠️ 需安装浏览器二进制 |
| 动态内容 | ✅ 完整获取 |

### 2.3 综合对比

| 维度 | Jsoup | Playwright |
|------|-------|-----------|
| 动态内容获取 | ❌ | ✅ |
| 部署复杂度 | 低 | 中 |
| 运行时资源 | ~10MB | ~200MB/实例 |
| 单次抓取耗时 | 100-500ms | 3-15s |
| 并发能力 | 高（HTTP 连接池） | 低（浏览器实例有限） |
| 适用场景 | 静态页面、API | SPA、动态渲染页面 |
| 维护成本 | 低 | 中（浏览器版本更新） |

---

## 3. 技术设计

### 3.1 架构方案：Jsoup + Playwright 混合策略

**不建议完全替换 Jsoup**，而是采用**两级策略**：

```
URL 请求
  │
  ├─ 第一级：Jsoup 快速尝试（< 3s）
  │   ├─ 检测到有效正文 → 直接返回
  │   └─ 内容过少/疑似 loading → 进入第二级
  │
  └─ 第二级：Playwright 渲染抓取（< 15s）
      └─ 等待 networkidle → 提取渲染后 HTML → 解析正文
```

**理由：**
- 大部分文档类网站（博客、文档站、Wiki）是静态渲染，Jsoup 够用
- 只有 SPA / 动态加载站点需要 Playwright
- 混合策略兼顾速度和覆盖率

### 3.2 流程设计

```
                    ┌──────────────┐
                    │  fetch(url)  │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │  Jsoup 抓取  │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │  正文 > 80字  │──Yes──▶ 返回结果
                    └──────┬───────┘
                           │ No
                    ┌──────▼───────┐
                    │Playwright 抓取│
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │  提取正文     │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │  有有效内容？ │──Yes──▶ 返回结果
                    └──────┬───────┘
                           │ No
                    ┌──────▼───────┐
                    │  抛出异常     │
                    └──────────────┘
```

### 3.3 Playwright 抓取策略

```java
private Document loadWithPlaywright(String url) {
    // 1. 启动无头 Chromium
    Browser browser = playwright.chromium().launch(
        new LaunchOptions().setHeadless(true)
    );

    // 2. 创建页面，设置超时
    Page page = browser.newPage(new Browser.NewContextOptions()
        .setUserAgent(USER_AGENT)
        .setViewportSize(1920, 1080));

    // 3. 导航并等待渲染完成
    page.navigate(url, new NavigateOptions().setTimeout(30000));
    page.waitForLoadState(LoadState.NETWORKIDLE);  // 等网络空闲

    // 4. 可选：等待特定内容出现
    // page.waitForSelector("article, main, .content", 5000);

    // 5. 获取渲染后的 HTML
    String html = page.content();
    browser.close();

    return Jsoup.parse(html);  // 仍用 Jsoup 解析
}
```

### 3.4 智能等待策略

| 等待策略 | 适用场景 | 超时 |
|----------|----------|------|
| `networkidle` | 通用，等所有网络请求完成 | 10s |
| `domcontentloaded` | 内容在 HTML 中，无需等 JS | 5s |
| `waitForSelector` | 等特定元素出现（如 `article`） | 8s |
| 自定义轮询 | 检测正文长度变化 | 15s |

推荐默认用 `networkidle`，超时后降级到 `domcontentloaded`。

---

## 4. 新增依赖

### 4.1 Maven 依赖

```xml
<!-- Playwright Java -->
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.44.0</version>
</dependency>
```

### 4.2 运行时依赖

Playwright 需要浏览器二进制文件：

```bash
# 首次部署时执行（下载 Chromium ~150MB）
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```

或在 Dockerfile 中预装：

```dockerfile
# 基础镜像需要额外系统库
RUN npx playwright install --with-deps chromium
```

### 4.3 Docker 部署需要的系统库

```dockerfile
# Ubuntu/Debian 系
RUN apt-get update && apt-get install -y \
    libnss3 libnspr4 libatk1.0-0 libatk-bridge2.0-0 \
    libcups2 libdrm2 libdbus-1-3 libxkbcommon0 \
    libatspi2.0-0 libxcomposite1 libxdamage1 libxfixes3 \
    libxrandr2 libgbm1 libpango-1.0-0 libcairo2 libasound2 \
    fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*
```

### 4.4 资源评估

| 项目 | Jsoup | Playwright |
|------|-------|-----------|
| Maven 依赖大小 | ~400KB | ~30MB |
| 浏览器二进制 | 无 | ~150MB（Chromium） |
| 系统库 | 无 | ~80MB（.so 文件） |
| 运行时内存 | ~10MB | ~200MB/浏览器实例 |
| 磁盘总计 | ~0.5MB | ~250MB |

---

## 5. 难点与风险

### 5.1 资源管理（高风险）

**问题：** Playwright 浏览器实例是重量级进程，创建/销毁开销大。

```java
// ❌ 错误：每次请求都创建浏览器
Page page = playwright.chromium().launch();  // 每次 ~2s 启动
// ... 使用
browser.close();

// ✅ 正确：复用浏览器实例
@Component
public class WebFetchUtil {
    private final Browser browser;  // 单例复用

    @PostConstruct
    void init() {
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch();
    }

    @PreDestroy
    void destroy() {
        browser.close();
        playwright.close();
    }
}
```

**风险：** 浏览器长时间运行可能内存泄漏 → 需要定期重启机制。

### 5.2 并发控制（高风险）

**问题：** 单个 Chromium 实例并发 tab 数有限（~10-20），超过会 OOM。

```java
// 需要信号量控制并发
private final Semaphore semaphore = new Semaphore(5);  // 最多 5 个并发

public FetchResult fetch(String url) {
    semaphore.acquire();
    try {
        return doFetch(url);
    } finally {
        semaphore.release();
    }
}
```

### 5.3 超时与降级（中风险）

**问题：** Playwright 抓取可能因页面复杂、资源加载慢而超时。

**策略：**
- Playwright 总超时 15s
- `networkidle` 等待 10s，超时降级为直接取当前内容
- 降级后仍无内容 → 抛异常

### 5.4 部署环境差异（中风险）

**问题：** 本地开发 vs Docker 生产环境，浏览器二进制和系统库不一致。

**解决：**
- Dockerfile 中预装 Playwright 和依赖库
- CI/CD 中增加 `playwright install` 步骤
- 健康检查接口验证 Playwright 可用性

### 5.5 反爬与封禁（低风险）

**问题：** 部分网站检测无头浏览器特征并封禁。

**缓解：**
- 设置真实 User-Agent
- `--disable-blink-features=AutomationControlled` 隐藏自动化特征
- 加随机延迟

---

## 6. 性能预估

| 场景 | Jsoup | Playwright | 混合策略（推荐） |
|------|-------|-----------|----------------|
| 静态博客 | 200ms | 5s | 200ms（Jsoup） |
| Vue SPA | 拿到空壳 | 8s | 8s（降级到 Playwright） |
| 新闻网站（AJAX） | 拿到骨架屏 | 6s | 6s（降级到 Playwright） |
| 纯 API 返回 JSON | 100ms | 3s | 100ms（Jsoup） |

混合策略下，**80%+ 的请求走 Jsoup 快速路径**，只有动态页面才触发 Playwright。

---

## 7. 实施计划

| 阶段 | 内容 | 工时 |
|------|------|------|
| 1 | 添加 Playwright Maven 依赖 | 0.5h |
| 2 | 重构 `WebFetchUtil`，Jsoup + Playwright 混合策略 | 3h |
| 3 | 浏览器实例生命周期管理（单例 + 信号量） | 2h |
| 4 | 超时降级 + 错误处理 | 1h |
| 5 | Dockerfile 更新（安装浏览器 + 系统库） | 1h |
| 6 | 本地测试 + 常见网站验证 | 2h |
| **总计** | | **~9.5h** |

---

## 8. 结论

| 维度 | 结论 |
|------|------|
| 方案选择 | Jsoup + Playwright 混合策略 |
| 核心改动 | `WebFetchUtil.loadDocument()` 增加 Playwright 降级 |
| 部署影响 | Docker 镜像增大 ~250MB，运行时内存增加 ~200MB |
| 兼容性 | 静态页面性能不变，动态页面从"失败"变为"可抓取" |
| 推荐度 | ⭐⭐⭐⭐ 强烈推荐（解决实际业务痛点） |
