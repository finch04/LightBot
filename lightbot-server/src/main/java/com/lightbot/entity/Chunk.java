package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分块表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("chunk")
public class Chunk {

    /** 主键ID，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属文档ID */
    private Long documentId;

    /** 所属知识库ID，冗余字段便于查询 */
    private Long knowledgeId;

    /** 分块文本内容 */
    private String content;

    /** 在文档中的分块序号 */
    private Integer chunkIndex;

    /** Token数量 */
    private Integer tokenCount;

    /** 分块元数据(JSON)，如页码、章节标题 */
    private String metadata;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
