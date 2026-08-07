package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.Region;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 行政区划 Mapper
 *
 * @author finch
 * @since 2026-08-05
 */
@Mapper
public interface RegionMapper extends BaseMapper<Region> {

    /**
     * 查询本级及所有下级区划编码（含自身），按 code / parent_code 递归
     *
     * @param code 根节点区划编码（已规范化）
     * @return 区划编码列表
     */
    @Select("""
            WITH RECURSIVE subtree AS (
                SELECT code FROM region
                WHERE deleted = 0 AND code = #{code}
                UNION ALL
                SELECT r.code FROM region r
                INNER JOIN subtree s ON r.parent_code = s.code
                WHERE r.deleted = 0
            )
            SELECT code FROM subtree ORDER BY code
            """)
    List<String> selectSelfAndDescendantCodes(@Param("code") String code);

    /**
     * 物理清空（重新导入种子用，绕过逻辑删除唯一约束）
     *
     * @return 影响行数
     */
    @Delete("DELETE FROM region")
    int hardDeleteAll();
}
