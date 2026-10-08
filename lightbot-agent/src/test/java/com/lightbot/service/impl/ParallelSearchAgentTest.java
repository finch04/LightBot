package com.lightbot.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.dto.ChatRequestDTO;
import com.lightbot.entity.Agent;
import com.lightbot.entity.McpServer;
import com.lightbot.entity.ModelProvider;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ModelProviderType;
import com.lightbot.event.CacheInvalidationBroadcaster;
import com.lightbot.model.ModelFactory;
import com.lightbot.model.OpenAIModelHandler;
import com.lightbot.service.*;
import com.lightbot.service.chat.*;
import com.lightbot.tool.registrar.BuiltinMcpRegistrar;
import com.lightbot.util.ModelProviderCacheUtil;
import com.lightbot.util.RedisUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实绑定、模型 HTTP 与原生工具循环；仅持久化边界和远端模型决策使用隔离夹具。 */
class ParallelSearchAgentTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SOURCE = "https://docs.spring.io/spring-boot/documentation.html";
    private static final String EXCERPT = "Spring Boot reference documentation maps the rest of the document.";

    /** 验证搜索结果选址、抓取内容反馈到下一轮模型，以及 HTTP 请求头。 */
    @Test
    void test_boundAgent_shouldFeedSearchAndFetchBackToModel() throws Exception {
        try (Harness h = new Harness(false)) {
            h.server.setStatus(CommonStatus.ACTIVE);
            assertEquals(EXCERPT, h.chat());
            h.assertFeedback();
            assertEquals(List.of("web_search", "web_fetch"), h.calls.stream()
                    .map(r -> r.path("params").path("name").asText()).toList());
            assertEquals(h.selectedUrl, h.calls.get(1).path("params").path("arguments").path("urls").get(0).asText());
            assertTrue(h.headers.stream().allMatch(v -> "LightBot/2.1.0".equals(v.get("User-Agent"))));
            assertTrue(h.headers.stream().allMatch(v -> !v.containsKey("Authorization") && !v.containsKey("x-api-key")));
            assertTrue(h.paths.stream().allMatch("/mcp"::equals));
        }
    }

    /** 未绑定或默认禁用时，模型不获得 MCP 工具且不连接 MCP。 */
    @Test
    void test_unboundOrDisabledAgent_shouldNotConnectOrAdvertiseParallel() throws Exception {
        for (boolean bound : List.of(false, true)) {
            try (Harness h = new Harness(false)) {
                h.server.setStatus(bound ? CommonStatus.DISABLED : CommonStatus.ACTIVE);
                when(h.agents.getMcpServerIds(7L)).thenReturn(bound ? List.of(42L) : List.of());
                assertEquals("No Parallel tools", h.chat());
                assertTrue(h.paths.isEmpty());
                assertTrue(h.context.getToolCallbackMap().isEmpty());
                assertTrue(h.context.getMcpToolNames().isEmpty());
            }
        }
    }

    /** 工具禁用配置经绑定加载生效，不将 web_fetch 发送给模型。 */
    @Test
    void test_disabledFetch_shouldBeAbsentFromBoundModelOptions() throws Exception {
        try (Harness h = new Harness(false)) {
            h.server.setStatus(CommonStatus.ACTIVE);
            h.server.setDisabledTools("[\"web_fetch\"]");
            assertEquals("No Parallel tools", h.chat());
            assertEquals(List.of("web_search"), new ArrayList<>(h.context.getToolCallbackMap().keySet()));
            assertTrue(h.calls.isEmpty());
            assertFalse(h.modelRequests.get(0).path("tools").toString().contains("web_fetch"));
        }
    }

    /** 同名内置工具经正常 ID 绑定优先保留；测试不修改或自动解除用户绑定。 */
    @Test
    void test_sameNameBinding_shouldKeepExistingSearchUntilExplicitUserChoice() throws Exception {
        try (Harness h = new Harness(false)) {
            h.server.setStatus(CommonStatus.ACTIVE);
            ToolCallback tavily = mock(ToolCallback.class);
            when(tavily.getToolDefinition()).thenReturn(ToolDefinition.builder().name("web_search")
                    .description("Existing Tavily binding").inputSchema("{\"type\":\"object\"}").build());
            when(h.agents.getToolIds(7L)).thenReturn(List.of(9L));
            when(h.tools.resolveToolCallbacksByIds(List.of(9L))).thenReturn(List.of(tavily));
            // Preparation uses precisely the same binding selection as chat().
            ChatContext ctx = h.newContext();
            h.prep.prepare(ctx);
            assertSame(tavily, ctx.getToolCallbackMap().get("web_search"));
            assertEquals(java.util.Set.of("web_fetch"), ctx.getMcpToolNames());
            verify(h.agents).getToolIds(7L);
            when(h.agents.getToolIds(7L)).thenReturn(List.of()); // explicit fixture user choice
            assertEquals(EXCERPT, h.chat());
            assertTrue(h.context.getMcpToolNames().contains("web_search"));
            verify(tavily, never()).call(anyString(), any());
        }
    }

    /** 匿名真实 Parallel 搜索与搜索选址抓取，决策来自本地受控模型而非付费推理。 */
    @Test
    @EnabledIfSystemProperty(named = "lightbot.parallel.live", matches = "true")
    void test_liveAnonymousBoundAgent_shouldReturnFetchedExcerptThroughModel() throws Exception {
        try (Harness h = new Harness(true)) {
            h.server.setStatus(CommonStatus.ACTIVE);
            String reply = h.chat();
            assertFalse(reply.isBlank());
            h.assertFeedback();
            assertEquals(h.fetchedExcerpt, reply);
            System.out.println("Native Agent search-selected URL: " + h.selectedUrl);
            System.out.println("Native Agent model response from fetched content: " + reply.substring(0, Math.min(400, reply.length())));
        }
    }

    /** 封装临时 HTTP 服务与隔离的记录存储；不模拟 MCP loader 或 ChatService 工具循环。 */
    private static class Harness implements AutoCloseable {
        final HttpServer fixture;
        final McpServerService servers = mock(McpServerService.class);
        final AgentService agents = mock(AgentService.class);
        final ToolService tools = mock(ToolService.class);
        final List<JsonNode> modelRequests = new CopyOnWriteArrayList<>();
        final List<JsonNode> calls = new CopyOnWriteArrayList<>();
        final List<Map<String, String>> headers = new CopyOnWriteArrayList<>();
        final List<String> paths = new CopyOnWriteArrayList<>();
        final McpServer server;
        final McpClientServiceImpl mcp;
        final ToolPrepMiddleware prep;
        final ChatServiceImpl service;
        final String session = UUID.randomUUID().toString();
        ChatContext context;
        String selectedUrl;
        String fetchedExcerpt;

        /**
         * 创建真实原生路径，提供商通过 ModelFactory/OpenAIModelHandler 访问本地 HTTP。
         * @param live 是否连接真实匿名 Parallel 端点
         * @throws Exception 夹具初始化失败
         */
        Harness(boolean live) throws Exception {
            fixture = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            fixture.createContext("/mcp", this::mcpResponse);
            fixture.createContext("/v1/chat/completions", this::modelResponse);
            fixture.start();
            String host = "http://127.0.0.1:" + fixture.getAddress().getPort();
            new BuiltinMcpRegistrar(servers, JSON).registerBuiltInMcpServers();
            ArgumentCaptor<McpServer> saved = ArgumentCaptor.forClass(McpServer.class);
            verify(servers, times(3)).save(saved.capture());
            server = saved.getAllValues().stream().filter(s -> "parallel-search".equals(s.getName())).findFirst().orElseThrow();
            assertEquals(CommonStatus.DISABLED, server.getStatus());
            assertEquals("{\"User-Agent\":\"LightBot/2.1.0\"}", server.getHeaders());
            server.setId(42L);
            if (!live) server.setHost(host + "/mcp");
            when(servers.getById(42L)).thenReturn(server);
            mcp = new McpClientServiceImpl(servers, mock(RedisUtil.class), JSON, mock(CacheInvalidationBroadcaster.class));
            ModelProvider provider = new ModelProvider();
            provider.setId(1L);
            provider.setType(ModelProviderType.OPENAI);
            provider.setBaseUrl(host);
            provider.setApiKey("local-fixture-only");
            provider.setConfig("{\"modelId\":\"controlled-fixture\"}");
            ModelProviderService providers = mock(ModelProviderService.class);
            when(providers.getById(1L)).thenReturn(provider);
            ModelFactory factory = new ModelFactory(List.of(new OpenAIModelHandler(JSON)), providers,
                    mock(ModelProviderCacheUtil.class), mock(SystemConfigService.class), JSON, mock(CacheInvalidationBroadcaster.class));
            factory.init();
            when(agents.getMcpServerIds(7L)).thenReturn(List.of(42L));
            prep = construct(ToolPrepMiddleware.class, Map.of(ModelFactory.class, factory, AgentService.class, agents,
                    ToolService.class, tools, McpClientService.class, mcp, McpServerService.class, servers,
                    ModelProviderService.class, providers));
            Executor executor = Runnable::run;
            ReflectionTestUtils.setField(prep, "lightBotExecutor", executor);
            InitMiddleware init = mock(InitMiddleware.class);
            doAnswer(invocation -> { context = invocation.getArgument(0); configure(context); return null; }).when(init).init(any());
            MessageMiddleware messages = mock(MessageMiddleware.class);
            doAnswer(invocation -> { ChatContext ctx = invocation.getArgument(0);
                ctx.setMessages(new ArrayList<>(List.of(new org.springframework.ai.chat.messages.UserMessage(ctx.getRequest().getMessage()))));
                return null; }).when(messages).prepare(any());
            service = construct(ChatServiceImpl.class, Map.of(InitMiddleware.class, init, MessageMiddleware.class, messages,
                    ToolPrepMiddleware.class, prep, ObjectMapper.class, JSON, ToolEventGenerator.class, new ToolEventGenerator(JSON)));
            ReflectionTestUtils.setField(service, "lightBotExecutor", executor);
        }

        /** @return 从公开聊天入口执行的回复 */
        String chat() {
            ChatRequestDTO request = new ChatRequestDTO();
            request.setMessage("Find official Spring Boot documentation and fetch a source before answering.");
            return service.chat(request);
        }

        /** @return 隔离持久化的 Agent 上下文 */
        ChatContext newContext() { ChatContext ctx = ChatContext.of(new ChatRequestDTO()); configure(ctx); return ctx; }

        /** @param ctx 设置 Agent 与模型选择，绑定仍由 AgentService 正常加载 */
        void configure(ChatContext ctx) {
            Agent agent = new Agent(); agent.setId(7L);
            ctx.setAgent(agent); ctx.setUserId(5L); ctx.setSessionId(11L); ctx.setProviderId(1L);
            ctx.setConfigMap(Map.of("modelId", "controlled-fixture", "modelRetryTimes", 0));
        }

        /** @param exchange 模型 HTTP 请求；决策只使用请求中实际收到的工具结果 */
        void modelResponse(HttpExchange exchange) throws java.io.IOException {
            JsonNode request = JSON.readTree(exchange.getRequestBody());
            modelRequests.add(request);
            List<JsonNode> results = new ArrayList<>();
            request.path("messages").forEach(m -> { if ("tool".equals(m.path("role").asText())) results.add(m); });
            Map<String, Object> message;
            String finish = "stop";
            if (results.isEmpty() && (!request.path("tools").toString().contains("web_search") || !request.path("tools").toString().contains("web_fetch"))) {
                message = Map.of("role", "assistant", "content", "No Parallel tools");
            } else if (results.isEmpty()) {
                message = toolMessage("search", "web_search", Map.of("objective", "Find official Spring Boot documentation",
                        "search_queries", List.of("Spring Boot official documentation"), "session_id", session)); finish = "tool_calls";
            } else {
                JsonNode content = JSON.readTree(results.get(results.size() - 1).path("content").asText());
                JsonNode result = JSON.readTree(content.get(0).path("text").asText()).path("results").get(0);
                if (results.size() == 1) {
                    selectedUrl = result.path("url").asText();
                    message = toolMessage("fetch", "web_fetch", Map.of("urls", List.of(selectedUrl), "objective", "Summarize this documentation", "session_id", session)); finish = "tool_calls";
                } else {
                    fetchedExcerpt = result.path("excerpts").get(0).asText().lines()
                            .filter(line -> !line.isBlank()).findFirst().orElseThrow();
                    message = Map.of("role", "assistant", "content", fetchedExcerpt);
                }
            }
            respond(exchange, Map.of("id", "fixture-" + modelRequests.size(), "object", "chat.completion", "created", 1,
                    "model", "controlled-fixture", "choices", List.of(Map.of("index", 0, "message", message, "finish_reason", finish))));
        }

        /** @param exchange 本地 MCP 协议夹具请求 */
        void mcpResponse(HttpExchange exchange) throws java.io.IOException {
            paths.add(exchange.getRequestURI().getPath());
            Map<String, String> observed = new java.util.HashMap<>();
            for (String name : List.of("User-Agent", "Authorization", "x-api-key")) {
                String value = exchange.getRequestHeaders().getFirst(name); if (value != null) observed.put(name, value);
            }
            headers.add(observed);
            if (!"POST".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405, -1); exchange.close(); return; }
            JsonNode request = JSON.readTree(exchange.getRequestBody());
            if (!request.has("id")) { exchange.sendResponseHeaders(202, -1); exchange.close(); return; }
            Object result = switch (request.path("method").asText()) {
                case "initialize" -> Map.of("protocolVersion", "2025-03-26", "capabilities", Map.of("tools", Map.of("listChanged", false)), "serverInfo", Map.of("name", "fixture", "version", "1"));
                case "tools/list" -> Map.of("tools", List.of(schema("web_search"), schema("web_fetch")));
                case "tools/call" -> {
                    calls.add(request);
                    String excerpt = "web_fetch".equals(request.path("params").path("name").asText()) ? EXCERPT : "Search summary only";
                    String text = JSON.writeValueAsString(Map.of("results", List.of(Map.of("url", SOURCE, "excerpts", List.of(excerpt)))));
                    yield Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", false);
                }
                default -> Map.of();
            };
            respond(exchange, Map.of("jsonrpc", "2.0", "id", request.get("id"), "result", result));
        }

        /** 校验三次真实模型 HTTP 请求里的关联 ID、选址与抓取内容。 */
        void assertFeedback() throws Exception {
            assertEquals(3, modelRequests.size());
            assertTrue(selectedUrl.startsWith("https://"));
            assertTrue(selectedUrl.contains("spring.io"));
            assertFalse(fetchedExcerpt.isBlank());
            assertEquals("search", modelRequests.get(1).path("messages").get(2).path("tool_call_id").asText());
            assertEquals("fetch", modelRequests.get(2).path("messages").get(4).path("tool_call_id").asText());
            assertEquals(selectedUrl, JSON.readTree(modelRequests.get(2).path("messages").get(3).path("tool_calls").get(0)
                    .path("function").path("arguments").asText()).path("urls").get(0).asText());
            assertEquals(2, context.getPendingToolCalls().size());
            assertTrue(context.getPendingToolCalls().stream().allMatch(c -> "success".equals(c.getStatus())));
        }

        /** 释放真实客户端及本地 HTTP 服务。 */
        public void close() { mcp.closeAll(); fixture.stop(0); }
    }

    /** @param name 工具名称 @return 本地协议 Schema */
    private static Map<String, Object> schema(String name) { return Map.of("name", name, "description", name,
            "inputSchema", Map.of("type", "object", "properties", Map.of())); }

    /** @param id 调用 ID @param name 工具名 @param arguments 参数 @return 受控模型的工具调用响应 */
    private static Map<String, Object> toolMessage(String id, String name, Map<String, Object> arguments) throws java.io.IOException {
        return Map.of("role", "assistant", "content", "", "tool_calls", List.of(Map.of("id", id, "type", "function",
                "function", Map.of("name", name, "arguments", JSON.writeValueAsString(arguments)))));
    }

    /** @param exchange 请求 @param body JSON 响应 */
    private static void respond(HttpExchange exchange, Object body) throws java.io.IOException {
        byte[] bytes = JSON.writeValueAsBytes(body); exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
    }

    /** @param type 原生类 @param real 实际依赖 @param <T> 原生类型 @return 仅外围依赖被模拟的实例 @throws Exception 构造失败 */
    private static <T> T construct(Class<T> type, Map<Class<?>, Object> real) throws Exception {
        var ctor = type.getConstructors()[0];
        Object[] args = java.util.Arrays.stream(ctor.getParameterTypes()).map(t -> real.containsKey(t) ? real.get(t) : mock(t)).toArray();
        return type.cast(ctor.newInstance(args));
    }
}
