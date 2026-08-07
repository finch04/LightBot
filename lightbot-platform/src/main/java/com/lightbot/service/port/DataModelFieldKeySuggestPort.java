package com.lightbot.service.port;

import java.util.List;

/**
 * 根据中文显示名补全数据模型字段英文名（数据库列标识），由 AI 模块实现。
 *
 * @author finch
 * @since 2026-07-28
 */
public interface DataModelFieldKeySuggestPort {

    /**
     * 为字段中文名生成合法英文标识（snake_case 优先），与 names 一一对应。
     *
     * @param names        待补全的中文显示名（顺序保留）
     * @param occupiedKeys 已占用的英文名（不可与之冲突）
     * @return 与 names 等长的英文名列表
     */
    List<String> suggestKeys(List<String> names, List<String> occupiedKeys);
}
