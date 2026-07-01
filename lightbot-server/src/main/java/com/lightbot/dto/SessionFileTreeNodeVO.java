package com.lightbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话文件树节点（参考 Yuxi thread files tree）
 */
@Data
@Schema(description = "会话文件树节点")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionFileTreeNodeVO {

    @Schema(description = "节点名称")
    private String name;

    @Schema(description = "相对会话根的路径")
    private String path;

    @Schema(description = "MinIO objectKey")
    private String objectKey;

    @Schema(description = "是否为目录")
    private Boolean directory;

    @Schema(description = "文件大小（字节）")
    private Long size;

    @Schema(description = "MIME 类型")
    private String mimeType;

    @Schema(description = "预览 URL")
    private String previewUrl;

    @Schema(description = "附件来源")
    private String source;

    @Schema(description = "子节点")
    private List<SessionFileTreeNodeVO> children = new ArrayList<>();
}
