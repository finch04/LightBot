package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lightbot.enums.AuthType;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ToolType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Tool表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("tool")
public class Tool {

    /** 主键ID，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 创建者ID，系统内置为NULL */
    private Long userId;

    /** Tool唯一标识，如 "http_request" */
    private String name;

    /** 显示名称 */
    private String displayName;

    /** Tool描述，供Agent理解 */
    private String description;

    /** 类型: builtin-内置, custom-自定义, api-API调用, mcp-MCP协议 */
    private ToolType toolType;

    /** 输入参数Schema(JSON) */
    private String inputSchema;

    /** 输出参数Schema(JSON) */
    private String outputSchema;

    /** 扩展配置(JSON) */
    private String config;

    /** API端点地址 */
    private String endpointUrl;

    /** 认证类型: none/api_key/oauth/bearer */
    private AuthType authType;

    /** 认证配置(JSON) */
    private String authConfig;

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
