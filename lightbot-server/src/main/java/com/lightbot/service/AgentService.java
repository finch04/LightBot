package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.Agent;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Agent服务接口
 *
 * @author finch
 * @since 2026-05-19
 */
public interface AgentService extends IService<Agent> {

    /**
     * 创建Agent
     *
     * @param agent Agent信息
     * @return Agent
     */
    Agent create(Agent agent);

    /**
     * 更新Agent
     *
     * @param agent Agent信息
     * @return Agent
     */
    Agent update(Agent agent);

    /**
     * 分页查询当前用户的Agent列表
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    Page<Agent> listMyAgents(int pageNum, int pageSize);

    /**
     * 获取 Agent 详情（包含绑定的知识库 ID 列表）
     *
     * @param id Agent ID
     * @return Agent 信息和绑定的知识库 ID 列表
     */
    Map<String, Object> getAgentDetail(Long id);

    /**
     * 删除Agent（逻辑删除）
     *
     * @param id 主键ID
     */
    void deleteById(Long id);

    /**
     * AI生成系统提示词
     *
     * @param id Agent ID
     * @return 生成的系统提示词
     */
    String generateSystemPrompt(Long id);

    /**
     * AI生成推荐问题
     *
     * @param id Agent ID
     * @return 推荐问题列表（JSON数组字符串）
     */
    String generateRecommendedQuestions(Long id);

    /**
     * 上传Agent头像到MinIO，返回头像URL
     *
     * @param id   Agent ID
     * @param file 头像文件
     * @return 头像访问URL
     */
    String uploadAvatar(Long id, MultipartFile file);
}
