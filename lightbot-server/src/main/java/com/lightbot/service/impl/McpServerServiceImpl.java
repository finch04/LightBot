package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.dto.McpServerRequest;
import com.lightbot.entity.McpServer;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ErrorCode;
import org.springframework.util.StringUtils;
import com.lightbot.mapper.McpServerMapper;
import com.lightbot.service.McpServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * MCP Server 服务实现类
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
@Service
public class McpServerServiceImpl extends ServiceImpl<McpServerMapper, McpServer>
        implements McpServerService {

    @Override
    public McpServer create(McpServerRequest request) {
        // 1. 构建实体并保存
        McpServer server = new McpServer();
        server.setName(request.getName());
        server.setDescription(request.getDescription());
        server.setInstallType(request.getInstallType());
        server.setDeployConfig(request.getDeployConfig());
        server.setDetailConfig(request.getDetailConfig());
        server.setHost(request.getHost());
        server.setStatus(CommonStatus.ACTIVE);
        save(server);
        return server;
    }

    @Override
    public McpServer update(McpServerRequest request) {
        // 1. 校验存在性
        McpServer server = getById(request.getId());
        if (server == null) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }
        // 2. 更新字段
        server.setName(request.getName());
        server.setDescription(request.getDescription());
        server.setInstallType(request.getInstallType());
        server.setDeployConfig(request.getDeployConfig());
        server.setDetailConfig(request.getDetailConfig());
        server.setHost(request.getHost());
        updateById(server);
        return server;
    }

    @Override
    public Page<McpServer> listPage(int pageNum, int pageSize, String name) {
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<McpServer>()
                        .like(StringUtils.hasText(name), McpServer::getName, name)
                        .orderByDesc(McpServer::getCreateTime));
    }

    @Override
    public void deleteById(Long id) {
        if (!removeById(id)) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }
    }
}
