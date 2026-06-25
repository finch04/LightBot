package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.SkillFileTreeNode;
import com.lightbot.dto.SkillImportPreview;
import com.lightbot.dto.SkillRequest;
import com.lightbot.entity.Skill;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.SkillMapper;
import com.lightbot.model.SkillMetadata;
import com.lightbot.service.McpServerService;
import com.lightbot.service.SkillService;
import com.lightbot.service.ToolService;
import com.lightbot.service.sandbox.SkillStorageService;
import com.lightbot.config.RedisCacheConfig;
import com.lightbot.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.Serializable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Skill 服务实现类
 * <p>支持「全局可复用 Skill」与「旧的按 Agent 私有 Skill」两种模式，
 * 新建 Skill 默认 scope=global，必须填写 slug。</p>
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl extends ServiceImpl<SkillMapper, Skill>
        implements SkillService {

    /** Skill slug 规范：3~64 个字符的英文/数字/短横线 */
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private final ObjectMapper objectMapper;
    private final SkillStorageService skillStorageService;
    private final ToolService toolService;
    private final McpServerService mcpServerService;

    @Override
    @Cacheable(value = RedisCacheConfig.CACHE_SKILL, key = "#id", unless = "#result == null")
    public Skill getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_SKILL, key = "#entity.id")
    public boolean updateById(Skill entity) {
        return super.updateById(entity);
    }

    @Override
    @Cacheable(value = RedisCacheConfig.CACHE_SKILL, key = "'slug:' + #slug", unless = "#result == null")
    public Skill getBySlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<Skill>().eq(Skill::getSlug, slug), false);
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisCacheConfig.CACHE_SKILL, allEntries = true)
    public Skill create(SkillRequest request) {
        // 1. 解析作用域：默认 global
        String scope = StringUtils.hasText(request.getScope()) ? request.getScope() : "global";
        if ("global".equals(scope)) {
            validateSlug(request.getSlug(), null);
        }

        // 1.1 校验名称唯一性
        long count = count(new LambdaQueryWrapper<Skill>().eq(Skill::getName, request.getName()));
        if (count > 0) {
            throw new BizException(ErrorCode.SKILL_NAME_EXISTS);
        }

        // 2. 组装实体
        String slug = request.getSlug();
        Skill skill = new Skill();
        skill.setSlug(slug);
        skill.setAgentId("agent".equals(scope) ? request.getAgentId() : null);
        skill.setName(request.getName());
        skill.setDisplayName(request.getDisplayName());
        skill.setDescription(request.getDescription());
        skill.setPromptTemplate(request.getPromptTemplate());
        skill.setConfig(request.getConfig());
        skill.setToolIds(toJsonArray(request.getToolIds()));
        skill.setMcpServerIds(toJsonArray(request.getMcpServerIds()));
        skill.setModelId(request.getModelId());
        skill.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        skill.setStatus(CommonStatus.ACTIVE);
        skill.setScope(scope);
        skill.setIsBuiltin(0);

        // 3. 新字段
        skill.setVersion(request.getVersion() != null ? request.getVersion() : "1.0.0");
        skill.setSkillDependencies(toJsonArray(request.getSkillDependencies()));
        skill.setSourceType(request.getSourceType() != null ? request.getSourceType() : "upload");

        // 4. 写入 MinIO 并设置 objectPrefix / contentHash
        if (slug != null && !slug.isBlank()) {
            skill.setObjectPrefix("skills/" + slug + "/");
            String skillMdContent = buildSkillMdContent(skill);
            skill.setContentHash(sha256(skillMdContent));
            skillStorageService.writeSkillMarkdown(slug, skillMdContent);
        }

        save(skill);
        return skill;
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisCacheConfig.CACHE_SKILL, allEntries = true)
    public Skill update(SkillRequest request) {
        Skill skill = getById(request.getId());
        if (skill == null) {
            throw new BizException(ErrorCode.SKILL_NOT_FOUND);
        }
        if (Integer.valueOf(1).equals(skill.getIsBuiltin())) {
            throw new BizException(ErrorCode.SKILL_BUILTIN_NOT_EDITABLE);
        }
        if ("global".equals(skill.getScope()) && StringUtils.hasText(request.getSlug())
                && !request.getSlug().equals(skill.getSlug())) {
            validateSlug(request.getSlug(), skill.getId());
            skill.setSlug(request.getSlug());
        }
        // 名称变更时校验唯一性
        if (!skill.getName().equals(request.getName())) {
            long count = count(new LambdaQueryWrapper<Skill>().eq(Skill::getName, request.getName()));
            if (count > 0) {
                throw new BizException(ErrorCode.SKILL_NAME_EXISTS);
            }
        }
        skill.setName(request.getName());
        skill.setDisplayName(request.getDisplayName());
        skill.setDescription(request.getDescription());
        skill.setConfig(request.getConfig());
        // 清理悬空工具/MCP引用
        request.setToolIds(cleanStaleToolIds(request.getToolIds()));
        request.setMcpServerIds(cleanStaleMcpIds(request.getMcpServerIds()));
        skill.setToolIds(toJsonArray(request.getToolIds()));
        skill.setMcpServerIds(toJsonArray(request.getMcpServerIds()));
        skill.setModelId(request.getModelId());
        skill.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);

        // 新字段
        if (request.getVersion() != null) {
            skill.setVersion(request.getVersion());
        }
        if (request.getSkillDependencies() != null) {
            skill.setSkillDependencies(toJsonArray(request.getSkillDependencies()));
        }

        // promptTemplate 变更时重写 MinIO
        boolean promptChanged = request.getPromptTemplate() != null
                && !request.getPromptTemplate().equals(skill.getPromptTemplate());
        if (promptChanged) {
            skill.setPromptTemplate(request.getPromptTemplate());
            if (skill.getSlug() != null) {
                skill.setObjectPrefix("skills/" + skill.getSlug() + "/");
                String skillMdContent = buildSkillMdContent(skill);
                skill.setContentHash(sha256(skillMdContent));
                skillStorageService.writeSkillMarkdown(skill.getSlug(), skillMdContent);
            }
        }

        updateById(skill);
        return skill;
    }

    @Override
    public List<Skill> listByAgentId(Long agentId, String name) {
        return list(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getAgentId, agentId)
                .like(StringUtils.hasText(name), Skill::getName, name)
                .orderByAsc(Skill::getSortOrder)
                .orderByDesc(Skill::getCreateTime));
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisCacheConfig.CACHE_SKILL, key = "#id")
    public void deleteById(Long id) {
        Skill skill = getById(id);
        if (skill == null) {
            throw new BizException(ErrorCode.SKILL_NOT_FOUND);
        }
        if (Integer.valueOf(1).equals(skill.getIsBuiltin())) {
            throw new BizException(ErrorCode.SKILL_BUILTIN_NOT_DELETABLE);
        }
        // 删除 MinIO 文件
        if (skill.getSlug() != null) {
            try {
                skillStorageService.deleteSkillDirectory(skill.getSlug());
            } catch (Exception e) {
                log.warn("[Skill] 删除 MinIO 文件失败, slug={}", skill.getSlug(), e);
            }
        }
        if (!removeById(id)) {
            throw new BizException(ErrorCode.SKILL_NOT_FOUND);
        }
    }

    @Override
    public Page<Skill> listGlobal(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<Skill>()
                .eq(Skill::getScope, "global")
                .orderByDesc(Skill::getIsBuiltin)
                .orderByAsc(Skill::getSortOrder)
                .orderByDesc(Skill::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Skill::getName, keyword)
                    .or().like(Skill::getDisplayName, keyword)
                    .or().like(Skill::getSlug, keyword));
        }
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public List<Skill> listEnabled() {
        return list(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getScope, "global")
                .eq(Skill::getStatus, CommonStatus.ACTIVE)
                .orderByDesc(Skill::getIsBuiltin)
                .orderByAsc(Skill::getSortOrder)
                .orderByDesc(Skill::getCreateTime));
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_SKILL, key = "#id")
    public void setEnabled(Long id, boolean enabled) {
        Skill skill = getById(id);
        if (skill == null) {
            throw new BizException(ErrorCode.SKILL_NOT_FOUND);
        }
        skill.setStatus(enabled ? CommonStatus.ACTIVE : CommonStatus.DISABLED);
        updateById(skill);
    }

    // ==================== ZIP 导入导出 ====================

    @Override
    public SkillImportPreview importZipStage(InputStream zipStream) {
        String draftId = java.util.UUID.randomUUID().toString().replace("-", "");
        return skillStorageService.stageDraft(draftId, zipStream);
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisCacheConfig.CACHE_SKILL, allEntries = true)
    public Skill importZipCommit(String draftId, String targetSlug) {
        // 1. 提交草稿到正式目录
        String finalSlug = skillStorageService.commitDraft(draftId, targetSlug);

        // 2. 读取 SKILL.md 解析元数据
        String skillMdContent = skillStorageService.getSkillMarkdown(finalSlug);
        SkillMetadata metadata = skillStorageService.parseSkillMarkdown(skillMdContent);

        // 3. 检查 slug 冲突
        Skill existing = getBySlug(finalSlug);
        if (existing != null) {
            // slug 冲突，自动追加后缀
            String baseSlug = finalSlug;
            for (int i = 2; i <= 100; i++) {
                finalSlug = baseSlug + "-v" + i;
                if (getBySlug(finalSlug) == null) {
                    break;
                }
            }
            if (getBySlug(finalSlug) != null) {
                throw new BizException(ErrorCode.SKILL_SLUG_CONFLICT, baseSlug);
            }
            // 重命名 MinIO 目录
            skillStorageService.deleteSkillDirectory(finalSlug);
            skillStorageService.writeSkillMarkdown(finalSlug, skillMdContent);
        }

        // 4. 创建 DB 记录
        Skill skill = new Skill();
        skill.setSlug(finalSlug);
        skill.setName(metadata.getName() != null ? metadata.getName() : finalSlug);
        skill.setDisplayName(metadata.getName());
        skill.setDescription(metadata.getDescription());
        skill.setPromptTemplate(metadata.getPromptTemplate());
        skill.setToolIds("[]");
        skill.setMcpServerIds("[]");
        skill.setSkillDependencies(toJsonArray(metadata.getSkillDependencies()));
        skill.setVersion(metadata.getVersion() != null ? metadata.getVersion() : "1.0.0");
        skill.setSourceType("upload");
        skill.setObjectPrefix("skills/" + finalSlug + "/");
        skill.setContentHash(sha256(skillMdContent));
        skill.setSortOrder(0);
        skill.setStatus(CommonStatus.ACTIVE);
        skill.setScope("global");
        skill.setIsBuiltin(0);
        save(skill);

        log.info("[Skill] ZIP 导入完成: slug={}, name={}", finalSlug, skill.getName());
        return skill;
    }

    @Override
    public byte[] exportZip(Long skillId) {
        Skill skill = getById(skillId);
        if (skill == null) {
            throw new BizException(ErrorCode.SKILL_NOT_FOUND);
        }
        if (skill.getSlug() == null) {
            throw new BizException(ErrorCode.SKILL_FILE_NOT_FOUND);
        }
        return skillStorageService.exportSkillZip(skill.getSlug());
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisCacheConfig.CACHE_SKILL, allEntries = true)
    public Skill commitRemoteSkill(String draftId, String slug) {
        // 1. 提交草稿中指定 slug 的文件到正式目录
        String finalSlug = skillStorageService.commitDraftForSlug(draftId, slug);

        // 2. 读取 SKILL.md 解析元数据
        String skillMdContent = skillStorageService.getSkillMarkdown(finalSlug);
        SkillMetadata metadata = skillStorageService.parseSkillMarkdown(skillMdContent);

        // 3. 检查 slug 冲突，自动追加后缀
        Skill existing = getBySlug(finalSlug);
        if (existing != null) {
            String baseSlug = finalSlug;
            for (int i = 2; i <= 100; i++) {
                finalSlug = baseSlug + "-v" + i;
                if (getBySlug(finalSlug) == null) break;
            }
            if (getBySlug(finalSlug) != null) {
                throw new BizException(ErrorCode.SKILL_SLUG_CONFLICT, baseSlug);
            }
            skillStorageService.deleteSkillDirectory(finalSlug);
            skillStorageService.writeSkillMarkdown(finalSlug, skillMdContent);
        }

        // 4. 创建 DB 记录
        Skill skill = new Skill();
        skill.setSlug(finalSlug);
        skill.setName(metadata.getName() != null ? metadata.getName() : finalSlug);
        skill.setDisplayName(metadata.getName());
        skill.setDescription(metadata.getDescription());
        skill.setPromptTemplate(metadata.getPromptTemplate());
        skill.setToolIds("[]");
        skill.setMcpServerIds("[]");
        skill.setSkillDependencies(toJsonArray(metadata.getSkillDependencies()));
        skill.setVersion(metadata.getVersion() != null ? metadata.getVersion() : "1.0.0");
        skill.setSourceType("remote");
        skill.setObjectPrefix("skills/" + finalSlug + "/");
        skill.setContentHash(sha256(skillMdContent));
        skill.setSortOrder(0);
        skill.setStatus(CommonStatus.ACTIVE);
        skill.setScope("global");
        skill.setIsBuiltin(0);
        save(skill);

        log.info("[Skill] 远程安装完成: slug={}, name={}", finalSlug, skill.getName());
        return skill;
    }

    @Override
    public void cleanupDraft(String draftId) {
        skillStorageService.cleanupDraft(draftId);
    }

    @Override
    public List<Skill> listBySlugs(Collection<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            return List.of();
        }
        return list(new LambdaQueryWrapper<Skill>()
                .in(Skill::getSlug, slugs)
                .eq(Skill::getStatus, CommonStatus.ACTIVE));
    }

    @Override
    public Map<String, List<String>> buildDependencyMap(Collection<String> slugs) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        List<Skill> skills = listBySlugs(slugs);
        for (Skill skill : skills) {
            result.put(skill.getSlug(), parseStringList(skill.getSkillDependencies()));
        }
        return result;
    }

    // ==================== 文件管理 ====================

    @Override
    public List<SkillFileTreeNode> listFiles(Long id) {
        Skill skill = getById(id);
        if (skill == null) {
            throw new BizException(ErrorCode.SKILL_NOT_FOUND);
        }
        if (skill.getSlug() == null) {
            return List.of();
        }
        return skillStorageService.buildFileTree(skill.getSlug());
    }

    @Override
    public byte[] readFile(Long id, String path) {
        Skill skill = getById(id);
        if (skill == null) {
            throw new BizException(ErrorCode.SKILL_NOT_FOUND);
        }
        if (skill.getSlug() == null) {
            throw new BizException(ErrorCode.SKILL_FILE_NOT_FOUND);
        }
        skillStorageService.validateFilePath(path);
        return skillStorageService.readFile(skill.getSlug(), path);
    }

    @Override
    public void createFile(Long id, String path, String content, boolean isDir) {
        Skill skill = getById(id);
        if (skill == null) {
            throw new BizException(ErrorCode.SKILL_NOT_FOUND);
        }
        if (skill.getSlug() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Skill 未关联文件目录");
        }
        skillStorageService.validateFilePath(path);
        if (isDir) {
            // 目录：创建一个占位的 .gitkeep 文件
            String dirPath = path.endsWith("/") ? path : path + "/";
            skillStorageService.writeFile(skill.getSlug(), dirPath + ".gitkeep", "");
        } else {
            skillStorageService.writeFile(skill.getSlug(), path, content != null ? content : "");
        }
    }

    @Override
    public void updateFile(Long id, String path, String content) {
        Skill skill = getById(id);
        if (skill == null) {
            throw new BizException(ErrorCode.SKILL_NOT_FOUND);
        }
        if (skill.getSlug() == null) {
            throw new BizException(ErrorCode.SKILL_FILE_NOT_FOUND);
        }
        skillStorageService.validateFilePath(path);
        skillStorageService.writeFile(skill.getSlug(), path, content != null ? content : "");
    }

    @Override
    public void deleteFile(Long id, String path) {
        Skill skill = getById(id);
        if (skill == null) {
            throw new BizException(ErrorCode.SKILL_NOT_FOUND);
        }
        if (skill.getSlug() == null) {
            throw new BizException(ErrorCode.SKILL_FILE_NOT_FOUND);
        }
        skillStorageService.validateFilePath(path);
        skillStorageService.deleteFile(skill.getSlug(), path);
    }

    // ==================== 内部方法 ====================

    /** 校验 slug 格式与唯一性 */
    private void validateSlug(String slug, Long currentId) {
        if (!StringUtils.hasText(slug)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "全局 Skill 必须填写 slug");
        }
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "slug 只能包含小写字母、数字和短横线");
        }
        Skill exists = getBySlug(slug);
        if (exists != null && !exists.getId().equals(currentId)) {
            throw new BizException(ErrorCode.SKILL_SLUG_CONFLICT, slug);
        }
    }

    /** 将字符串列表序列化为 JSON 数组（无元素时返回 "[]"） */
    private String toJsonArray(List<String> items) {
        try {
            if (items == null || items.isEmpty()) {
                return "[]";
            }
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 解析 JSONB 字符串数组 */
    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<>() {});
            return list != null ? list : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 从 Skill 实体构建 SKILL.md 内容 */
    private String buildSkillMdContent(Skill skill) {
        SkillMetadata metadata = SkillMetadata.builder()
                .slug(skill.getSlug())
                .name(skill.getName())
                .description(skill.getDescription())
                .version(skill.getVersion() != null ? skill.getVersion() : "1.0.0")
                .skillDependencies(parseStringList(skill.getSkillDependencies()))
                .promptTemplate(skill.getPromptTemplate())
                .build();
        return skillStorageService.buildSkillMarkdown(metadata);
    }

    private String sha256(String input) {
        return HashUtil.sha256(input);
    }

    private List<String> cleanStaleToolIds(List<String> ids) {
        return toolService.cleanStaleToolIds(ids);
    }

    /**
     * 清理悬空MCP服务ID：过滤掉已被删除的MCP服务
     *
     * @param ids MCP服务ID列表
     * @return 过滤后仍存在的MCP服务ID列表
     */
    private List<String> cleanStaleMcpIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return ids;
        }
        List<Long> longIds = ids.stream().map(Long::parseLong).toList();
        Set<String> existing = mcpServerService.listByIds(longIds).stream()
                .map(m -> String.valueOf(m.getId()))
                .collect(Collectors.toSet());
        return ids.stream().filter(existing::contains).toList();
    }
}
