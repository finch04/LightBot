package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.businesspage.BusinessPageDefinition;
import com.lightbot.common.BizException;
import com.lightbot.dto.BusinessPageHtmlGenerateDTO;
import com.lightbot.dto.BusinessPageHtmlNormalizeDTO;
import com.lightbot.dto.BusinessPageKeyConfigUpdateDTO;
import com.lightbot.dto.BusinessPageUpsertDTO;
import com.lightbot.entity.ApiKey;
import com.lightbot.entity.BusinessPage;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.BusinessPageMapper;
import com.lightbot.model.ModelFactory;
import com.lightbot.model.ProviderResolver;
import com.lightbot.service.ApiKeyService;
import com.lightbot.service.BusinessPageService;
import com.lightbot.util.LlmTraceContext;
import com.lightbot.vo.BusinessPageKeyConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 业务办理页注册表：仅以 DB 为准，由开发者在能力中心注册。
 *
 * @author finch
 * @since 2026-08-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessPageServiceImpl extends ServiceImpl<BusinessPageMapper, BusinessPage>
        implements BusinessPageService {

    /**
     * 业务页 HTML 生成系统约束（与宿主静默桥接、平台示例模板视觉规范保持一致）。
     */
    private static final String GENERATE_HTML_SYSTEM = """
            你是前端工程师，专门为 LightBot 业务办理页生成「完整 HTML 文档」（含 <!DOCTYPE html>、CSS、JS）。
            硬性要求：
            1. 只输出 HTML 源码本身，不要 markdown 代码围栏，不要解释文字。
            2. 表单校验与业务请求写在页面内；使用 fetch 调用接口（method 用 POST）。
            3. 未提供真实接口时，fetch 地址使用 /__lightbot_bp_demo__（宿主会模拟成功响应）；可用 API_URL 常量，空则走演示路径。
            4. 成功后不要写 parent.postMessage / LightBot.submit；宿主会静默拦截成功请求。
            5. 必须有「取消」按钮，文案为「取消」或 id/class 含 cancel；提交按钮文案为「提交」，办理中禁用并改文案「办理中…」。
            6. 可监听 message：source=lightbot-business-page 且 type=init，用 payload.props 预填；同时兼容 window.__LIGHTBOT_BP_INIT__。
            7. 不要引入外部 CDN / 图标库 / Tailwind / Bootstrap（除非需求明确要求）。
            8. 每个输入必须有可见中文 <label>，并与控件关联（包裹控件，或 label[for]=input.id，或紧邻在输入上方）；
               input 同时设置 id 与 name（同字段码）。宿主办结摘要会采集 label 文案，不会翻译 name。
            9. 结构建议：顶部 hint 说明 → 字段区 → error 提示条 → 右对齐 actions（取消 + 主按钮）。
            10. JS 建议提供 showError / clearError / setBusy / readForm / validate / applyInit，逻辑清晰可改。

            【视觉与样式（必须贴近平台示例模板，禁止另起一套设计语言）】
            - 画布：body margin:0；padding:12px；font-family: system-ui, sans-serif；color:#111。
            - 适配对话内窄宽度（约 420px），单列布局，移动端友好；不要宽卡片阴影、渐变背景、大圆角营销风。
            - 标签：display:block；font-size:13px；margin:10px 0 4px。
            - 控件（input/select/textarea）：width:100%；padding:8px 10px；border:1px solid #d4d4d8；
              border-radius:8px；font-size:14px；背景默认白色，勿用厚重描边或彩色输入框。
            - 说明文案 .hint：font-size:12px；color:#71717a；margin:0 0 8px。
            - 错误条 .error：默认隐藏；展示时 padding:8px 10px；border-radius:8px；
              background:#fef2f2；color:#b91c1c；font-size:12px；line-height:1.45。
            - 操作区 .actions：display:flex；justify-content:flex-end；gap:8px；margin-top:16px。
            - 按钮：padding:8px 14px；border-radius:8px；border:1px solid #d4d4d8；background:#fff；cursor:pointer。
              主按钮 .primary：background:#171717；color:#fff；border-color:#171717。
              disabled：opacity:0.55；cursor:not-allowed。
            - 配色克制：主色近黑 #171717，边框/分割 #d4d4d8，次要文字 #71717a，错误红仅用于错误态。
            - 禁止：紫色霓虹、大面积渐变、玻璃拟态、过度阴影、emoji 装饰、居中大标题海报式布局。
            - 可按业务增删字段与校验文案，但 CSS token（圆角 8px、字号、颜色、按钮层级、间距节奏）必须与上述规范一致。
            """;

    /**
     * 规范化：在现有页面上套用平台样式与交互骨架，尽量不改业务语义。
     */
    private static final String NORMALIZE_HTML_SYSTEM = """
            你是前端工程师，负责把已有业务办理页 HTML「对齐平台样式与交互规范」。
            硬性要求：
            1. 只输出完整 HTML 源码，不要 markdown 围栏，不要解释。
            2. 必须保留原页面的业务字段、中文 label、校验规则、接口地址与请求体语义；不要删改业务含义。
            3. 将视觉与结构改造成平台规范：
               - body：padding 12px，font-family:system-ui,sans-serif，color:#111，无大阴影/渐变/营销风
               - label：block，13px，margin 10px 0 4px
               - input/select/textarea：全宽，padding 8px 10px，border 1px #d4d4d8，radius 8px，font-size 14px
               - .hint：12px，#71717a
               - .error：默认隐藏，展示时 #fef2f2 / #b91c1c，radius 8px
               - .actions：右对齐 flex，gap 8px；取消为普通按钮，提交为 .primary（#171717）
               - 窄宽约 420px 单列；去掉外部 CDN / Tailwind / Bootstrap / emoji 装饰
            4. 交互骨架对齐：hint → 字段 → error → actions；取消文案「取消」或 id/class 含 cancel；
               提交「提交」，办理中禁用并改「办理中…」；保留 fetch POST；无真实接口时用 /__lightbot_bp_demo__。
            5. 保留 init：message source=lightbot-business-page type=init，以及 window.__LIGHTBOT_BP_INIT__。
            6. 每个控件同时有 id 与 name，并有可见中文 label 关联。
            7. 若原页面已接近规范，做最小改动即可；不要借机重写业务字段名。
            """;

    /**
     * 平台示例模板骨架（样式/结构参考，字段可按需求替换）。
     */
    private static final String STYLE_REFERENCE_HTML = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>业务办理</title>
              <style>
                * { box-sizing: border-box; }
                body { margin: 0; font-family: system-ui, sans-serif; padding: 12px; color: #111; }
                label { display: block; font-size: 13px; margin: 10px 0 4px; }
                input, select, textarea {
                  width: 100%; padding: 8px 10px; border: 1px solid #d4d4d8;
                  border-radius: 8px; font-size: 14px;
                }
                .actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
                button {
                  padding: 8px 14px; border-radius: 8px; border: 1px solid #d4d4d8;
                  background: #fff; cursor: pointer;
                }
                button.primary { background: #171717; color: #fff; border-color: #171717; }
                button:disabled { opacity: 0.55; cursor: not-allowed; }
                .hint { font-size: 12px; color: #71717a; margin: 0 0 8px; }
                .error {
                  display: none; margin-top: 10px; padding: 8px 10px; border-radius: 8px;
                  background: #fef2f2; color: #b91c1c; font-size: 12px; line-height: 1.45;
                }
                .error.show { display: block; }
              </style>
            </head>
            <body>
              <p class="hint" id="hint">请填写信息后提交。</p>
              <label for="field1">示例字段一
                <input id="field1" name="field1" autocomplete="off" />
              </label>
              <div class="error" id="error"></div>
              <div class="actions">
                <button type="button" id="btnCancel">取消</button>
                <button type="button" class="primary" id="btnSubmit">提交</button>
              </div>
            </body>
            </html>
            """;

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;
    private final ModelFactory modelFactory;
    private final ProviderResolver providerResolver;

    @Override
    public Optional<BusinessPageDefinition> resolveEnabled(String pageType) {
        if (pageType == null || pageType.isBlank()) {
            return Optional.empty();
        }
        String key = pageType.trim();
        BusinessPage db = getOne(new LambdaQueryWrapper<BusinessPage>().eq(BusinessPage::getPageType, key), false);
        if (db == null || !Integer.valueOf(1).equals(db.getEnabled())) {
            return Optional.empty();
        }
        return Optional.of(toDefinition(db));
    }

    @Override
    public Collection<BusinessPageDefinition> listEnabledDefinitions() {
        List<BusinessPage> rows = list(new LambdaQueryWrapper<BusinessPage>()
                .eq(BusinessPage::getEnabled, 1)
                .orderByAsc(BusinessPage::getPageType));
        return rows.stream().map(this::toDefinition).toList();
    }

    @Override
    public List<BusinessPage> listAllForAdmin() {
        return list(new LambdaQueryWrapper<BusinessPage>().orderByAsc(BusinessPage::getPageType));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessPage upsert(BusinessPageUpsertDTO dto) {
        // 1. 校验：仅支持内嵌 HTML（保存时清空外链）
        String pageType = dto.getPageType().trim();
        String pageHtml = dto.getPageHtml() == null ? null : dto.getPageHtml().trim();
        if (pageHtml != null && pageHtml.isBlank()) {
            pageHtml = null;
        }
        if (pageHtml == null) {
            throw new BizException("请填写内嵌 HTML 页面内容");
        }
        if (pageHtml.length() > 512 * 1024) {
            throw new BizException("内嵌 HTML 过大，请控制在 512KB 以内");
        }
        BusinessPage existing = getOne(new LambdaQueryWrapper<BusinessPage>().eq(BusinessPage::getPageType, pageType), false);
        // 2. 写入（开发者注册，一律非平台内置）
        BusinessPage entity = existing != null ? existing : new BusinessPage();
        entity.setPageType(pageType);
        entity.setDisplayName(dto.getDisplayName().trim());
        entity.setDescription(dto.getDescription());
        entity.setDefaultTitle(dto.getDefaultTitle() != null && !dto.getDefaultTitle().isBlank()
                ? dto.getDefaultTitle().trim() : dto.getDisplayName().trim());
        entity.setPageHtml(pageHtml);
        entity.setPageUrl(null);
        // 默认仅允许对话内嵌；开发者可显式传入 drawer
        entity.setAllowedModes(writeJson(dto.getAllowedModes() != null ? dto.getAllowedModes() : List.of("inline")));
        entity.setAllowedActions(writeJson(dto.getAllowedActions() != null ? dto.getAllowedActions() : List.of("submit", "cancel")));
        entity.setAllowedPropKeys(writeJson(dto.getAllowedPropKeys() != null ? dto.getAllowedPropKeys() : List.of()));
        entity.setAllowedOptionKeys(writeJson(dto.getAllowedOptionKeys() != null ? dto.getAllowedOptionKeys() : List.of()));
        entity.setDefaultProps(writeJson(dto.getDefaultProps() != null ? dto.getDefaultProps() : Map.of()));
        entity.setDefaultOptions(writeJson(dto.getDefaultOptions() != null ? dto.getDefaultOptions() : Map.of()));
        // formSchema 已废弃，保存时清空
        entity.setFormSchema(null);
        entity.setEnabled(dto.getEnabled() == null || Boolean.TRUE.equals(dto.getEnabled()) ? 1 : 0);
        entity.setBuiltin(0);
        if (existing == null) {
            save(entity);
        } else {
            updateById(entity);
        }
        return getById(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setEnabled(Long id, boolean enabled) {
        BusinessPage page = getById(id);
        if (page == null) {
            throw new BizException("业务页不存在");
        }
        page.setEnabled(enabled ? 1 : 0);
        updateById(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCustom(Long id) {
        BusinessPage page = getById(id);
        if (page == null) {
            throw new BizException("业务页不存在");
        }
        removeById(id);
    }

    @Override
    public String catalogForToolDescription() {
        String catalog = listEnabledDefinitions().stream()
                .map(d -> d.pageType() + "=" + d.displayName())
                .collect(Collectors.joining("；"));
        return catalog.isBlank() ? "（尚未在能力中心注册任何业务页）" : catalog;
    }

    @Override
    public Set<String> resolveAllowedPageTypes(Long apiKeyId, List<String> agentAllowed) {
        Set<String> enabled = listEnabledDefinitions().stream()
                .map(BusinessPageDefinition::pageType)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> fromKey = resolveKeyAllowed(apiKeyId, enabled);
        // null：仅 API Key 管理端预览，不做 Agent 侧过滤
        if (agentAllowed == null) {
            return fromKey;
        }
        Set<String> agentSet = agentAllowed.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // 空列表：Agent 未绑定业务页组件
        if (agentSet.isEmpty()) {
            return Set.of();
        }
        Set<String> intersect = new LinkedHashSet<>();
        for (String type : fromKey) {
            if (agentSet.contains(type)) {
                intersect.add(type);
            }
        }
        return intersect;
    }

    @Override
    public boolean isPageTypeAllowed(String pageType, Long apiKeyId, List<String> agentAllowed) {
        if (pageType == null || pageType.isBlank()) {
            return false;
        }
        return resolveAllowedPageTypes(apiKeyId, agentAllowed).contains(pageType.trim());
    }

    @Override
    public BusinessPageKeyConfigVO getKeyConfig(Long apiKeyId) {
        ApiKey apiKey = requireApiKey(apiKeyId);
        Map<String, Object> cfg = parseKeyConfig(apiKey.getBusinessPageConfig());
        BusinessPageKeyConfigVO vo = new BusinessPageKeyConfigVO();
        vo.setApiKeyId(apiKeyId);
        boolean inherit = !Boolean.FALSE.equals(cfg.get("inherit"));
        vo.setInherit(inherit);
        vo.setAllowedPageTypes(asStringList(cfg.get("allowedPageTypes")));
        vo.setEffectivePageTypes(new ArrayList<>(resolveAllowedPageTypes(apiKeyId, null)));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessPageKeyConfigVO updateKeyConfig(Long apiKeyId, BusinessPageKeyConfigUpdateDTO dto) {
        ApiKey apiKey = requireApiKey(apiKeyId);
        if (dto == null || !Boolean.FALSE.equals(dto.getInherit())) {
            apiKey.setBusinessPageConfig(null);
            apiKeyService.updateById(apiKey);
            return getKeyConfig(apiKeyId);
        }
        Map<String, Object> override = new LinkedHashMap<>();
        override.put("inherit", false);
        List<String> types = dto.getAllowedPageTypes() != null ? dto.getAllowedPageTypes() : List.of();
        override.put("allowedPageTypes", types.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .toList());
        apiKey.setBusinessPageConfig(writeJson(override));
        apiKeyService.updateById(apiKey);
        return getKeyConfig(apiKeyId);
    }

    @Override
    public String generateHtml(BusinessPageHtmlGenerateDTO dto) {
        // 1. 组装用户提示
        StringBuilder user = new StringBuilder();
        user.append("请生成业务办理页 HTML。\n");
        if (dto.getPageType() != null && !dto.getPageType().isBlank()) {
            user.append("pageType：").append(dto.getPageType().trim()).append('\n');
        }
        if (dto.getDisplayName() != null && !dto.getDisplayName().isBlank()) {
            user.append("展示名称：").append(dto.getDisplayName().trim()).append('\n');
        }
        if (dto.getDescription() != null && !dto.getDescription().isBlank()) {
            user.append("描述：").append(dto.getDescription().trim()).append('\n');
        }
        user.append("需求：").append(dto.getRequirement().trim()).append('\n');
        boolean basedOnCurrent = Boolean.TRUE.equals(dto.getBasedOnCurrent())
                && dto.getCurrentHtml() != null && !dto.getCurrentHtml().isBlank();
        if (basedOnCurrent) {
            user.append("\n请在以下现有 HTML 基础上修改：保留平台样式 token 与交互骨架（hint/label/error/actions、主次按钮），");
            user.append("按需求调整字段、校验与接口逻辑；不要换成另一套视觉风格。\n");
            user.append(dto.getCurrentHtml().trim());
        } else {
            // 从零生成时注入示例模板，强制视觉与结构对齐平台样式
            user.append("\n以下是平台示例模板（样式与结构必须对齐；字段名/校验/接口按需求改写）：\n");
            user.append(STYLE_REFERENCE_HTML);
        }

        // 2. 调用模型生成并清洗围栏
        return callLlmForHtml(GENERATE_HTML_SYSTEM, user.toString(), "生成");
    }

    @Override
    public String normalizeHtml(BusinessPageHtmlNormalizeDTO dto) {
        // 1. 组装规范化提示：保留业务，对齐平台样式
        StringBuilder user = new StringBuilder();
        user.append("请将下列业务办理页 HTML 对齐平台样式与交互规范。\n");
        if (dto.getPageType() != null && !dto.getPageType().isBlank()) {
            user.append("pageType：").append(dto.getPageType().trim()).append('\n');
        }
        if (dto.getDisplayName() != null && !dto.getDisplayName().isBlank()) {
            user.append("展示名称：").append(dto.getDisplayName().trim()).append('\n');
        }
        if (dto.getDescription() != null && !dto.getDescription().isBlank()) {
            user.append("描述：").append(dto.getDescription().trim()).append('\n');
        }
        user.append("\n平台样式参考（结构/CSS token 必须对齐）：\n");
        user.append(STYLE_REFERENCE_HTML);
        user.append("\n\n待规范化的当前 HTML：\n");
        user.append(dto.getCurrentHtml().trim());

        // 2. 调用模型规范化
        return callLlmForHtml(NORMALIZE_HTML_SYSTEM, user.toString(), "规范化");
    }

    /**
     * 调用默认模型生成/改写 HTML，并去掉 markdown 围栏。
     */
    private String callLlmForHtml(String systemPrompt, String userPrompt, String actionLabel) {
        Long providerId = providerResolver.resolve();
        ChatModel chatModel = modelFactory.getChatModel(providerId);
        String result;
        try {
            List<org.springframework.ai.chat.messages.Message> messages = List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            );
            ChatResponse response = LlmTraceContext.callWithoutTrace(() -> chatModel.call(new Prompt(messages)));
            result = response.getResult().getOutput().getText();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[BusinessPage] AI {} HTML 失败: {}", actionLabel, e.getMessage());
            throw new BizException(ErrorCode.AI_GENERATE_FAILED);
        }
        if (result == null || result.isBlank()) {
            throw new BizException(ErrorCode.AI_GENERATE_FAILED);
        }
        String html = result.trim()
                .replaceAll("(?s)^```(?:html)?\\s*", "")
                .replaceAll("(?s)\\s*```$", "")
                .trim();
        if (!html.toLowerCase().contains("<html")) {
            log.warn("[BusinessPage] AI {} 返回内容不像完整 HTML", actionLabel);
        }
        return html;
    }

    private Set<String> resolveKeyAllowed(Long apiKeyId, Set<String> enabled) {
        if (apiKeyId == null) {
            return enabled;
        }
        ApiKey apiKey = apiKeyService.getById(apiKeyId);
        if (apiKey == null) {
            return enabled;
        }
        Map<String, Object> cfg = parseKeyConfig(apiKey.getBusinessPageConfig());
        if (!Boolean.FALSE.equals(cfg.get("inherit"))) {
            return enabled;
        }
        List<String> allowed = asStringList(cfg.get("allowedPageTypes"));
        Set<String> result = new LinkedHashSet<>();
        for (String type : allowed) {
            if (enabled.contains(type)) {
                result.add(type);
            }
        }
        return result;
    }

    private ApiKey requireApiKey(Long apiKeyId) {
        if (apiKeyId == null) {
            throw new BizException(ErrorCode.API_KEY_NOT_FOUND);
        }
        ApiKey apiKey = apiKeyService.getById(apiKeyId);
        if (apiKey == null) {
            throw new BizException(ErrorCode.API_KEY_NOT_FOUND);
        }
        return apiKey;
    }

    private BusinessPageDefinition toDefinition(BusinessPage row) {
        return BusinessPageDefinition.of(
                row.getPageType(),
                row.getDisplayName(),
                row.getDescription(),
                row.getDefaultTitle(),
                row.getPageHtml(),
                row.getPageUrl(),
                asStringList(readJson(row.getAllowedModes())),
                asStringList(readJson(row.getAllowedActions())),
                asStringList(readJson(row.getAllowedPropKeys())),
                asStringList(readJson(row.getAllowedOptionKeys())),
                asObjectMap(readJson(row.getDefaultProps())),
                asObjectMap(readJson(row.getDefaultOptions())),
                null,
                false
        );
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new BizException("JSON 序列化失败");
        }
    }

    private Map<String, Object> parseKeyConfig(String json) {
        if (json == null || json.isBlank()) {
            return Map.of("inherit", true);
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            return map != null ? map : Map.of("inherit", true);
        } catch (Exception e) {
            return Map.of("inherit", true);
        }
    }

    private List<String> asStringList(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    out.add(String.valueOf(item).trim());
                }
            }
            return out;
        }
        return List.of();
    }

    private Map<String, Object> asObjectMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    out.put(String.valueOf(k), v);
                }
            });
            return out;
        }
        return Map.of();
    }
}
