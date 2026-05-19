package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ModelProviderType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型提供商表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("model_provider")
public class ModelProvider {

    /** 主键ID，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 提供商名称，如 "OpenAI"、"通义千问" */
    private String name;

    /** 类型: openai/dashscope/deepseek/ollama */
    private ModelProviderType type;

    /** API密钥，加密存储 */
    private String apiKey;

    /** API基础地址 */
    private String baseUrl;

    /** 扩展配置(JSON) */
    private String config;

    /** 状态: active-启用, disabled-禁用 */
    private CommonStatus status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除: 0-未删除 1-已删除 */
    @TableLogic
    private Integer deleted;
}
