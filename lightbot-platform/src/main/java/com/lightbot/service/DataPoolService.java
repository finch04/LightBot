package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.vo.DataPoolImportResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 数据池动态表数据服务
 *
 * @author finch
 * @since 2026-07-26
 */
public interface DataPoolService {

    Page<Map<String, Object>> page(Long modelId, int pageNum, int pageSize,
                                   String keyword, Map<String, Object> filters);

    Map<String, Object> get(Long modelId, Long recordId);

    Map<String, Object> create(Long modelId, Map<String, Object> data);

    List<Map<String, Object>> batchCreate(Long modelId, List<Map<String, Object>> records);

    Map<String, Object> update(Long modelId, Long recordId, Map<String, Object> data);

    void delete(Long modelId, Long recordId);

    int batchDelete(Long modelId, List<Long> ids);

    /**
     * 同步导入 CSV/JSON；mode=append|replace。
     * 逐行处理并返回统计信息与带「成功/失败」「失败原因」列的结果 Excel。
     */
    DataPoolImportResultVO importData(Long modelId, MultipartFile file, String mode);

    /**
     * 导出全部（按当前筛选）为 JSON 字节
     */
    byte[] exportJson(Long modelId, String keyword, Map<String, Object> filters);

    /**
     * 导出 CSV
     */
    byte[] exportCsv(Long modelId, String keyword, Map<String, Object> filters);

    /**
     * 上传数据池附件，返回 { name, url, size, path }
     */
    Map<String, Object> uploadAttachment(MultipartFile file);
}
