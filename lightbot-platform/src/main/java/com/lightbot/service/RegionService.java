package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.Region;
import com.lightbot.vo.RegionVO;

import java.util.List;
import java.util.Map;

/**
 * 行政区划地区库
 *
 * @author finch
 * @since 2026-08-05
 */
public interface RegionService extends IService<Region> {

    /**
     * 按编码查询（支持 6 位国标码兼容缩短为库内 code）
     *
     * @param code 区划编码
     * @return 地区；不存在则 null
     */
    Region findByCode(String code);

    /**
     * 本级及全部下级区划编码（含自身），用于问数 subtree 隔离
     *
     * @param code callerContext.regionId
     * @return 编码列表；地区不存在则空列表
     */
    List<String> listSelfAndDescendantCodes(String code);

    /**
     * 关键词搜索（名称/编码）
     *
     * @param keyword 关键词
     * @param limit   上限
     * @return 列表
     */
    List<RegionVO> search(String keyword, int limit);

    /**
     * 懒加载子节点；parentCode 为空则返回省级
     *
     * @param parentCode 上级编码
     * @return 子节点
     */
    List<RegionVO> listChildren(String parentCode);

    /**
     * 地区库条数
     *
     * @return 数量
     */
    long countActive();

    /**
     * 按层级统计：count / provinces / cities / districts
     *
     * @return 统计 Map
     */
    Map<String, Long> statsBreakdown();

    /**
     * 从根到当前节点的路径（含自身）
     *
     * @param code 区划编码
     * @return 路径；不存在则空列表
     */
    List<RegionVO> listPath(String code);

    /**
     * 若库为空则从 classpath 种子导入国标省市区
     *
     * @return 导入条数；已有数据则 0
     */
    int seedIfEmpty();

    /**
     * 强制重新导入国标省市区（清空后写入）
     *
     * @return 导入条数
     */
    int reseed();
}
