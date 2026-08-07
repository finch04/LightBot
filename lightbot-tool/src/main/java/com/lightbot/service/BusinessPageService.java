package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.businesspage.BusinessPageDefinition;
import com.lightbot.dto.BusinessPageHtmlGenerateDTO;
import com.lightbot.dto.BusinessPageHtmlNormalizeDTO;
import com.lightbot.dto.BusinessPageKeyConfigUpdateDTO;
import com.lightbot.dto.BusinessPageUpsertDTO;
import com.lightbot.entity.BusinessPage;
import com.lightbot.vo.BusinessPageKeyConfigVO;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 业务办理页注册与白名单（开发者元数据，以 DB 为准）。
 *
 * @author finch
 * @since 2026-08-04
 */
public interface BusinessPageService extends IService<BusinessPage> {

    /**
     * 解析启用中的页面定义（仅 DB）。
     */
    Optional<BusinessPageDefinition> resolveEnabled(String pageType);

    /**
     * 全部启用中的页面（仅 DB）。
     */
    Collection<BusinessPageDefinition> listEnabledDefinitions();

    /**
     * 管理端列表（含禁用）。
     */
    List<BusinessPage> listAllForAdmin();

    BusinessPage upsert(BusinessPageUpsertDTO dto);

    void setEnabled(Long id, boolean enabled);

    /** 删除业务页（开发者注册，均可删） */
    void deleteCustom(Long id);

    String catalogForToolDescription();

    /**
     * 解析最终允许的 pageType（API Key ∩ Agent 绑定）。
     *
     * @param apiKeyId     API Key，可空（控制台）
     * @param agentAllowed Agent 绑定的 pageType；null=不做 Agent 过滤（仅 API Key 管理预览）；
     *                     空列表=未绑定、无可用页；非空=与 Key 白名单取交集
     */
    Set<String> resolveAllowedPageTypes(Long apiKeyId, List<String> agentAllowed);

    boolean isPageTypeAllowed(String pageType, Long apiKeyId, List<String> agentAllowed);

    BusinessPageKeyConfigVO getKeyConfig(Long apiKeyId);

    BusinessPageKeyConfigVO updateKeyConfig(Long apiKeyId, BusinessPageKeyConfigUpdateDTO dto);

    /**
     * AI 辅助生成内嵌业务页 HTML（完整文档）。
     *
     * @param dto 生成需求
     * @return 完整 HTML 字符串
     */
    String generateHtml(BusinessPageHtmlGenerateDTO dto);

    /**
     * AI 规范化内嵌业务页 HTML：保留业务字段与逻辑，对齐平台样式/结构规范。
     *
     * @param dto 当前 HTML
     * @return 规范化后的完整 HTML
     */
    String normalizeHtml(BusinessPageHtmlNormalizeDTO dto);
}
