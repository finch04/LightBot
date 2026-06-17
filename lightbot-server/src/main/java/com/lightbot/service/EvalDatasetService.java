package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.EvalDatasetExampleVO;
import com.lightbot.entity.EvalDataset;

import java.util.List;

/**
 * 评测数据集服务接口
 *
 * @author finch
 * @since 2026-05-27
 */
public interface EvalDatasetService extends IService<EvalDataset> {

    /**
     * 创建评测数据集
     *
     * @param name          数据集名称
     * @param description   描述
     * @param columnsConfig 列配置（JSON）
     * @param userId        创建者ID
     * @return 数据集实体
     */
    EvalDataset create(String name, String description, String columnsConfig, Long userId);

    /**
     * 更新评测数据集
     *
     * @param id            主键ID
     * @param name          数据集名称
     * @param description   描述
     * @param columnsConfig 列配置（JSON）
     */
    void update(Long id, String name, String description, String columnsConfig);

    /**
     * 删除评测数据集（逻辑删除）
     *
     * @param id 主键ID
     */
    void deleteById(Long id);

    /**
     * 分页查询评测数据集列表
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @param keyword  搜索关键词
     * @param userId   用户ID
     * @return 分页结果
     */
    Page<EvalDataset> list(int pageNum, int pageSize, String keyword, Long userId);

    /**
     * 获取示例评测集列表
     *
     * @return 示例列表
     */
    List<EvalDatasetExampleVO> listExamples();

    /**
     * 从示例模板创建评测集（含示例数据项）
     *
     * @param key    示例标识
     * @param userId 创建者ID
     * @return 创建的数据集
     */
    EvalDataset createFromExample(String key, Long userId);
}
