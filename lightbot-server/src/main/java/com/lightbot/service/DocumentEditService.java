package com.lightbot.service;

import com.lightbot.dto.DocumentEditRequest;
import com.lightbot.dto.DocumentEditSaveVO;
import com.lightbot.dto.EditableContentVO;

/**
 * 文档在线编辑服务接口
 *
 * @author finch
 * @since 2026-06-16
 */
public interface DocumentEditService {

    /**
     * 获取文档可编辑内容
     *
     * @param documentId 文档ID
     * @return 可编辑内容
     */
    EditableContentVO getEditableContent(Long documentId);

    /**
     * 保存编辑内容并触发全量重建
     *
     * @param documentId 文档ID
     * @param request    编辑请求
     * @return 保存结果
     */
    DocumentEditSaveVO saveContent(Long documentId, DocumentEditRequest request);
}
