package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lightbot.enums.DocumentStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("document")
public class Document {

    /** 主键ID，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属知识库ID */
    private Long knowledgeId;

    /** 上传者ID */
    private Long userId;

    /** 文档名称 */
    private String name;

    /** 文件存储路径 */
    private String filePath;

    /** 文件类型: pdf/txt/md/docx */
    private String fileType;

    /** 文件大小(字节) */
    private Long fileSize;

    /** 文件哈希，用于去重 */
    private String fileHash;

    /** 分块数量 */
    private Integer chunkCount;

    /** Token数量 */
    private Long tokenCount;

    /** 状态: pending-待处理, processing-处理中, completed-已完成, failed-失败 */
    private DocumentStatus status;

    /** 处理错误信息 */
    private String errorMessage;

    /** 文档元数据(JSON) */
    private String metadata;

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
