package com.lightbot.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lightbot.entity.ModelProvider;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ModelProviderType;
import com.lightbot.mapper.ModelProviderMapper;
import com.lightbot.util.ModelProviderCacheUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 启动时初始化默认模型提供商
 * <p>检测数据库中是否已存在对应类型的提供商，不存在则新建（状态为禁用，apiKey 留空待用户填写）</p>
 *
 * @author finch
 * @since 2026-05-26
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DefaultProviderInitializer implements ApplicationRunner {

    private final ModelProviderMapper modelProviderMapper;
    private final ModelProviderCacheUtil cacheUtil;

    /** 默认提供商配置：type -> (name, baseUrl) */
    private static final Map<ModelProviderType, String[]> DEFAULT_PROVIDERS = Map.of(
            ModelProviderType.OPENAI, new String[]{"OpenAI", "https://api.openai.com"},
            ModelProviderType.DASHSCOPE, new String[]{"通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1"},
            ModelProviderType.MIMO, new String[]{"小米MiMo", "https://api.xiaomimimo.com/v1"}
    );

    @Override
    public void run(ApplicationArguments args) {
        boolean created = false;
        for (Map.Entry<ModelProviderType, String[]> entry : DEFAULT_PROVIDERS.entrySet()) {
            ModelProviderType type = entry.getKey();
            String[] config = entry.getValue();
            if (!existsByType(type)) {
                createDefault(type, config[0], config[1]);
                created = true;
            }
        }
        // 有新增时刷新全部提供商缓存
        if (created) {
            syncAllProvidersCache();
        }
    }

    /**
     * 检查数据库中是否已存在指定类型的提供商
     */
    private boolean existsByType(ModelProviderType type) {
        return modelProviderMapper.selectCount(
                new LambdaQueryWrapper<ModelProvider>().eq(ModelProvider::getType, type)) > 0;
    }

    /**
     * 创建默认提供商（状态禁用，apiKey 留空）
     */
    private void createDefault(ModelProviderType type, String name, String baseUrl) {
        ModelProvider provider = new ModelProvider();
        provider.setName(name);
        provider.setType(type);
        provider.setBaseUrl(baseUrl);
        provider.setStatus(CommonStatus.DISABLED);
        modelProviderMapper.insert(provider);
        log.info("[DefaultProvider] 创建默认提供商: type={}, name={}, id={}", type, name, provider.getId());
    }

    /**
     * 刷新全部提供商列表缓存
     */
    private void syncAllProvidersCache() {
        List<ModelProvider> all = modelProviderMapper.selectList(
                new LambdaQueryWrapper<ModelProvider>().orderByDesc(ModelProvider::getCreateTime));
        cacheUtil.cacheAllProviders(all);
    }
}
