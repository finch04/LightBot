package com.lightbot.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务错误码枚举
 * <p>所有业务错误信息统一管理，禁止在代码中硬编码错误字符串</p>
 *
 * @author finch
 * @since 2026-05-19
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ========== 通用 ==========
    BAD_REQUEST(10000, "请求参数错误", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(10001, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(10002, "无权访问", HttpStatus.FORBIDDEN),
    NOT_FOUND(10003, "资源不存在", HttpStatus.NOT_FOUND),
    INTERNAL_ERROR(10004, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),

    // ========== 用户模块 ==========
    USER_NOT_FOUND(20001, "用户不存在", HttpStatus.UNAUTHORIZED),
    USERNAME_EXISTS(20002, "用户名已存在", HttpStatus.BAD_REQUEST),
    USERNAME_OR_PASSWORD_ERROR(20003, "用户名或密码错误", HttpStatus.BAD_REQUEST),
    ACCOUNT_DISABLED(20004, "账号已被禁用", HttpStatus.FORBIDDEN),

    // ========== Agent模块 ==========
    AGENT_NOT_FOUND(30000, "Agent不存在", HttpStatus.BAD_REQUEST),
    AGENT_NAME_EXISTS(30009, "Agent名称已存在", HttpStatus.BAD_REQUEST),
    AGENT_KNOWLEDGE_LIMIT(30003, "每个Agent最多绑定10个知识库", HttpStatus.BAD_REQUEST),
    AGENT_TOOL_LIMIT(30005, "每个Agent最多绑定10个额外工具", HttpStatus.BAD_REQUEST),
    AGENT_MCP_LIMIT(30006, "每个Agent最多绑定5个MCP Server", HttpStatus.BAD_REQUEST),
    AGENT_SUBAGENT_LIMIT(30007, "每个Agent最多绑定5个SubAgent", HttpStatus.BAD_REQUEST),
    AGENT_SKILL_LIMIT(30008, "每个Agent最多绑定10个Skill", HttpStatus.BAD_REQUEST),
    AVATAR_UNSUPPORTED_TYPE(30004, "头像文件格式不支持: %s", HttpStatus.BAD_REQUEST),
    AI_NO_PROVIDER(30001, "未配置模型提供商，请先添加一个模型提供商", HttpStatus.BAD_REQUEST),
    AI_GENERATE_FAILED(30002, "AI生成失败，请检查模型KEY配置", HttpStatus.INTERNAL_SERVER_ERROR),

    // ========== 会话模块 ==========
    SESSION_NOT_FOUND(30010, "会话不存在", HttpStatus.BAD_REQUEST),

    // ========== 模型提供商模块 ==========
    MODEL_PROVIDER_NOT_FOUND(40001, "模型提供商不存在", HttpStatus.BAD_REQUEST),
    MODEL_PROVIDER_CHECK_FAILED(40002, "模型连通性检查失败: %s", HttpStatus.BAD_REQUEST),

    // ========== 模型模块 ==========
    MODEL_NOT_FOUND(40003, "模型不存在", HttpStatus.BAD_REQUEST),
    MODEL_ALREADY_EXISTS(40004, "该提供商下已存在相同标识的模型", HttpStatus.BAD_REQUEST),

    // ========== 知识库模块 ==========
    KNOWLEDGE_NOT_FOUND(50001, "知识库不存在", HttpStatus.BAD_REQUEST),
    KNOWLEDGE_NAME_EXISTS(50009, "知识库名称已存在", HttpStatus.BAD_REQUEST),
    KNOWLEDGE_NO_PERMISSION(50002, "无权访问该知识库", HttpStatus.FORBIDDEN),
    KNOWLEDGE_ROLE_INSUFFICIENT(50003, "权限不足，需要%s及以上权限", HttpStatus.FORBIDDEN),
    KNOWLEDGE_MEMBER_EXISTS(50004, "该用户已是知识库成员", HttpStatus.BAD_REQUEST),
    KNOWLEDGE_MEMBER_NOT_FOUND(50005, "该用户不是知识库成员", HttpStatus.BAD_REQUEST),
    KNOWLEDGE_CREATOR_ROLE_IMMUTABLE(50006, "不能修改创建者角色", HttpStatus.BAD_REQUEST),
    KNOWLEDGE_CREATOR_CANNOT_REMOVE(50007, "不能移除创建者", HttpStatus.BAD_REQUEST),
    KNOWLEDGE_NO_DOCUMENT(50008, "知识库暂无已完成的文档", HttpStatus.BAD_REQUEST),

    // ========== 文档模块 ==========
    DOCUMENT_UNSUPPORTED_TYPE(60001, "不支持的文件类型，支持: md/txt/pdf/doc/docx/ppt/pptx/xls/xlsx/csv/html", HttpStatus.BAD_REQUEST),
    DOCUMENT_ALREADY_EXISTS(60002, "%s已是相同内容文件，无需重复上传", HttpStatus.BAD_REQUEST),
    DOCUMENT_NOT_FOUND(60003, "文档不存在", HttpStatus.BAD_REQUEST),
    DOCUMENT_READ_FAILED(60004, "读取文档内容失败", HttpStatus.INTERNAL_SERVER_ERROR),
    DOCUMENT_PARSE_FAILED(60005, "文档解析失败: %s", HttpStatus.INTERNAL_SERVER_ERROR),
    DOCUMENT_INVALID_STATUS(60006, "文档状态不允许此操作", HttpStatus.BAD_REQUEST),
    DOCUMENT_FILE_TOO_LARGE(60007, "文件大小超过100MB限制", HttpStatus.BAD_REQUEST),
    DOCUMENT_CHUNK_FAILED(60008, "文档分块失败", HttpStatus.INTERNAL_SERVER_ERROR),
    DOCUMENT_CHUNKS_TOO_SHORT(60009, "按照此分片策略会导致每个分片过短，请重新选择策略!", HttpStatus.BAD_REQUEST),
    DOCUMENT_CONTENT_SUSPICIOUS(60010, "文档内容安全扫描未通过: %s", HttpStatus.BAD_REQUEST),
    CHAT_FILE_CONTENT_SUSPICIOUS(60011, "上传的文件中包含敏感信息", HttpStatus.BAD_REQUEST),
    DOCUMENT_DUPLICATE_WARNING(60012, "文档内容与已有文档相似度较高", HttpStatus.OK),
    DOCUMENT_URL_NO_CONTENT(60013, "未能从网页中提取有效正文，请尝试其他 URL 或上传 HTML 文件", HttpStatus.BAD_REQUEST),
    DOCUMENT_EDIT_CONFLICT(60014, "文档已被其他人修改，请刷新后重试", HttpStatus.CONFLICT),
    DOCUMENT_EDIT_UNSUPPORTED(60015, "该文件类型暂不支持在线编辑", HttpStatus.BAD_REQUEST),

    // ========== 任务模块 ==========
    TASK_NOT_FOUND(61001, "任务不存在", HttpStatus.BAD_REQUEST),
    TASK_CANCEL_FAILED(61002, "任务无法取消", HttpStatus.BAD_REQUEST),
    TASK_DELETE_FAILED(61003, "任务无法删除（仅已完成/失败/已取消的任务可删除）", HttpStatus.BAD_REQUEST),

    // ========== RAG 模块 ==========
    RAG_KNOWLEDGE_NOT_FOUND(70001, "知识库不存在", HttpStatus.BAD_REQUEST),

    // ========== 知识图谱模块 ==========
    GRAPH_NEO4J_UNAVAILABLE(71001, "图数据库连接不可用", HttpStatus.SERVICE_UNAVAILABLE),
    GRAPH_EXTRACTION_FAILED(71002, "图谱抽取失败: %s", HttpStatus.INTERNAL_SERVER_ERROR),
    GRAPH_IMPORT_FAILED(71003, "图谱导入失败: %s", HttpStatus.INTERNAL_SERVER_ERROR),
    GRAPH_NODE_NOT_FOUND(71004, "图谱节点不存在", HttpStatus.BAD_REQUEST),
    GRAPH_EDGE_NOT_FOUND(71005, "图谱关系不存在", HttpStatus.BAD_REQUEST),
    GRAPH_NOT_ENABLED(71006, "知识库未启用图谱功能", HttpStatus.BAD_REQUEST),
    GRAPH_JSONL_TOO_LARGE(71007, "JSONL文件大小超过5MB限制", HttpStatus.BAD_REQUEST),
    GRAPH_JSONL_INVALID_FORMAT(71008, "JSONL文件格式错误: 每行必须是合法JSON且包含head/relation/tail字段", HttpStatus.BAD_REQUEST),
    GRAPH_JSONL_INVALID_TYPE(71009, "仅支持 .jsonl 格式文件", HttpStatus.BAD_REQUEST),

    // ========== Tool模块 ==========
    TOOL_NOT_FOUND(91001, "工具不存在", HttpStatus.BAD_REQUEST),
    TOOL_NAME_EXISTS(91002, "工具标识已存在", HttpStatus.BAD_REQUEST),
    TOOL_NOT_EDITABLE(91003, "知识库工具由系统自动管理，不可编辑", HttpStatus.BAD_REQUEST),
    TOOL_NOT_DELETABLE(91004, "知识库工具由系统自动管理，不可删除", HttpStatus.BAD_REQUEST),

    // ========== SubAgent模块 ==========
    SUBAGENT_NOT_FOUND(91101, "SubAgent不存在", HttpStatus.BAD_REQUEST),
    SUBAGENT_NAME_EXISTS(91102, "SubAgent标识已存在", HttpStatus.BAD_REQUEST),

    // ========== Skill模块 ==========
    SKILL_NOT_FOUND(92001, "Skill不存在", HttpStatus.BAD_REQUEST),
    SKILL_NAME_EXISTS(92002, "Skill名称已存在", HttpStatus.BAD_REQUEST),
    SKILL_IMPORT_FAILED(92003, "Skill导入失败: %s", HttpStatus.BAD_REQUEST),
    SKILL_SLUG_CONFLICT(92004, "Skill标识冲突: %s", HttpStatus.BAD_REQUEST),
    SKILL_DEPENDENCY_CYCLE(92005, "Skill依赖存在循环", HttpStatus.BAD_REQUEST),
    SKILL_DEPENDENCY_NOT_FOUND(92006, "依赖的Skill不存在: %s", HttpStatus.BAD_REQUEST),
    SKILL_FILE_NOT_FOUND(92007, "Skill文件不存在", HttpStatus.NOT_FOUND),
    SKILL_ZIP_INVALID(92008, "ZIP包格式无效: %s", HttpStatus.BAD_REQUEST),
    SKILL_REMOTE_FETCH_FAILED(92012, "远程仓库获取失败: %s", HttpStatus.BAD_REQUEST),
    SKILL_BUILTIN_NOT_EDITABLE(92009, "内置Skill不可编辑", HttpStatus.BAD_REQUEST),
    SKILL_BUILTIN_NOT_DELETABLE(92010, "内置Skill不可删除", HttpStatus.BAD_REQUEST),
    SANDBOX_PATH_VIOLATION(92011, "路径安全校验失败: %s", HttpStatus.FORBIDDEN),
    SANDBOX_ENGINE_NOT_FOUND(92020, "不支持的编程语言: %s", HttpStatus.BAD_REQUEST),
    SANDBOX_EXEC_FAILED(92021, "代码执行失败: %s", HttpStatus.BAD_REQUEST),
    SANDBOX_COMPILE_ERROR(92022, "代码编译错误: %s", HttpStatus.BAD_REQUEST),
    SANDBOX_TIMEOUT(92023, "代码执行超时（%sms）", HttpStatus.BAD_REQUEST),
    SANDBOX_SECURITY_VIOLATION(92024, "代码安全校验未通过: %s", HttpStatus.FORBIDDEN),

    // ========== MCP模块 ==========
    MCP_SERVER_NOT_FOUND(90001, "MCP Server不存在", HttpStatus.BAD_REQUEST),
    MCP_SERVER_NAME_EXISTS(90005, "MCP Server名称已存在", HttpStatus.BAD_REQUEST),
    MCP_CONNECTION_FAILED(90002, "MCP Server连接失败", HttpStatus.BAD_REQUEST),
    MCP_CONFIG_ERROR(90003, "MCP Server配置错误", HttpStatus.BAD_REQUEST),
    MCP_TOOLS_FETCH_FAILED(90004, "MCP获取工具失败", HttpStatus.BAD_REQUEST),

    // ========== LLM调用链模块 ==========
    LLM_TRACE_NOT_FOUND(93001, "调用链记录不存在", HttpStatus.BAD_REQUEST),

    // ========== 文件存储 ==========
    FILE_UPLOAD_FAILED(80001, "文件上传失败", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_DOWNLOAD_FAILED(80002, "文件下载失败", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_URL_FAILED(80003, "获取文件URL失败", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_TOO_LARGE_FOR_MEMORY(80004, "文件过大，无法加载到内存，请使用流式下载", HttpStatus.BAD_REQUEST),

    // ========== Prompt模块 ==========
    PROMPT_NOT_FOUND(100001, "Prompt不存在", HttpStatus.BAD_REQUEST),
    PROMPT_KEY_EXISTS(100002, "Prompt Key已存在", HttpStatus.BAD_REQUEST),
    PROMPT_VERSION_NOT_FOUND(100003, "Prompt版本不存在", HttpStatus.BAD_REQUEST),
    PROMPT_VERSION_EXISTS(100004, "该版本已存在正式发布，不可重复发布", HttpStatus.BAD_REQUEST),

    // ========== 评测集模块 ==========
    EVAL_DATASET_NOT_FOUND(100101, "评测集不存在", HttpStatus.BAD_REQUEST),
    EVAL_DATASET_NAME_EXISTS(100103, "评测集名称已存在", HttpStatus.BAD_REQUEST),
    EVAL_DATASET_VERSION_NOT_FOUND(100102, "评测集版本不存在", HttpStatus.BAD_REQUEST),
    EVAL_DATASET_EMPTY(100104, "数据集暂无数据项，请先添加数据项再创建版本", HttpStatus.BAD_REQUEST),

    // ========== 评估器模块 ==========
    EVAL_EVALUATOR_NOT_FOUND(100201, "评估器不存在", HttpStatus.BAD_REQUEST),
    EVAL_EVALUATOR_NAME_EXISTS(100204, "评估器名称已存在", HttpStatus.BAD_REQUEST),
    EVAL_EVALUATOR_VERSION_NOT_FOUND(100202, "评估器版本不存在", HttpStatus.BAD_REQUEST),
    EVAL_EVALUATOR_TEMPLATE_NOT_FOUND(100203, "评估器模板不存在", HttpStatus.BAD_REQUEST),

    // ========== 实验模块 ==========
    EVAL_EXPERIMENT_NOT_FOUND(100301, "实验不存在", HttpStatus.BAD_REQUEST),
    EVAL_EXPERIMENT_STATUS_INVALID(100302, "实验状态不允许此操作", HttpStatus.BAD_REQUEST),
    EVAL_EXPERIMENT_LLM_CALL_FAILED(100303, "LLM调用失败: %s", HttpStatus.INTERNAL_SERVER_ERROR),
    EVAL_EXPERIMENT_SCORE_PARSE_FAILED(100304, "评分结果解析失败", HttpStatus.INTERNAL_SERVER_ERROR),
    EVAL_EXPERIMENT_EVALUATOR_LIMIT(100305, "每个实验最多添加5个评估器", HttpStatus.BAD_REQUEST);

    /** 业务错误码 */
    private final int code;

    /** 错误信息 */
    private final String message;

    /** HTTP状态码 */
    private final HttpStatus httpStatus;
}
