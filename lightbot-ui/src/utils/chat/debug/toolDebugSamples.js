import { TOOL_RENDERERS, getToolDisplayName } from '@/components/toolRegistry'

/** 全部已注册工具名 */
export const REGISTERED_TOOL_NAMES = Object.keys(TOOL_RENDERERS)

/** 未注册工具（测试 BaseToolCall 兜底） */
export const UNREGISTERED_TOOL_NAME = '__unregistered__'

function sample(toolName, resultObj, extra = {}) {
  return {
    type: 'tool_result',
    toolName,
    displayName: getToolDisplayName(toolName),
    result: typeof resultObj === 'string' ? resultObj : JSON.stringify(resultObj),
    contentOffset: 0,
    ...extra,
  }
}

/** 各工具成功样例（字段对齐后端 Tool 输出 + 前端 *Result.vue 解析） */
export const TOOL_DEBUG_SAMPLES = {
  query_knowledge: sample('query_knowledge', {
    total: 2,
    qa_answer: null,
    results: [
      {
        result_type: 'chunk',
        document_id: '2056961707612393473',
        document_name: '产品手册.pdf',
        knowledge_id: '1001',
        content: '智元 是一个轻量级 Java AI Agent 平台，支持 RAG、工作流与多工具调用。',
        score: 0.92,
      },
      {
        result_type: 'qa_pair',
        question: '如何创建 Agent？',
        answer: '在 Agent 管理页点击新建，配置系统提示词与模型即可。',
        content: '在 Agent 管理页点击新建，配置系统提示词与模型即可。',
        knowledge_id: '1001',
        score: 0.88,
      },
    ],
  }),

  find_in_document: sample('find_in_document', {
    mode: 'open',
    document_id: '2056961707612393473',
    document_name: 'API 指南.md',
    total_lines: 120,
    start_line: 10,
    end_line: 15,
    has_more: true,
    next_offset: 16,
    content: '## 认证\n\n所有 API 请求需携带 Authorization 头。\n\n## 限流\n\n默认每分钟 60 次请求。',
  }),

  search_documents: sample('search_documents', {
    total: 2,
    documents: [
      { document_id: '1001', document_name: '部署指南.pdf', knowledge_name: '运维文档' },
      { document_id: '1002', document_name: 'FAQ.md', knowledge_name: '产品文档' },
    ],
  }),

  list_knowledge_bases: sample('list_knowledge_bases', {
    total: 2,
    knowledge_bases: [
      {
        id: '1001',
        name: '产品文档',
        description: '产品相关文档与 FAQ',
        document_count: 12,
        chunk_count: 320,
        total_tokens: 128000,
      },
      {
        id: '1002',
        name: '技术规范',
        description: '架构设计与 API 规范',
        document_count: 8,
        chunk_count: 180,
        total_tokens: 86000,
      },
    ],
  }),

  get_mindmap: sample('get_mindmap', {
    knowledge_id: '1001',
    knowledge_name: '产品架构',
    mindmap: {
      name: '智元 架构',
      children: [
        { name: 'lightbot-server', children: [{ name: 'Agent' }, { name: 'RAG' }] },
        { name: 'lightbot-ui', children: [{ name: 'Chat' }, { name: 'Workflow' }] },
      ],
    },
  }),

  open_kb_document: sample('open_kb_document', {
    document_id: '2056961707612393473',
    document_name: 'README.md',
    content: '# 智元\n\n轻量级 Java AI Agent 平台。\n\n## 功能\n\n- Agent 对话\n- 知识库 RAG\n- 工作流编排',
  }),

  web_search: sample('web_search', {
    query: 'Spring AI 框架',
    answer: 'Spring AI 是 Spring 生态下的 AI 应用框架，提供统一的 ChatClient 与 Tool 调用能力。',
    total: 1,
    results: [
      {
        title: 'Spring AI 官方文档',
        url: 'https://spring.io/projects/spring-ai',
        content: 'Spring AI 提供统一的 ChatClient API，支持 OpenAI、Azure、Ollama 等多种模型提供商。',
        score: 0.95,
      },
    ],
  }),

  calculator: sample('calculator', {
    expression: '123 × 456',
    operation: 'multiply',
    operands: [123, 456],
    result: '56088',
  }),

  pg_list_tables: sample('pg_list_tables', {
    tables: ['users', 'agent', 'knowledge', 'chat_session', 'message'],
    total: 5,
  }),

  pg_describe_table: sample('pg_describe_table', {
    table_name: 'agent',
    columns: [
      {
        column_name: 'id',
        data_type: 'bigint',
        is_nullable: false,
        column_default: null,
        column_comment: '主键ID',
      },
      {
        column_name: 'name',
        data_type: 'character varying(128)',
        is_nullable: false,
        column_default: null,
        column_comment: 'Agent 名称',
      },
      {
        column_name: 'status',
        data_type: 'character varying(20)',
        is_nullable: false,
        column_default: "'draft'",
        column_comment: '状态',
      },
    ],
    indexes: [
      {
        index_name: 'idx_agent_status',
        index_def: 'CREATE INDEX idx_agent_status ON agent USING btree (status)',
      },
    ],
  }),

  pg_query: sample('pg_query', {
    sql: 'SELECT id, name, status FROM agent LIMIT 2',
    columns: ['id', 'name', 'status'],
    rows: [
      ['2056961707612393473', '客服助手', 'published'],
      ['2056961707612393474', '代码助手', 'draft'],
    ],
    total_rows: 2,
    has_more: false,
    elapsed_ms: 12,
  }),

  ask_user: sample('ask_user', {
    question: '请选择您要查询的时间范围',
    options: ['最近 7 天', '最近 30 天', '自定义'],
    is_open_ended: false,
    wait_for_user: true,
    break_loop: true,
  }),

  write_todos: sample('write_todos', {
    success: true,
    todos: [
      { id: 'scope', content: '确认调研范围与交付形式', status: 'completed' },
      { id: 'research', content: '并行收集并核验资料', status: 'in_progress' },
      { id: 'report', content: '整理结论并输出报告', status: 'pending' },
    ],
  }),

  image_generation: sample('image_generation', {
    image_url: 'https://picsum.photos/seed/lightbot-debug/512/512',
    prompt: 'A minimalist robot assistant icon, flat design, soft blue background',
    file_path: 'sessions/debug/images/robot-icon.jpg',
  }),

  read_skill: sample('read_skill', {
    slug: 'code-review',
    displayName: '代码审查',
    activated: true,
    content: [
      '---',
      'description: 审查代码质量、安全性与可读性',
      'version: 1.0.0',
      'tool_dependencies:',
      '  - pg_query',
      '---',
      '',
      '# 代码审查 Skill',
      '',
      '审查代码时关注：',
      '',
      '1. 安全漏洞（SQL 注入、XSS）',
      '2. 异常处理与边界条件',
      '3. 命名与可读性',
    ].join('\n'),
  }),

  list_skill_files: sample('list_skill_files', {
    slug: 'code-review',
    files: ['SKILL.md', 'examples/review-checklist.md'],
    total: 2,
  }),

  execute_code: sample('execute_code', {
    success: true,
    language: 'python',
    output: 'Hello, 智元!\n',
    returnValue: null,
    error: null,
    elapsedMs: 128,
  }),

  sandbox_read_file: sample('sandbox_read_file', {
    path: 'output.txt',
    content: 'Line 1: analysis result\nLine 2: processing complete\nLine 3: done',
    size: 68,
  }),

  sandbox_list_files: sample('sandbox_list_files', {
    dirPath: 'data',
    files: ['input.csv', 'output/result.json', 'output/summary.md'],
    total: 3,
  }),

  sandbox_write_file: sample('sandbox_write_file', {
    path: 'outputs/files/report.json',
    size: 256,
    success: true,
  }),

  sandbox_append_file: sample('sandbox_append_file', {
    path: 'outputs/files/report.md',
    size: 128,
    success: true,
    mode: 'append',
  }),

  ocr_parse_file: sample('ocr_parse_file', {
    source_path: 'invoice.pdf',
    parsed_path: 'ocr/invoice.md',
    char_count: 1280,
    preview: '发票号码：12345678\n开票日期：2026-01-15\n\n商品名称    数量    金额\n智元 订阅  1    ¥999.00\n\n合计：¥999.00',
    truncated: true,
  }),

  present_artifacts: sample('present_artifacts', {
    success: true,
    total: 2,
    artifacts: [
      {
        name: 'chart.png',
        path: 'outputs/files/chart.png',
        url: 'https://picsum.photos/seed/lightbot-chart/400/300',
        downloadUrl: 'https://picsum.photos/seed/lightbot-chart/400/300',
        size: 102400,
        contentType: 'image/png',
      },
      {
        name: 'report.pdf',
        path: 'outputs/files/report.pdf',
        url: 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf',
        downloadUrl: 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf',
        size: 204800,
        contentType: 'application/pdf',
      },
    ],
    errors: [],
  }),

  present_business_page: sample('present_business_page', {
    success: true,
    pageType: 'leave_request',
    displayName: '请假申请',
    title: '请假申请',
    mode: 'inline',
    props: {
      days: 1,
      reason: '事假',
    },
    pageHtml: '<!DOCTYPE html><html><body><p>请假申请 Demo</p></body></html>',
    actions: ['submit', 'cancel'],
    options: {
      primaryButtonText: '提交申请',
      hint: 'H5 业务页 · Debug Lab 预览',
    },
    wait_for_user: true,
    break_loop: true,
    renderHint: 'h5',
    schemaVersion: 1,
  }),

  install_skill: sample('install_skill', {
    success: true,
    message: '成功安装 1 个技能，下一轮对话即可使用',
    total: 1,
    installed: [
      { slug: 'pdf-summary', displayName: 'PDF 摘要', activated: true },
    ],
    errors: [],
  }),

  memory_save: sample('memory_save', {
    success: true,
    memory: {
      id: '2056961707612393473',
      agentId: null,
      sessionId: '2056961707612393480',
      memoryType: 'preference',
      content: '用户偏好使用 React + TypeScript 技术栈，习惯下午 2 点后写代码',
      keywords: ['技术栈', '偏好', 'React', 'TypeScript'],
      confidence: 0.9,
      status: 'enabled',
      lastUsedAt: null,
      createTime: '2026-07-09 14:20:00',
      updateTime: '2026-07-09 14:20:00',
    },
  }),

  memory_search: sample('memory_search', {
    success: true,
    memories: [
      {
        id: '2056961707612393473',
        agentId: null,
        sessionId: '2056961707612393480',
        memoryType: 'preference',
        content: '用户偏好使用 React + TypeScript 技术栈，习惯下午 2 点后写代码',
        keywords: ['技术栈', '偏好', 'React', 'TypeScript'],
        confidence: 0.9,
        status: 'enabled',
        lastUsedAt: '2026-07-09 15:00:00',
        createTime: '2026-07-09 14:20:00',
        updateTime: '2026-07-09 14:20:00',
      },
      {
        id: '2056961707612393474',
        agentId: null,
        sessionId: '2056961707612393481',
        memoryType: 'profile',
        content: '用户是拥有 10 年经验的 Java 架构师，擅长分布式系统',
        keywords: ['Java', '架构师', '分布式'],
        confidence: 0.95,
        status: 'enabled',
        lastUsedAt: null,
        createTime: '2026-07-08 10:12:00',
        updateTime: '2026-07-08 10:12:00',
      },
    ],
  }),

  memory_delete: sample('memory_delete', {
    success: true,
    memoryId: '2056961707612393473',
  }),

  [UNREGISTERED_TOOL_NAME]: sample('custom_unknown_tool', {
    message: '未注册工具示例输出',
    data: { foo: 'bar' },
  }),
}

/** 各工具错误样例（对齐 ErrorToolResult 或组件内错误态） */
export const TOOL_DEBUG_ERROR_SAMPLES = {
  query_knowledge: { _error: true, message: '工具执行失败: 知识库检索超时' },
  find_in_document: { _error: true, message: '工具执行失败: 文档不存在' },
  search_documents: { _error: true, message: '工具执行失败: 知识库 ID 无效' },
  list_knowledge_bases: '该智能体未绑定任何知识库。',
  get_mindmap: '该知识库尚未生成思维导图，请先在知识库详情页生成。',
  open_kb_document: '读取文档失败: 文档不存在或已被删除',
  web_search: { _error: true, message: '联网搜索未配置或网络请求失败' },
  calculator: { _error: true, message: '工具执行失败: 表达式格式无效' },
  pg_list_tables: { _error: true, message: '工具执行失败: 数据库连接失败' },
  pg_describe_table: { _error: true, message: '工具执行失败: 表 agent_backup 不存在' },
  pg_query: { _error: true, message: '工具执行失败: SQL 语法错误' },
  ask_user: { _error: true, message: '工具执行失败: 问题内容不能为空' },
  write_todos: {
    success: false,
    message: '更新待办失败：待办 id 不能为空',
    todos: [],
  },
  image_generation: '图片生成过程中发生错误: 模型未配置或 API Key 无效',
  read_skill: '读取技能失败: 技能 code-review 不存在',
  list_skill_files: '技能不存在: unknown-skill',
  execute_code: {
    success: false,
    language: 'python',
    output: '',
    returnValue: null,
    error: 'SyntaxError: invalid syntax (<string>, line 1)',
    elapsedMs: 45,
  },
  sandbox_read_file: { success: false, error: '读取文件失败: 文件不存在: missing.txt' },
  sandbox_list_files: { success: false, error: '读取目录失败: 目录不存在: /missing' },
  sandbox_write_file: { success: false, error: '写入文件失败: 权限不足' },
  sandbox_append_file: { success: false, error: '追加文件失败: 权限不足' },
  ocr_parse_file: { success: false, error: 'OCR 解析失败: 不支持的文件类型: docx' },
  present_artifacts: {
    success: false,
    total: 0,
    artifacts: [],
    errors: ['outputs/missing.pdf（文件不存在）', 'outputs/bad.txt（路径不在 outputs/ 下）'],
  },
  present_business_page: { _error: true, message: '未知或已禁用 pageType: not_exists。可用：（尚未在能力中心注册任何业务页）' },
  install_skill: { _error: true, message: '未找到可安装的技能' },
  memory_save: { success: false, error: '缺少 content 参数' },
  memory_search: { success: false, error: '缺少用户上下文，无法查询长期记忆' },
  memory_delete: { success: false, error: '未找到可停用的长期记忆' },
  [UNREGISTERED_TOOL_NAME]: { _error: true, message: '工具 custom_unknown_tool 不存在' },
}

export function getToolSelectOptions() {
  const registered = REGISTERED_TOOL_NAMES.map((name) => ({
    value: name,
    label: `${getToolDisplayName(name)} (${name})`,
  }))
  registered.push({
    value: UNREGISTERED_TOOL_NAME,
    label: '未注册工具 (BaseToolCall 兜底)',
  })
  return registered
}

export function getToolSampleEvent(toolName) {
  if (toolName === UNREGISTERED_TOOL_NAME) {
    return { ...TOOL_DEBUG_SAMPLES[UNREGISTERED_TOOL_NAME] }
  }
  const s = TOOL_DEBUG_SAMPLES[toolName]
  if (s) return { ...s }
  return sample(toolName, { message: 'empty sample' })
}

export function getToolSampleResultJson(toolName) {
  const event = getToolSampleEvent(toolName)
  try {
    return JSON.stringify(JSON.parse(event.result), null, 2)
  } catch {
    return event.result || '{}'
  }
}

/** 供消息组合 Preset 等复用：返回 result 字符串 */
export function getToolSampleResultString(toolName) {
  return getToolSampleEvent(toolName).result
}

/**
 * 获取当前工具的错误样例 JSON 文本
 * @param {string} [toolName]
 */
export function getToolErrorSampleResult(toolName) {
  const key = toolName || REGISTERED_TOOL_NAMES[0]
  const err = TOOL_DEBUG_ERROR_SAMPLES[key] ?? TOOL_DEBUG_ERROR_SAMPLES[UNREGISTERED_TOOL_NAME]
  if (typeof err === 'string') {
    return JSON.stringify(err)
  }
  return JSON.stringify(err, null, 2)
}
