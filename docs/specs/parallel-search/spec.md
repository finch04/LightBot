# SPEC-202610-001：内置 Parallel Search MCP（轻量 SDD）

状态：待评审

## 目标与范围

通过已有内置 MCP 注册器提供可选的 Parallel 网页搜索与抓取服务。
生产代码增加 lightbot-tool 注册定义，并修复原生非流式对话的可空 SSE 容器检查；lightbot-agent 增加原生对话链路验证，不改变模块边界、数据库结构、接口或 Agent 默认选择。
不增加付费 REST/SDK 接口，不新增模型或自动启用服务。

## 设计

- 注册名 `parallel-search`，地址 `https://search.parallel.ai/mcp`，传输为 Streamable HTTP。
- 新记录保持 `DISABLED`；启动注册不连接远端。已有同名记录不覆盖配置。
- 内置定义增加可选 HTTP 请求头，Parallel 使用 `User-Agent: LightBot/2.1.0`。
- 复用 McpClientService 的工具发现、SyncMcpToolCallback、30 秒请求超时和缓存清理。
- 原生非流式 chat 调用工具事件记录器时没有 SSE 收集容器；追加 skillFlux 前检查容器非空，保留事件记录与流式行为。
- 匿名端点无需 Parallel API Key；免费额度有速率限制，模型推理费用独立。

## 验收标准

- AC-1：缺失时注册禁用的 Parallel 记录，写入正确 URL、传输和 User-Agent；其他内置记录不变。
- AC-2：重复启动保留已有同名记录的状态、地址、请求头和禁用工具配置。
- AC-3：通过 McpClientService 加载注册配置，匿名发现工具并用回调执行 web_search / web_fetch，得到有效内容。
- AC-4：请求包含配置的 User-Agent；禁用状态和禁用工具仍生效。
- AC-5：绑定 Agent 后，经 ToolPrepMiddleware → ChatServiceImpl.chat → ModelFactory/OpenAIModelHandler 的原生工具循环执行搜索；模型从搜索结果选择 URL 抓取，抓取内容进入后续模型请求与最终回复。
- AC-6：未绑定、禁用 Server、禁用工具与同名 web_search 绑定的选择行为有原生路径回归；不自动替换已有工具。

## 任务与验证

1. 为内置定义增加可选 headers，添加 Parallel 定义。
2. 增加注册与实际客户端调用回归测试，以及显式启用的匿名远端测试。
3. 在干净 Maven 环境运行测试并记录匿名工具返回的具体结果。
4. 按下方用户步骤验证；保持启动注册无网络请求。

## 使用

启动服务后，在「扩展 / MCP」中找到 `parallel-search`，启用并测试连接，
刷新工具列表。使用已有 MCP 工具选择界面选择 `web_search` 或 `web_fetch`。
若绑定到 Agent，在该 Agent 的 MCP 配置中显式绑定 `parallel-search`。
若已有内置 Tavily `web_search` 绑定，现有去重规则保留先加载的内置搜索，
Parallel 的 `web_fetch` 仍可用。需要 Parallel 搜索时，用户可显式移除该 Agent 的
Tavily 工具绑定；不需要时保留现状或不绑定 Parallel。本集成不会自动解除或替换绑定。
也可在 Workflow 的 MCP 节点中指定 `parallel-search` 与对应工具名称。
本次不改变已有工具优先级。
请求头预设为 `{"User-Agent":"LightBot/2.1.0"}`，不要添加 Authorization 或 API Key。
服务使用匿名免费额度，限流时稍后重试；工具说明与额度以
[官方文档](https://docs.parallel.ai/integrations/mcp/search-mcp)为准。

自动回归测试：`mvn -pl lightbot-tool -am test -Dtest=ParallelSearchMcpTest -Dsurefire.failIfNoSpecifiedTests=false`。
原生 Agent 回归：`mvn -pl lightbot-agent -am test -Dtest=ParallelSearchAgentTest -Dsurefire.failIfNoSpecifiedTests=false`。
显式匿名远端验证：同一命令增加 `-Dlightbot.parallel.live=true`；会向 Parallel 发送搜索与网页抓取请求。

Agent 测试从公开 `ChatServiceImpl.chat` 进入，使用真实 ToolPrepMiddleware、
McpClientServiceImpl、ModelFactory、OpenAIModelHandler 和原生工具/结果循环。
数据库与 Redis 使用内存边界夹具，初始化与消息持久化中间件隔离；模型提供商是
本地受控 OpenAI 兼容 HTTP 夹具，不进行付费模型推理，也不证明模型自主决策质量。
夹具仅根据实际收到的搜索结果选择 URL，并从实际收到的抓取结果生成下一轮回复。
普通测试使用本地 MCP 协议夹具；显式远端测试使用真实匿名 Parallel MCP。
本地 HTTP 请求验证 User-Agent、路径及无鉴权；远端请求头需结合 JDK HTTP 请求日志验证。

完整验证：仓库根目录 `mvn -fae test`；前端 `pnpm install --frozen-lockfile` 后运行
`pnpm test`、`pnpm lint:check`、`pnpm format:check` 与 `pnpm build`。
完整应用启动依赖 PostgreSQL/pgvector、Redis 和模型配置，步骤见 [QUICKSTART](../../../QUICKSTART.md)。
隔离测试不替代浏览器、真实持久化或真实推理的完整手动验收。

## 回滚

回退新增注册定义、可选请求头字段和测试；如已创建数据库记录，管理员可禁用或删除该条目。
