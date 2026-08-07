package com.lightbot.contract;

import com.lightbot.controller.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Controller 一级路由契约。
 *
 * <p>模块迁移期间 Controller 仍留在 server 模块。该测试防止移动类或调整包结构时
 * 丢失既有 REST 入口；不创建 Spring 上下文，避免依赖数据库和外部中间件。</p>
 */
class ControllerRouteContractTest {

    private static final Map<Class<?>, String> EXPECTED_BASE_PATHS = Map.ofEntries(
            Map.entry(AdminController.class, "/api/admin"),
            Map.entry(AgentController.class, "/api/agents"),
            Map.entry(AgentWorkflowController.class, "/api/agents/{agentId}/workflow"),
            Map.entry(ApiKeyController.class, "/api/api-keys"),
            Map.entry(AskDatasetController.class, "/api/ask-data"),
            Map.entry(AuthController.class, "/api/auth"),
            Map.entry(ChatController.class, "/api/chat"),
            Map.entry(ChatSessionController.class, "/api/chat/sessions"),
            Map.entry(DashboardController.class, "/api/dashboard"),
            Map.entry(DataModelCategoryController.class, "/api/data-model-categories"),
            Map.entry(DataModelController.class, "/api/data-models"),
            Map.entry(DataPoolAttachmentController.class, "/api/data-pools/attachments"),
            Map.entry(DataPoolController.class, "/api/data-pools/{modelId}"),
            Map.entry(DocumentEditController.class, "/api/documents"),
            Map.entry(EnumController.class, "/api/enums"),
            Map.entry(EvalDatasetController.class, "/api/eval/datasets"),
            Map.entry(EvalEvaluatorController.class, "/api/eval/evaluators"),
            Map.entry(EvalExperimentController.class, "/api/eval/experiments"),
            Map.entry(KnowledgeController.class, "/api/knowledge"),
            Map.entry(KnowledgeDocController.class, "/api/knowledge"),
            Map.entry(KnowledgeEvalController.class, "/api/knowledge/{knowledgeId}/eval"),
            Map.entry(KnowledgeGraphController.class, "/api/knowledge"),
            Map.entry(KnowledgeQAPairController.class, "/api/knowledge"),
            Map.entry(KnowledgeRagController.class, "/api/knowledge"),
            Map.entry(LandingController.class, "/api/landing"),
            Map.entry(LlmTraceController.class, "/api/observability"),
            Map.entry(McpServerController.class, "/api/mcp-servers"),
            Map.entry(ModelController.class, "/api/models"),
            Map.entry(ModelProviderController.class, "/api/model-providers"),
            Map.entry(OcrController.class, "/api/ocr"),
            Map.entry(OpenDataPoolController.class, "/api/open/v1/data-pools/records"),
            Map.entry(PromptController.class, "/api/prompts"),
            Map.entry(RegionController.class, "/api/regions"),
            Map.entry(SkillController.class, "/api/skills"),
            Map.entry(StandaloneGraphController.class, "/api/graph"),
            Map.entry(SubAgentController.class, "/api/subagents"),
            Map.entry(SystemConfigController.class, "/api/system-config"),
            Map.entry(TaskController.class, "/api/tasks"),
            Map.entry(TaskEventController.class, "/api/tasks"),
            Map.entry(ToolCallController.class, "/api/tool-calls"),
            Map.entry(ToolController.class, "/api/tools"),
            Map.entry(UserMemoryController.class, "/api/user/memories"),
            Map.entry(UserPreferenceController.class, "/api/user/preferences")
    );

    @Test
    void test_controllerBasePaths_shouldRemainCompatible() {
        assertEquals(42, EXPECTED_BASE_PATHS.size());

        EXPECTED_BASE_PATHS.forEach((controllerClass, expectedPath) -> {
            RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
            assertNotNull(requestMapping, () -> controllerClass.getSimpleName() + " 缺少 @RequestMapping");
            assertEquals(expectedPath, requestMapping.value()[0],
                    () -> controllerClass.getSimpleName() + " 的一级路由发生变化");
        });
    }
}
