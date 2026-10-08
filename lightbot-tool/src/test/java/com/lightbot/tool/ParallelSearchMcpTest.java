package com.lightbot.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.McpServer;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.McpTransportType;
import com.lightbot.service.McpServerService;
import com.lightbot.tool.registrar.BuiltinMcpRegistrar;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 内置注册默认值与已有记录保护；Agent 原生调用回归见 ParallelSearchAgentTest。 */
class ParallelSearchMcpTest {
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 从真实注册器取得配置，同时校验匿名默认值。
     * @return 已注册、尚未启用的 Parallel 记录
     * @throws Exception 请求头 JSON 无法解析时抛出
     */
    private McpServer registeredParallel() throws Exception {
        McpServerService store = mock(McpServerService.class);
        new BuiltinMcpRegistrar(store, mapper).registerBuiltInMcpServers();
        ArgumentCaptor<McpServer> saved = ArgumentCaptor.forClass(McpServer.class);
        verify(store, times(3)).save(saved.capture());
        List<McpServer> servers = saved.getAllValues();
        assertTrue(servers.stream().allMatch(s -> s.getStatus() == CommonStatus.DISABLED));
        assertNull(servers.get(0).getHeaders());
        assertEquals("https://remote.mcpservers.org/sequentialthinking/mcp", servers.get(0).getHost());
        assertNull(servers.get(1).getHeaders());
        assertEquals("@antv/mcp-server-chart", mapper.readTree(servers.get(1).getDeployConfig()).get("packageName").asText());
        McpServer parallel = servers.stream().filter(s -> "parallel-search".equals(s.getName())).findFirst().orElseThrow();
        assertEquals("https://search.parallel.ai/mcp", parallel.getHost());
        assertEquals(McpTransportType.STREAMABLE_HTTP, parallel.getTransport());
        assertEquals(Map.of("User-Agent", "LightBot/2.1.0"), mapper.readValue(parallel.getHeaders(), Map.class));
        assertEquals(1, parallel.getIsBuiltin());
        parallel.setId(42L);
        return parallel;
    }

    /** 验证内置注册不会自动启用或要求鉴权。 */
    @Test
    void test_registerParallel_shouldBeDisabledAndAnonymous() throws Exception {
        registeredParallel();
    }

    /** 验证已有记录不会被内置默认配置覆盖。 */
    @Test
    void test_repeatRegistration_shouldPreserveSavedConfiguration() {
        McpServerService store = mock(McpServerService.class);
        McpServer existing = new McpServer();
        existing.setIsBuiltin(1);
        existing.setStatus(CommonStatus.ACTIVE);
        existing.setHost("https://custom.example/mcp");
        existing.setHeaders("{\"User-Agent\":\"custom\"}");
        existing.setDisabledTools("[\"web_fetch\"]");
        when(store.getOne(any())).thenReturn(existing);
        new BuiltinMcpRegistrar(store, mapper).registerBuiltInMcpServers();
        verify(store, never()).save(any());
        verify(store, never()).updateById(any(McpServer.class));
        assertEquals(CommonStatus.ACTIVE, existing.getStatus());
        assertEquals("https://custom.example/mcp", existing.getHost());
        assertEquals("{\"User-Agent\":\"custom\"}", existing.getHeaders());
        assertEquals("[\"web_fetch\"]", existing.getDisabledTools());
    }

}
