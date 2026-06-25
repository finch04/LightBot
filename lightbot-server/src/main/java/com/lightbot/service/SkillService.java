package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.SkillFileTreeNode;
import com.lightbot.dto.SkillImportPreview;
import com.lightbot.dto.SkillRequest;
import com.lightbot.entity.Skill;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Skill 服务接口
 *
 * @author finch
 * @since 2026-05-20
 */
public interface SkillService extends IService<Skill> {

    /** 创建 Skill（自定义） */
    Skill create(SkillRequest request);

    /** 更新 Skill（内置不可编辑） */
    Skill update(SkillRequest request);

    /** 按 Agent 兼容查询（旧接口，保留） */
    List<Skill> listByAgentId(Long agentId, String name);

    /** 删除 Skill（内置不可删） */
    void deleteById(Long id);

    /** 分页查询全局 Skill 库 */
    Page<Skill> listGlobal(int pageNum, int pageSize, String keyword);

    /** 获取所有启用的全局 Skill（供 Agent 绑定下拉） */
    List<Skill> listEnabled();

    /** 按 slug 获取 */
    Skill getBySlug(String slug);

    /** 设置启用/禁用 */
    void setEnabled(Long id, boolean enabled);

    /** ZIP 导入（阶段一：暂存草稿并返回预览） */
    SkillImportPreview importZipStage(InputStream zipStream);

    /** ZIP 导入（阶段二：确认提交） */
    Skill importZipCommit(String draftId, String targetSlug);

    /** 远程安装确认（提交草稿中指定 slug 的 Skill） */
    Skill commitRemoteSkill(String draftId, String slug);

    /** 清理远程安装草稿 */
    void cleanupDraft(String draftId);

    /** ZIP 导出 */
    byte[] exportZip(Long skillId);

    /** 根据 slug 列表批量查询 */
    List<Skill> listBySlugs(Collection<String> slugs);

    /** 构建 slug -> skillDependencies 映射（用于依赖闭包展开） */
    Map<String, List<String>> buildDependencyMap(Collection<String> slugs);

    // ==================== 文件管理 ====================

    /** 获取 Skill 文件树 */
    List<SkillFileTreeNode> listFiles(Long id);

    /** 读取 Skill 文件内容 */
    byte[] readFile(Long id, String path);

    /** 创建 Skill 文件/目录 */
    void createFile(Long id, String path, String content, boolean isDir);

    /** 更新 Skill 文件内容 */
    void updateFile(Long id, String path, String content);

    /** 删除 Skill 文件/目录 */
    void deleteFile(Long id, String path);
}
