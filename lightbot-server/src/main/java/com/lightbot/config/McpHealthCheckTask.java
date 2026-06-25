package com.lightbot.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lightbot.entity.McpServer;
import com.lightbot.enums.CommonStatus;
import com.lightbot.service.McpClientService;
import com.lightbot.service.McpServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP Server 定时健康检查任务
 * <p>每 3 小时刷新所有活跃 MCP Server 的工具缓存，更新 lastSyncTime</p>
 *
 * @author finch
 * @since 2026-06-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpHealthCheckTask {

    private final McpServerService mcpServerService;
    private final McpClientService mcpClientService;

    /**
     * 每 3 小时执行一次：刷新所有活跃 MCP Server 的工具缓存
     */
    @Scheduled(fixedRate = 3 * 60 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void refreshAllActiveServers() {
        List<McpServer> servers = mcpServerService.list(
                new LambdaQueryWrapper<McpServer>()
                        .eq(McpServer::getStatus, CommonStatus.ACTIVE)
                        .eq(McpServer::getDeleted, 0));

        if (servers.isEmpty()) {
            return;
        }

        log.info("[MCP定时刷新] 开始刷新 {} 个活跃 MCP Server", servers.size());
        int success = 0;
        int fail = 0;

        for (McpServer server : servers) {
            int toolCount = mcpClientService.refreshServer(server.getId());
            if (toolCount >= 0) {
                server.setLastSyncTime(LocalDateTime.now());
                mcpServerService.updateById(server);
                success++;
            } else {
                fail++;
            }
        }

        log.info("[MCP定时刷新] 完成: 成功={}, 失败={}", success, fail);
    }
}
