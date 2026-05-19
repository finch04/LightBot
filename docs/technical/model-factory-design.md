# ModelFactory 动态模型创建设计文档

## 背景

LightBot 需要支持多个 AI 模型提供商（DashScope、OpenAI、DeepSeek、Ollama 等）。早期方案通过 Spring 自动配置注入 `ChatModel` Bean，但多个 starter 共存时会产生 Bean 冲突。

参考 [spring-ai-alibaba-admin](https://github.com/spring-ai-alibaba/spring-ai-alibaba-admin) 的 `ModelFactory` 模式，采用运行时动态创建 ChatModel 的方案。

## 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                       Agent Config (JSONB)                   │
│  {"providerId": 123, "modelId": "qwen-plus", "temp": 0.7}   │
└──────────────────────────┬──────────────────────────────────┘
                           │ providerId
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                     ModelProvider (DB)                        │
│  id=123, type=DASHSCOPE, apiKey=sk-xxx, baseUrl=...          │
└──────────────────────────┬──────────────────────────────────┘
                           │ type
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                       ModelFactory                           │
│  ┌─────────────────────┐  ┌─────────────────────┐           │
│  │ DashScopeModelHandler│  │  OpenAIModelHandler │           │
│  │  createChatModel()   │  │  createChatModel()  │           │
│  │  buildChatOptions()  │  │  buildChatOptions() │           │
│  │  getConfigFields()   │  │  getConfigFields()  │           │
│  └─────────────────────┘  └─────────────────────┘           │
│                                                              │
│  ConcurrentHashMap<Long, ChatModel> cache                    │
└─────────────────────────────────────────────────────────────┘
```

## 核心组件

### ModelProviderHandler（接口）

每个模型提供商实现此接口，职责：
- `getProviderType()` — 返回提供商类型枚举
- `createChatModel(ModelProvider)` — 根据凭证创建 ChatModel 实例
- `buildChatOptions(Map<String, Object>)` — 从 Agent config 构建 ChatOptions
- `getConfigFields()` — 返回前端动态表单字段定义

### ModelFactory（工厂）

- 按 `ModelProviderType` 注册所有 `ModelProviderHandler`
- `getChatModel(providerId)` — 查缓存 → 查 DB → 创建 → 缓存
- `buildChatOptions(providerId, config)` — 路由到对应 handler
- `getConfigFields(providerId)` — 获取配置字段定义
- `invalidateCache(providerId)` — 凭证变更时清除缓存

### 数据流

```
用户请求 → ChatServiceImpl
  ├── 解析 Agent.config → 获取 providerId
  ├── ModelFactory.getChatModel(providerId)
  │     ├── 缓存命中 → 直接返回
  │     └── 缓存未命中 → 查 ModelProvider → handler.createChatModel() → 缓存
  ├── ModelFactory.buildChatOptions(providerId, config)
  │     └── handler.buildChatOptions(config) → ChatOptions
  └── chatModel.call(new Prompt(messages, options))
```

## Agent Config JSONB 结构

```json
{
  "providerId": 1234567890,
  "modelId": "qwen-plus",
  "temperature": 0.7,
  "topP": 0.9,
  "maxTokens": 2048,
  "repetitionPenalty": 1.0
}
```

- `providerId` — 关联 `model_provider.id`，决定使用哪个提供商
- 其余字段 — 模型参数，通过 `getConfigFields()` 定义，前端动态渲染

## 扩展新提供商

1. 创建 `XxxModelHandler implements ModelProviderHandler`
2. 添加 `@Component` 注解
3. 实现四个方法
4. 在 `ModelProviderType` 枚举中添加新类型
5. 前端 `ModelProviderManage.vue` 的类型下拉自动包含新类型

```java
@Component
public class DeepSeekModelHandler implements ModelProviderHandler {

    @Override
    public ModelProviderType getProviderType() {
        return ModelProviderType.DEEPSEEK;
    }

    @Override
    public ChatModel createChatModel(ModelProvider provider) {
        // DeepSeek 兼容 OpenAI API
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(provider.getApiKey())
                .baseUrl(provider.getBaseUrl())
                .build();
        return OpenAiChatModel.builder().openAiApi(api).build();
    }

    @Override
    public ChatOptions buildChatOptions(Map<String, Object> config) {
        return OpenAiChatOptions.builder()
                .model(config.get("modelId").toString())
                .temperature(toDouble(config.get("temperature")))
                .build();
    }

    @Override
    public List<ConfigField> getConfigFields() {
        return List.of(/* ... */);
    }
}
```

## 缓存策略

- ChatModel 按 `providerId` 缓存在 `ConcurrentHashMap` 中
- 缓存生命周期：应用启动到关闭
- 凭证变更时调用 `invalidateCache(providerId)` 清除
- ChatOptions 每次调用时构建，不缓存

## 与旧方案对比

| 维度 | 旧方案（Spring Bean 注入） | 新方案（ModelFactory） |
|------|---------------------------|----------------------|
| ChatModel 来源 | 自动配置 Bean | 运行时动态创建 |
| 多提供商支持 | @Qualifier 硬编码 | 按 providerId 路由 |
| Bean 冲突 | 需要排除自动配置 | 无冲突 |
| 凭证管理 | application.yml | model_provider 表 |
| 扩展性 | 修改配置文件 | 实现 Handler 接口 |
