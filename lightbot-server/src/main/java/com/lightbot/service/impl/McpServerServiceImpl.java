package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.config.RedisCacheConfig;
import com.lightbot.dto.McpServerRequest;
import com.lightbot.entity.McpServer;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.McpServerMapper;
import com.lightbot.service.McpClientService;
import com.lightbot.service.McpServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.Serializable;

/**
 * MCP Server 服务实现类
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerServiceImpl extends ServiceImpl<McpServerMapper, McpServer>
        implements McpServerService {

    @Lazy
    @Autowired
    private McpClientService mcpClientService;

    @Override
    @Cacheable(value = RedisCacheConfig.CACHE_MCP_SERVER, key = "#id")
    public McpServer getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_MCP_SERVER, key = "#entity.id")
    public boolean updateById(McpServer entity) {
        return super.updateById(entity);
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_MCP_SERVER, allEntries = true)
    public McpServer create(McpServerRequest request) {
        // 1. 校验名称唯一性
        long count = count(new LambdaQueryWrapper<McpServer>().eq(McpServer::getName, request.getName()));
        if (count > 0) {
            throw new BizException(ErrorCode.MCP_SERVER_NAME_EXISTS);
        }

        // 2. 构建实体并保存
        McpServer server = new McpServer();
        server.setName(request.getName());
        server.setDescription(request.getDescription());
        server.setInstallType(request.getInstallType());
        server.setDeployConfig(request.getDeployConfig());
        server.setDetailConfig(request.getDetailConfig());
        server.setHost(request.getHost());
        server.setTransport(request.getTransport());
        server.setHeaders(request.getHeaders());
        server.setDisabledTools(request.getDisabledTools());
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
        // 2. 名称变更时校验唯一性
        if (!server.getName().equals(request.getName())) {
            long count = count(new LambdaQueryWrapper<McpServer>().eq(McpServer::getName, request.getName()));
            if (count > 0) {
                throw new BizException(ErrorCode.MCP_SERVER_NAME_EXISTS);
            }
        }

        // 3. 更新字段
        server.setName(request.getName());
        server.setDescription(request.getDescription());
        server.setInstallType(request.getInstallType());
        server.setDeployConfig(request.getDeployConfig());
        server.setDetailConfig(request.getDetailConfig());
        server.setHost(request.getHost());
        server.setTransport(request.getTransport());
        server.setHeaders(request.getHeaders());
        server.setDisabledTools(request.getDisabledTools());
        updateById(server);
        // 配置变更后清除 MCP 客户端缓存，下次对话重新拉取工具
        mcpClientService.clearCache(server.getId());
        return server;
    }

    @Override
    public Page<McpServer> listPage(int pageNum, int pageSize, String name) {
        return baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<McpServer>()
                        .like(StringUtils.hasText(name), McpServer::getName, name)
                        .orderByDesc(McpServer::getCreateTime));
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_MCP_SERVER, key = "#id")
    public void deleteById(Long id) {
        if (!removeById(id)) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }
        mcpClientService.clearCache(id);
    }
}
