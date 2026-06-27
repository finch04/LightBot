package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.dto.ModelProviderPresetVO;
import com.lightbot.dto.ModelProviderRequest;
import com.lightbot.entity.ModelProvider;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.ModelProviderType;
import com.lightbot.mapper.ModelProviderMapper;
import com.lightbot.service.ModelProviderService;
import com.lightbot.service.ModelService;
import com.lightbot.util.ModelProviderCacheUtil;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 模型提供商服务实现类
 * <p>纯数据层 CRUD，不依赖 ModelFactory，避免循环依赖</p>
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelProviderServiceImpl extends ServiceImpl<ModelProviderMapper, ModelProvider>
        implements ModelProviderService {

    private static final String ICON_BASE = "https://registry.npmmirror.com/@lobehub/icons-static-svg/latest/files/icons";
    private static final String EMPTY_JSON = "{}";

    private static final String OPENAI_LOGO = ICON_BASE + "/openai.svg";
    private static final String DEEPSEEK_LOGO = ICON_BASE + "/deepseek-color.svg";
    private static final String ZHIPU_LOGO = ICON_BASE + "/zhipu-color.svg";
    private static final String MOONSHOT_LOGO = ICON_BASE + "/moonshot.svg";
    private static final String MINIMAX_LOGO = ICON_BASE + "/minimax-color.svg";
    private static final String DASHSCOPE_LOGO = ICON_BASE + "/bailian-color.svg";
    private static final String SILICONFLOW_LOGO = ICON_BASE + "/siliconcloud-color.svg";
    private static final String OPENROUTER_LOGO = ICON_BASE + "/openrouter.svg";
    private static final String MODELSCOPE_LOGO = ICON_BASE + "/modelscope-color.svg";
    private static final String ZAI_LOGO = ICON_BASE + "/zai.svg";

    private static final String VOLCENGINE_AGENT_PLAN_LOGO = "data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyBpZD0iX+WbvuWxgl8yIiBkYXRhLW5hbWU9IuWbvuWxgiAyIiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAzNTEuNTQgNzUiIHdpZHRoPSIxNTAiIGhlaWdodD0iMzciPgogIDxkZWZzPgogICAgPHN0eWxlPgogICAgICAuY2xzLTEgewogICAgICAgIGZpbGwtcnVsZTogZXZlbm9kZDsKICAgICAgfQoKICAgICAgLmNscy0xLCAuY2xzLTIgewogICAgICAgIGZpbGw6ICMxYzI2MzM7CiAgICAgIH0KCiAgICAgIC5jbHMtMyB7CiAgICAgICAgZmlsbDogIzAwZGNmZjsKICAgICAgfQoKICAgICAgLmNscy00IHsKICAgICAgICBmaWxsOiAjMDA2YWZmOwogICAgICB9CiAgICA8L3N0eWxlPgogIDwvZGVmcz4KICA8ZyBpZD0iX+WbvuWxgl80IiBkYXRhLW5hbWU9IuWbvuWxgiA0Ij4KICAgIDxnPgogICAgICA8Zz4KICAgICAgICA8cGF0aCBjbGFzcz0iY2xzLTEiIGQ9Ik0yMjMuOTQsMTcuMzhoLTUuNTdjLS41MiwwLS45NC40Mi0uOTQuOTR2NDQuNThoLTE0LjU3VjEwLjM0YzAtLjUyLS40Mi0uOTQtLjk0LS45NGgtNS42MmMtLjUyLDAtLjk0LjQyLS45NC45NHY1Mi41NWgtMTQuNThWMTguMzFjMC0uNTItLjQyLS45NC0uOTQtLjk0aC01LjU4Yy0uNTIsMC0uOTQuNDItLjk0Ljk0djUwLjcyYzAsLjcyLjU5LDEuMzEsMS4zMSwxLjMxaDQ4LjkyYy43MiwwLDEuMzEtLjU5LDEuMzEtMS4zMVYxOC4zMWMwLS41Mi0uNDItLjk0LS45NC0uOTRaIi8+CiAgICAgICAgPGc+CiAgICAgICAgICA8cmVjdCBjbGFzcz0iY2xzLTIiIHg9IjI3OC4zNSIgeT0iOS40MSIgd2lkdGg9IjcuNSIgaGVpZ2h0PSI2MC45NCIgcng9Ii45NCIgcnk9Ii45NCIvPgogICAgICAgICAgPHBhdGggY2xhc3M9ImNscy0xIiBkPSJNMjYzLjc3LDkuNDFoLTI3Ljg5Yy0uNTIsMC0uOTQuNDItLjk0Ljk0djUuNThjMCwuNTIuNDIuOTQuOTQuOTRoMjEuMDRjLjE4LDAsLjMzLjE1LjMzLjMzdjguMDJjMCwuMTgtLjE1LjMzLS4zMy4zM2gtMjAuMjFjLS40OSwwLS45LjM4LS45NC44N2wtMS40NCwyMS4xOGMtLjAyLjM2LjI1LjY3LjYxLjcuMDEsMCwuMDMsMCwuMDQsMGgyMS45M2MuMTgsMCwuMzMuMTUuMzMuMzN2MTEuNDljMCwxLjQ1LTEuMTcsMi42My0yLjYyLDIuNjNoLTE2LjEzYy0uNTIsMC0uOTQuNDItLjk0Ljk0djUuNzJjMCwuNTIuNDIuOTQuOTQuOTRoMTguMzNjNC4zNSwwLDcuODctMy41Myw3Ljg3LTcuODh2LTIwLjY5YzAtLjUyLS40Mi0uOTQtLjk0LS45NGgtMjEuMzJjLS4zNiwwLS42Ni0uMjktLjY2LS42NiwwLS4wMiwwLS4wNCwwLS4wNWwuNTUtNi41M2MuMDMtLjM0LjMxLS42LjY1LS42aDIwLjc3Yy41MiwwLC45NC0uNDIuOTQtLjk0VjEwLjM0YzAtLjUyLS40Mi0uOTQtLjk0LS45NFoiLz4KICAgICAgICA8L2c+CiAgICAgICAgPGc+CiAgICAgICAgICA8cGF0aCBjbGFzcz0iY2xzLTEiIGQ9Ik0xNjMuMzgsNjIuNTJjLTguOTItLjU3LTIzLjk1LTEyLjQyLTIzLjY1LTMwLjA4VjEwLjM0YzAtLjUyLS40Mi0uOTQtLjk0LS45NGgtNS43OWMtLjUyLDAtLjk0LjQyLS45NC45NHYyMi4xYy4zMSwxNy42Ni0xNC44LDI5LjUxLTIzLjcyLDMwLjA4LS4zNy4wMi0uNjYuMzItLjY2LjY5djYuNDNjMCwuNC4zMi43Mi43MS43LDYuNjctLjQxLDE0LjI1LTQuMDksMTkuOTYtOS43NCwyLjU0LTIuNTEsNS4zMy01Ljk5LDcuNS0xMC41NSwyLjE3LDQuNTYsNC45NSw4LjAzLDcuNSwxMC41NSw1LjcyLDUuNjUsMTMuMjksOS4zMywxOS45Niw5Ljc0LjM5LjAyLjcxLS4zLjcxLS43di02LjQzYzAtLjM3LS4yOS0uNjctLjY2LS42OVoiLz4KICAgICAgICAgIDxwYXRoIGNsYXNzPSJjbHMtMSIgZD0iTTE1MS41MiwzOC45Nmg3LjA3Yy4zNSwwLC42NC0uMjcuNjktLjYzLDEuMzctMTEuMDMsMS43Ni0xNy45OSwxLjg3LTIxLjA2LjAxLS40LS4zLS43My0uNjktLjczaC03LjAyYy0uMzcsMC0uNjguMy0uNjkuNjgtLjExLDMuMDMtLjUxLDkuOTktMS45LDIwLjkzLS4wNS40My4yNy44MS42OC44MVoiLz4KICAgICAgICAgIDxwYXRoIGNsYXNzPSJjbHMtMSIgZD0iTTExMi44MiwzOS4zaDcuM2MuNDMsMCwuNzYtLjM5LjcxLS44Mi0xLjQ0LTExLjExLTEuODUtMTguMTctMS45Ny0yMS4yNS0uMDEtLjM5LS4zMy0uNjktLjcxLS42OWgtNy4yNWMtLjQsMC0uNzMuMzQtLjcxLjc1LjExLDMuMTEuNTEsMTAuMTgsMS45MywyMS4zOC4wNS4zNi4zNS42NC43MS42NFoiLz4KICAgICAgICA8L2c+CiAgICAgICAgPGc+CiAgICAgICAgICA8cGF0aCBjbGFzcz0iY2xzLTEiIGQ9Ik0yOTcuNTksMTYuNDdoMy40N3YuNzdjMCwuMi4xNS4zNy4zNC4zN2g2LjQ4Yy4xOSwwLC4zNC0uMTcuMzQtLjM3di0uNzdoNS41di43N2MwLC4yLjE1LjM3LjM0LjM3aDYuNDhjLjE5LDAsLjM0LS4xNy4zNC0uMzd2LS43N2gzLjYzYy4xOSwwLC4zNC0uMTUuMzQtLjM0di00Ljg0YzAtLjE5LS4xNS0uMzQtLjM0LS4zNGgtMy42M3YtMS4xOGMwLS4yLS4xNS0uMzctLjM0LS4zN2gtNi40OGMtLjE5LDAtLjM0LjE3LS4zNC4zN3YxLjE4aC01LjV2LTEuMThjMC0uMi0uMTUtLjM3LS4zNC0uMzdoLTYuNDhjLS4xOSwwLS4zNC4xNy0uMzQuMzd2MS4xOGgtMy40N2MtLjE5LDAtLjM0LjE1LS4zNC4zNHY0Ljg0YzAsLjE5LjE1LjM0LjM0LjM0WiIvPgogICAgICAgICAgPHBhdGggY2xhc3M9ImNscy0xIiBkPSJNMjk3LjIxLDI3Ljg1Yy45NS0uMDksMS44NS0uMjksMi42OS0uNTl2OC44MWMwLC4zNy4zLjY3LjY3LjY3aDE1LjA3cy4wNi0uMDEuMDktLjAydjMuMTdjMCwuMTguMTUuMzMuMzMuMzNoNS43MmMxLjUyLDAsMi43NC0xLjIzLDIuNzQtMi43NXYtMTcuMjJjMC0uMzctLjMtLjY3LS42Ny0uNjdoLTE3LjU5Yy4wOC0uNDIuMTUtLjg2LjE5LTEuMzEuMDItLjItLjE0LS4zNy0uMzQtLjM3aC01LjY0Yy0uMTcsMC0uMzEuMTMtLjM0LjMtLjM4LDIuOTMtMi4wMSwzLjI3LTIuOTgsMy4zNS0uMTcuMDEtLjMyLjE3LS4zMi4zNHY1LjZjMCwuMi4xNy4zNi4zNy4zNFpNMzExLjAzLDMyLjM2YzAsLjE5LS4xNS4zNC0uMzQuMzRoLTUuMTZjLS4xOSwwLS4zNC0uMTUtLjM0LS4zNHYtMi4zMWMwLS4xOS4xNS0uMzQuMzQtLjM0aDUuMTZjLjE5LDAsLjM0LjE1LjM0LjM0djIuMzFaTTMxNy44OCwyNC4zNmMuMTgsMCwuMzMuMTUuMzMuMzN2OS42NmMwLC4xOC0uMTUuMzMtLjMzLjMzaC0xLjU2di04LjM2YzAtLjM3LS4zLS42Ny0uNjctLjY3aC0xMi45MmMuNDgtLjM5LjkzLS44MiwxLjMzLTEuM2gxMy44MloiLz4KICAgICAgICAgIDxwYXRoIGNsYXNzPSJjbHMtMSIgZD0iTTMyNS42NywzMy40NGwuMDcsNS45NmMwLC4yLjE3LjM3LjM3LjM2LDQuODYtLjEsOC43NC0uOTksMTEuOTMtMi42NSwzLjQzLDEuODgsNy42MiwyLjksMTIuNDcsMi44OS4xOSwwLC4zNS0uMTcuMzUtLjM3di01Ljg0YzAtLjItLjE1LS4zNS0uMzQtLjM1LTIuMzIsMC00LjYxLS4zMS02LjcyLTEsNC4xNy01LjAxLDQuNDgtMTEuMDcsNC40NS0xNC4yM2gyLjhjLjE5LDAsLjM0LS4xNS4zNC0uMzR2LTUuMjZjMC0uMTktLjE1LS4zNC0uMzQtLjM0aC0xNC4wN2MuMTgtLjgxLjMtMS42NS4zMi0yLjUyLDAtLjE5LS4xNS0uMzUtLjM0LS4zNWgtNS44M2MtLjE4LDAtLjMyLjE0LS4zMy4zMi0uMTQsMi44My0yLjE2LDUuMDYtNC44Miw1LjQyLS4xNy4wMi0uMy4xNi0uMy4zM3Y1Ljg0YzAsLjIuMTYuMzUuMzYuMzQsMy4wMS0uMiw1LjY5LTEuNDcsNy42OC0zLjQ2aDhjLjAzLDIuNTQtLjIsNy4wOS0zLjMzLDEwLjQxLS4xMS4xMi0uMjQuMjItLjM1LjMzLTEuNy0xLjcxLTMtMy45Ny0zLjctNi44OS0uMDQtLjE2LS4xNy0uMjctLjMzLS4yN2gtNS44MWMtLjIyLDAtLjM4LjItLjM1LjQyLjY4LDMuOTEsMi4xNyw3LjI5LDQuMzcsMTAuMDMtMS43Ni41LTMuNzguNzgtNi4xOS44My0uMTksMC0uMzUuMTYtLjM1LjM2WiIvPgogICAgICAgICAgPHBhdGggY2xhc3M9ImNscy0xIiBkPSJNMzUxLjIxLDU2LjE4aC0yMi44di0yLjU4aDIwLjAzYy4xOCwwLC4zMy0uMTUuMzMtLjMzdi00LjIyYzAtLjE4LS4xNS0uMzMtLjMzLS4zM2gtMjAuMDN2LTIuNjRoMjAuODZjLjE4LDAsLjMzLS4xNS4zMy0uMzN2LTQuMjJjMC0uMTgtLjE1LS4zMy0uMzMtLjMzaC01MC4xNWMtLjE4LDAtLjMzLjE1LS4zMy4zM3Y0LjIyYzAsLjE4LjE1LjMzLjMzLjMzaDIwLjg2djIuNjRoLTIwLjAzYy0uMTgsMC0uMzMuMTUtLjMzLjMzdjQuMjJjMCwuMTguMTUuMzMuMzMuMzNoMjAuMDN2Mi41OGgtMjIuOGMtLjE4LDAtLjMzLjE1LS4zMy4zM3Y0LjIyYzAsLjE4LjE1LjMzLjMzLjMzaDIyLjh2My4zOGMwLC4zNy0uMjguNjctLjY1LjY3aC01Ljg1Yy0uMzYsMC0uNjYuMjktLjY2LjY2djMuOTFjMCwuMzYuMjkuNjYuNjYuNjZoOS43YzIuOSwwLDUuMjItMi4zNSw1LjIyLTUuMjV2LTQuMDJoMjIuOGMuMTgsMCwuMzMtLjE1LjMzLS4zM3YtNC4yMmMwLS4xOC0uMTUtLjMzLS4zMy0uMzNaIi8+CiAgICAgICAgPC9nPgogICAgICA8L2c+CiAgICAgIDxnPgogICAgICAgIDxwYXRoIGNsYXNzPSJjbHMtMyIgZD0iTTM0LjgyLDI4LjkzbC0xNC45Nyw0Ni4wN2gzMi4xNmwtMTQuOTctNDYuMDdjLS4zNS0xLjA4LTEuODgtMS4wOC0yLjIzLDBaIi8+CiAgICAgICAgPHBhdGggY2xhc3M9ImNscy0zIiBkPSJNMTIuODMsNDIuMzZjLS4zNS0xLjA4LTEuODgtMS4wOC0yLjIzLDBMMCw3NWg5LjQybDcuMDEtMjEuNTctMy41OS0xMS4wNloiLz4KICAgICAgICA8cGF0aCBjbGFzcz0iY2xzLTQiIGQ9Ik0yOS41MiwyMGMtLjM1LTEuMDgtMS44OC0xLjA4LTIuMjMsMGwtMTcuODcsNTVoMTAuNDNsMTMuNzctNDIuMzctNC4xLTEyLjYzWiIvPgogICAgICAgIDxwYXRoIGNsYXNzPSJjbHMtMyIgZD0iTTcxLjczLDM2LjQzYy0uMzUtMS4wOC0xLjg4LTEuMDgtMi4yMywwbC0zLjU1LDEwLjk0LDguOTgsMjcuNjNoOS4zNGwtMTIuNTMtMzguNTdaIi8+CiAgICAgICAgPHBhdGggY2xhc3M9ImNscy00IiBkPSJNNTAuODIuODFjLS4zNS0xLjA4LTEuODgtMS4wOC0yLjIzLDBsLTEwLjM0LDMxLjgyLDEzLjc3LDQyLjM3aDIyLjlMNTAuODIuODFaIi8+CiAgICAgIDwvZz4KICAgIDwvZz4KICA8L2c+Cjwvc3ZnPg==";

    private final ModelProviderCacheUtil cacheUtil;
    private final ModelService modelService;
    private final ObjectMapper objectMapper;

    @Override
    public ModelProvider create(ModelProviderRequest request) {
        // 1. 构建实体并保存
        ModelProvider provider = new ModelProvider();
        provider.setName(request.getName());
        provider.setType(request.getType());
        provider.setApiKey(request.getApiKey());
        provider.setBaseUrl(request.getBaseUrl());
        provider.setModelsEndpoint(request.getModelsEndpoint());
        provider.setHeadersJson(request.getHeadersJson());
        provider.setExtraJson(request.getExtraJson());
        provider.setConfig(buildProviderConfig(request));
        provider.setStatus(CommonStatus.ACTIVE);
        save(provider);

        // 2. 同步缓存
        cacheUtil.cacheProvider(provider);
        syncAllProvidersCache();
        return provider;
    }

    @Override
    public ModelProvider update(ModelProviderRequest request) {
        // 1. 校验存在性
        ModelProvider provider = getById(request.getId());
        if (provider == null) {
            throw new BizException(ErrorCode.MODEL_PROVIDER_NOT_FOUND);
        }
        // 2. 更新字段
        provider.setName(request.getName());
        provider.setType(request.getType());
        provider.setApiKey(request.getApiKey());
        provider.setBaseUrl(request.getBaseUrl());
        provider.setModelsEndpoint(request.getModelsEndpoint());
        provider.setHeadersJson(request.getHeadersJson());
        provider.setExtraJson(request.getExtraJson());
        provider.setConfig(buildProviderConfig(request));
        updateById(provider);

        // 3. 同步缓存
        cacheUtil.cacheProvider(provider);
        syncAllProvidersCache();
        return provider;
    }

    @Override
    public Page<ModelProvider> listPage(int pageNum, int pageSize) {
        return baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ModelProvider>().orderByDesc(ModelProvider::getCreateTime));
    }

    @Override
    public void deleteById(Long id) {
        if (!removeById(id)) {
            throw new BizException(ErrorCode.MODEL_PROVIDER_NOT_FOUND);
        }
        // 级联删除关联模型
        try {
            modelService.deleteByProviderId(id);
        } catch (Exception e) {
            log.warn("[ModelProvider] 级联删除模型失败, providerId={}, error={}", id, e.getMessage());
        }
        // 同步缓存
        cacheUtil.evictProvider(id);
        syncAllProvidersCache();
    }

    @Override
    public void updateStatus(Long id, String status) {
        ModelProvider provider = getById(id);
        if (provider == null) {
            throw new BizException(ErrorCode.MODEL_PROVIDER_NOT_FOUND);
        }
        provider.setStatus(CommonStatus.fromValue(status));
        updateById(provider);
        // 同步缓存
        cacheUtil.cacheProvider(provider);
        syncAllProvidersCache();
    }

    @Override
    public List<ModelProvider> listAllActive() {
        return list(new LambdaQueryWrapper<ModelProvider>()
                .eq(ModelProvider::getStatus, CommonStatus.ACTIVE)
                .orderByDesc(ModelProvider::getCreateTime));
    }

    @Override
    public List<ModelProviderPresetVO> listPresets() {
        return List.of(
                preset("openai", "OpenAI", "标准 OpenAI Chat Completions 接口预设", OPENAI_LOGO,
                        "https://api.openai.com/v1", "gpt-4o-mini", null,
                        "https://api.openai.com/v1/models", EMPTY_JSON, EMPTY_JSON),
                preset("deepseek", "DeepSeek", "DeepSeek OpenAI 兼容接口预设", DEEPSEEK_LOGO,
                        "https://api.deepseek.com", "deepseek-chat", null,
                        "https://api.deepseek.com/v1/models", EMPTY_JSON, EMPTY_JSON),
                preset("zhipuai", "智谱 BigModel", "GLM OpenAI 兼容接口预设", ZHIPU_LOGO,
                        "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", null,
                        "https://open.bigmodel.cn/api/paas/v4/models", EMPTY_JSON, EMPTY_JSON),
                preset("moonshotai-cn", "Moonshot CN", "Kimi 国内 OpenAI 兼容接口预设", MOONSHOT_LOGO,
                        "https://api.moonshot.cn/v1", "moonshot-v1-8k", null,
                        "https://api.moonshot.cn/v1/models", EMPTY_JSON, EMPTY_JSON),
                preset("minimax-cn", "MiniMax CN", "MiniMax 国内 OpenAI 兼容接口预设", MINIMAX_LOGO,
                        "https://api.minimaxi.com/v1", "", null,
                        "https://api.minimaxi.com/v1/models", EMPTY_JSON, EMPTY_JSON),
                preset("dashscope-compatible", "DashScope 兼容模式", "阿里百炼 OpenAI 兼容模式，原生能力仍建议使用 DashScope 类型", DASHSCOPE_LOGO,
                        "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", null,
                        "https://dashscope.aliyuncs.com/compatible-mode/v1/models", EMPTY_JSON, EMPTY_JSON),
                preset("siliconflow-cn", "SiliconFlow CN", "硅基流动国内 OpenAI 兼容接口预设", SILICONFLOW_LOGO,
                        "https://api.siliconflow.cn/v1", "", null,
                        "https://api.siliconflow.cn/v1/models?sub_type=chat", EMPTY_JSON, EMPTY_JSON),
                preset("openrouter", "OpenRouter", "OpenRouter 聚合平台 OpenAI 兼容接口预设，可按需补充 HTTP-Referer 与 X-Title", OPENROUTER_LOGO,
                        "https://openrouter.ai/api/v1", "", null,
                        "https://openrouter.ai/api/v1/models", "{\"HTTP-Referer\":\"\",\"X-Title\":\"LightBot\"}", EMPTY_JSON),
                preset("modelscope", "ModelScope", "魔搭社区推理聚合 OpenAI 兼容接口预设", MODELSCOPE_LOGO,
                        "https://api-inference.modelscope.cn/v1", "", null,
                        "https://api-inference.modelscope.cn/v1/models", EMPTY_JSON, EMPTY_JSON),
                preset("zai", "Z.AI", "Z.AI OpenAI 兼容接口预设", ZAI_LOGO,
                        "https://api.z.ai/api/paas/v4", "", null,
                        "https://api.z.ai/api/paas/v4/models", EMPTY_JSON, EMPTY_JSON),
                preset("volcengine-agent-plan", "火山 AgentPlan", "火山引擎 Ark Coding OpenAI 兼容接口预设", VOLCENGINE_AGENT_PLAN_LOGO,
                        "https://ark.cn-beijing.volces.com/api/coding/v3", "ark-code-latest", "/chat/completions",
                        "https://ark.cn-beijing.volces.com/api/coding/v3/models", EMPTY_JSON, EMPTY_JSON)
        );
    }

    /**
     * 构建 OpenAI 兼容提供商预设
     *
     * @param code 预设标识
     * @param name 预设名称
     * @param description 预设说明
     * @param logo Logo 地址
     * @param baseUrl 基础地址
     * @param defaultModelId 默认模型ID
     * @param completionsPath Chat Completions 请求路径
     * @param modelsEndpoint 模型列表地址
     * @param headersJson 额外请求头 JSON
     * @param extraJson 扩展配置 JSON
     * @return 提供商预设
     */
    private ModelProviderPresetVO preset(String code, String name, String description, String logo, String baseUrl,
                                         String defaultModelId, String completionsPath, String modelsEndpoint,
                                         String headersJson, String extraJson) {
        return ModelProviderPresetVO.builder()
                .code(code)
                .name(name)
                .description(description)
                .type(ModelProviderType.OPENAI)
                .logo(logo)
                .baseUrl(baseUrl)
                .defaultModelId(defaultModelId)
                .completionsPath(completionsPath)
                .modelsEndpoint(modelsEndpoint)
                .headersJson(headersJson)
                .extraJson(extraJson)
                .build();
    }

    /**
     * 构建提供商配置 JSON
     *
     * @param request 提供商请求
     * @return 配置 JSON
     */
    private String buildProviderConfig(ModelProviderRequest request) {
        Map<String, Object> config = parseConfig(request.getConfig());
        String defaultModelId = request.getDefaultModelId();
        if (defaultModelId != null) {
            if (defaultModelId.isBlank()) {
                config.remove(ConfigKeys.Agent.MODEL_ID);
            } else {
                config.put(ConfigKeys.Agent.MODEL_ID, defaultModelId.trim());
            }
        }
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, e);
        }
    }

    /**
     * 解析配置 JSON
     *
     * @param config 配置 JSON
     * @return 配置 Map
     */
    private Map<String, Object> parseConfig(String config) {
        if (config == null || config.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(config, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[ModelProvider] 配置JSON解析失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 刷新全部提供商列表缓存
     */
    private void syncAllProvidersCache() {
        List<ModelProvider> all = list(new LambdaQueryWrapper<ModelProvider>()
                .orderByDesc(ModelProvider::getCreateTime));
        cacheUtil.cacheAllProviders(all);
    }
}
