package com.lightbot.skill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Skill;
import com.lightbot.entity.Tool;
import com.lightbot.enums.CommonStatus;
import com.lightbot.mapper.SkillMapper;
import com.lightbot.mapper.ToolMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 内置 Skill 自动注册器
 * <p>对标 Yuxi 的内置 SKILL.md：启动时扫描 {@link BuiltInSkillDefinitions} 中声明的内置 Skill，
 * 不存在则插入，存在且 content_hash 不一致则更新（保持代码版本同步）。</p>
 *
 * <p>顺序在 {@code BuiltinToolRegistrar} 之后执行，确保 tool 表已建好可被引用。</p>
 *
 * @author finch
 * @since 2026-05-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(20)
public class BuiltInSkillRegistrar implements ApplicationRunner {

    private final SkillMapper skillMapper;
    private final ToolMapper toolMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void run(ApplicationArguments args) {
        log.info("[BuiltInSkillRegistrar] 开始注册内置 Skill ...");

        for (BuiltInSkillDefinitions.Definition def : BuiltInSkillDefinitions.list()) {
            registerOne(def);
        }

        log.info("[BuiltInSkillRegistrar] 内置 Skill 注册完成: 共 {} 个", BuiltInSkillDefinitions.list().size());
    }

    private void registerOne(BuiltInSkillDefinitions.Definition def) {
        String slug = def.slug();
        Skill existing = skillMapper.selectOne(
                new LambdaQueryWrapper<Skill>().eq(Skill::getSlug, slug));

        String toolIdsJson = serializeIds(resolveToolIds(def.toolNames()));
        String contentHash = sha256(def.promptTemplate() + "|" + def.description());

        if (existing == null) {
            Skill skill = new Skill();
            skill.setSlug(slug);
            skill.setName(def.name());
            skill.setDisplayName(def.displayName());
            skill.setDescription(def.description());
            skill.setPromptTemplate(def.promptTemplate());
            skill.setToolIds(toolIdsJson);
            skill.setMcpServerIds("[]");
            skill.setSortOrder(def.sortOrder());
            skill.setStatus(CommonStatus.ACTIVE);
            skill.setScope("global");
            skill.setIsBuiltin(1);
            skill.setContentHash(contentHash);
            skillMapper.insert(skill);
            log.info("[BuiltInSkillRegistrar] 注册内置 Skill: slug={}", slug);
            return;
        }

        // 已存在：仅当内容 hash 变化或必要字段缺失时更新
        boolean needsUpdate = !contentHash.equals(existing.getContentHash())
                || existing.getIsBuiltin() == null
                || !toolIdsJson.equals(existing.getToolIds());
        if (!needsUpdate) {
            return;
        }
        existing.setName(def.name());
        existing.setDisplayName(def.displayName());
        existing.setDescription(def.description());
        existing.setPromptTemplate(def.promptTemplate());
        existing.setToolIds(toolIdsJson);
        existing.setIsBuiltin(1);
        existing.setScope("global");
        existing.setContentHash(contentHash);
        if (existing.getSortOrder() == null) {
            existing.setSortOrder(def.sortOrder());
        }
        skillMapper.updateById(existing);
        log.info("[BuiltInSkillRegistrar] 更新内置 Skill: slug={}", slug);
    }

    /** 根据 Tool 英文标识批量解析出 Tool ID（缺失忽略） */
    private List<String> resolveToolIds(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (String name : toolNames) {
            Tool tool = toolMapper.selectByName(name);
            if (tool != null) {
                ids.add(String.valueOf(tool.getId()));
            } else {
                log.warn("[BuiltInSkillRegistrar] 未找到依赖工具: name={} (slug 关联将忽略)", name);
            }
        }
        return ids;
    }

    private String serializeIds(List<String> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
