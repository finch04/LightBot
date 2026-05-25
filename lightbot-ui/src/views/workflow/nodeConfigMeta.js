/**
 * 工作流节点配置说明、内置变量、示例配置（对齐 spring-ai-alibaba-admin 常用字段）
 */

/** 内置变量：可在输入框中插入 {{key}} */
export const BUILTIN_VARIABLES = [
  {
    key: 'query',
    label: '用户问题',
    desc: '当前轮次用户输入文本，对话场景最常用',
    example: '{{query}}',
  },
  {
    key: 'input',
    label: '流程输入',
    desc: '工作流开始节点或上游节点写入的默认输入变量',
    example: '{{input}}',
  },
  {
    key: 'history_list',
    label: '对话历史',
    desc: '按轮次组织的消息列表，适合多轮对话与脚本处理',
    example: '{{history_list}}',
  },
  {
    key: 'history',
    label: '历史文本',
    desc: '拼接后的历史对话字符串',
    example: '{{history}}',
  },
  {
    key: 'session_id',
    label: '会话 ID',
    desc: '当前会话唯一标识',
    example: '{{session_id}}',
  },
  {
    key: 'agent_id',
    label: 'Agent ID',
    desc: '当前智能体 ID',
    example: '{{agent_id}}',
  },
]

/** 条件规则运算符 */
export const CONDITION_OPERATORS = [
  { value: 'eq', label: '等于' },
  { value: 'neq', label: '不等于' },
  { value: 'contains', label: '包含' },
  { value: 'not_contains', label: '不包含' },
  { value: 'empty', label: '为空' },
  { value: 'not_empty', label: '不为空' },
]

/** 脚本语言 */
export const SCRIPT_LANGUAGES = [
  { value: 'javascript', label: 'JavaScript' },
  { value: 'python', label: 'Python' },
  { value: 'groovy', label: 'Groovy' },
]

/** 各语言脚本模板（新建/切换语言时使用） */
export const SCRIPT_TEMPLATES_BY_LANGUAGE = {
  javascript: `function main(params) {
  const query = params.query || '';
  const history_list = params.history_list || [];
  return {
    result: query.trim(),
    historyCount: Array.isArray(history_list) ? history_list.length : 0
  };
}`,
  python: `def main(params):
    query = params.get("query") or ""
    history_list = params.get("history_list") or []
    return {
        "result": query.strip(),
        "historyCount": len(history_list) if isinstance(history_list, list) else 0
    }`,
  groovy: `def main(Map params) {
    def query = params.query ?: ''
    def history = params.history_list ?: []
    return [
        result: query.trim(),
        historyCount: history instanceof List ? history.size() : 0
    ]
}`,
}

const SCRIPT_EXAMPLE_BASE = {
  label: '格式化输出',
  inputParams: [
    { key: 'query', value: '{{query}}' },
    { key: 'history_list', value: '{{history_list}}' },
  ],
  outputParams: [{ key: 'result', type: 'String' }],
  retryConfig: { enabled: true, maxAttempts: 3, delayMs: 1000 },
  errorStrategy: 'defaultValue',
  defaultOutput: '{"result":""}',
}

/** 按语言获取脚本示例配置 */
export function getScriptExampleConfig(language = 'javascript') {
  const lang = SCRIPT_TEMPLATES_BY_LANGUAGE[language] ? language : 'javascript'
  return {
    ...SCRIPT_EXAMPLE_BASE,
    scriptLanguage: lang,
    scriptContent: SCRIPT_TEMPLATES_BY_LANGUAGE[lang],
  }
}

/** 判断当前脚本内容是否为某一语言的默认模板（用于切换语言时替换） */
export function isScriptTemplateContent(content) {
  const t = (content || '').trim()
  if (!t) return true
  return Object.values(SCRIPT_TEMPLATES_BY_LANGUAGE).some(tpl => tpl.trim() === t)
}

/** 各节点类型字段说明（用于 label 旁 ? 悬浮提示） */
export const FIELD_HINTS = {
  common: {
    label: '画布上展示的节点名称，便于识别',
  },
  llm: {
    providerId: '选择已配置的模型提供商（OpenAI / DashScope 等）',
    modelId: '该提供商下的具体模型，如 qwen-plus、gpt-4o',
    sysPrompt: 'System 角色提示词，定义 AI 身份与全局规则，不随用户输入变化',
    promptTemplate: 'User 提示词模板，支持 {{query}}、{{history_list}} 等变量',
    temperature: '采样温度，越高越发散，越低越稳定，建议 0.3~1.0',
  },
  classifier: {
    inputVariable: '待分类的文本，通常填 {{query}}',
    mode_switch: '快速模式不输出思考过程；效果模式逐步推理，匹配更准',
    instruction: '补充意图识别约束，如行业术语、分类边界',
  },
  condition: {
    conditionGroups: '按顺序匹配条件组，命中后走对应出口（上/下/右）；均未命中走「否则」出口',
  },
  retrieval: {
    inputVariable: '用于检索的查询文本，常用 {{query}}',
    knowledgeId: '选择已创建的知识库',
    overrideConfig: '开启后可在此节点单独设置 TopK 与相似度阈值',
    topK: '返回最相似的片段数量',
    threshold: '相似度低于该值的片段将被过滤',
  },
  tool: {
    toolId: '选择系统或自定义工具，执行后结果写入变量',
  },
  script: {
    scriptLanguage: '脚本运行语言，影响语法高亮与后续执行引擎',
    scriptContent: '入口函数 main(params)，params 含输入变量，需 return 对象作为输出',
    inputParams: '脚本可读入的变量映射，key 为 params 字段名，value 支持 {{变量}}',
    outputParams: '脚本 return 的字段声明，供下游节点引用',
    retryEnabled: '开启后失败将按次数与间隔自动重试',
    maxAttempts: '最大重试次数（含首次执行）',
    retryDelayMs: '两次重试间隔（毫秒）',
    errorStrategy: '失败时：使用默认值继续，或终止流程',
    defaultOutput: '失败且选择「默认值」时写入的 JSON 对象',
  },
  api: {
    url: '完整请求地址，支持 https',
    method: 'HTTP 方法',
    headers: '请求头 JSON，如 {"Authorization":"Bearer xxx"}',
    body: '请求体 JSON，支持 {{query}} 变量',
    timeout: '请求超时时间（秒）',
  },
  loop: {
    iteratorType: '按数组遍历元素，或固定次数循环',
    arrayVariable: '待迭代的数组变量，需为 Array 类型',
    countLimit: '按次数循环时的最大迭代次数',
    outputParams: '循环结束后写入流程的变量声明',
  },
  batch: {
    arrayVariable: '待批处理的数组变量，需为 Array 类型',
    batchSize: '单批最大条数',
    concurrentSize: '并行执行数量',
    errorStrategy: '批处理中单条失败时的处理策略',
    outputParams: '批处理聚合结果变量',
  },
  variable: {
    variableName: '写入会话的变量名',
    variableValue: '变量值，支持 {{query}} 等引用',
  },
  input: {
    outputParams: '流程开始时暴露给下游的参数，query 为常用默认项',
  },
  output: {
    output: '最终输出模板，可引用上游节点输出变量',
    streamSwitch: '是否以流式方式返回给前端',
  },
  mcp: {
    mcpServerName: 'MCP 服务名称',
    toolName: '要调用的工具名',
    inputParams: '工具入参 JSON，支持变量占位',
  },
  parameter_extractor: {
    inputVariable: '待提取的源文本',
    instruction: '补充提取规则说明',
    extractParams: '需提取的参数 key、类型与是否必填',
  },
  variable_handle: {
    handleType: '模板拼接或分组取值',
    templateContent: '模板字符串，支持 {{变量}}',
    groupStrategy: '多变量时的选取策略',
  },
  app_component: {
    componentCode: '已发布组件唯一标识',
    componentType: '引用工作流或智能体组件',
    streamSwitch: '子组件是否流式返回',
  },
}

/** 节点示例配置（点击节点详情 ? 展示） */
export const NODE_EXAMPLES = {
  llm: {
    title: '大模型节点',
    summary: '调用 LLM 生成内容，支持系统/用户提示词与短期记忆。',
    example: {
      label: '客服回复',
      providerId: '1',
      modelId: 'qwen-plus',
      sysPrompt: '你是专业客服，回答简洁友好。',
      promptTemplate: '用户问题：{{query}}\n\n参考历史：{{history_list}}',
      temperature: 0.7,
      short_memory: { enabled: true, type: 'self', round: 5, paramKey: '' },
    },
  },
  condition: {
    title: '条件判断节点',
    summary: '按条件组顺序匹配，分别走上方/下方/右侧出口；否则走默认分支。',
    example: {
      label: '意图路由',
      conditionGroups: [
        {
          label: '如果',
          relation: 'and',
          sourceHandle: 'out_a',
          rules: [{ variable: '{{query}}', operator: 'contains', value: '价格' }],
        },
        {
          label: '否则如果',
          relation: 'and',
          sourceHandle: 'out_b',
          rules: [{ variable: '{{query}}', operator: 'contains', value: '售后' }],
        },
      ],
    },
  },
  script: {
    title: '脚本节点',
    summary: '使用 JavaScript / Python / Groovy 处理变量；切换语言后示例与模板会随之变化。',
    example: getScriptExampleConfig('javascript'),
    examplesByLanguage: {
      javascript: getScriptExampleConfig('javascript'),
      python: getScriptExampleConfig('python'),
      groovy: getScriptExampleConfig('groovy'),
    },
  },
  retrieval: {
    title: '知识检索节点',
    summary: '从知识库召回片段，供下游 LLM 或输出节点使用。',
    example: {
      label: '产品知识检索',
      inputVariable: '{{query}}',
      knowledgeId: '1234567890',
      topK: 5,
      threshold: 0.5,
      overrideConfig: false,
    },
  },
  classifier: {
    title: '意图分类节点',
    summary: '用大模型判断用户意图，命中后走对应分支出口。',
    example: {
      label: '咨询分流',
      inputVariable: '{{query}}',
      conditions: [
        { id: 'cond_1', subject: '用户询问产品价格或优惠' },
        { id: 'cond_2', subject: '用户需要售后服务' },
      ],
      mode_switch: 'efficient',
    },
  },
  api: {
    title: 'HTTP API 节点',
    summary: '调用外部 REST 接口，将响应写入流程变量。',
    example: {
      label: '查询订单',
      url: 'https://api.example.com/order',
      method: 'POST',
      headers: '{"Content-Type":"application/json"}',
      body: '{"query":"{{query}}"}',
      timeout: 30,
    },
  },
  tool: {
    title: '工具调用节点',
    summary: '执行平台已注册工具（搜索、计算等）。',
    example: { label: '网页搜索', toolId: '1', toolName: 'web_search' },
  },
  input: {
    title: '流程输入节点',
    summary: '定义工作流对外输入参数，query 为常用内置字段。',
    example: {
      label: '流程输入',
      outputParams: [{ key: 'query', type: 'String', defaultValue: '' }],
    },
  },
  output: {
    title: '流程输出节点',
    summary: '将模板渲染结果作为工作流输出返回给用户。',
    example: { label: '回复用户', output: '{{result}}', streamSwitch: true },
  },
  loop: {
    title: '循环节点',
    summary: '按数组逐项迭代，或按固定次数重复执行子流程；每次迭代可将当前元素写入变量供下游使用。',
    example: {
      label: '遍历订单列表',
      iteratorType: 'byArray',
      arrayVariable: '{{orders}}',
      countLimit: 10,
    },
  },
  batch: {
    title: '批处理节点',
    summary: '将大数组拆成多批并行处理，适合批量调用 API 或脚本；可配置并发数与失败策略。',
    example: {
      label: '批量查库存',
      arrayVariable: '{{sku_list}}',
      batchSize: 20,
      concurrentSize: 5,
      errorStrategy: 'continueOnError',
    },
  },
  variable: {
    title: '变量赋值节点',
    summary: '向流程上下文写入或覆盖变量，供后续节点通过 {{变量名}} 引用。',
    example: {
      label: '保存用户城市',
      variableName: 'city',
      variableValue: '{{query}}',
    },
  },
  variable_handle: {
    title: '变量处理节点',
    summary: '用模板拼接多个变量，或按策略从分组中选取第一个非空值。',
    example: {
      label: '拼接回复前缀',
      handleType: 'template',
      templateContent: '您好，{{user_name}}！您的问题是：{{query}}',
      groupStrategy: 'firstNotNull',
      groups: [{ variables: [{ value: '{{nickname}}' }, { value: '{{user_name}}' }] }],
    },
  },
  mcp: {
    title: 'MCP 节点',
    summary: '调用已接入的 MCP 服务工具，将入参映射为 JSON 后执行，结果写入流程变量。',
    example: {
      label: '飞书发消息',
      mcpServerName: 'lark-im',
      toolName: 'send_message',
      inputParams: '{"chat_id":"oc_xxx","text":"{{query}}"}',
    },
  },
  parameter_extractor: {
    title: '参数提取节点',
    summary: '用大模型从自然语言中抽取结构化字段（如城市、日期），供后续 API 或条件节点使用。',
    example: {
      label: '提取行程参数',
      inputVariable: '{{query}}',
      instruction: '从用户描述中提取出发地、目的地与出行日期，缺失字段留空。',
      extractParams: [
        { key: 'from_city', type: 'String', required: true, desc: '出发城市' },
        { key: 'to_city', type: 'String', required: true, desc: '目的地城市' },
        { key: 'travel_date', type: 'String', required: false, desc: '出行日期' },
      ],
    },
  },
  app_component: {
    title: '应用组件节点',
    summary: '引用已发布的工作流或智能体作为子流程，支持流式返回。',
    example: {
      label: '调用子工作流',
      componentCode: 'wf_order_assistant',
      componentName: '订单助手',
      componentType: 'workflow',
      streamSwitch: false,
    },
  },
  start: {
    title: '开始节点',
    summary: '工作流入口，每个画布有且仅有一个；所有执行从此出发。',
    example: { label: '开始' },
  },
  end: {
    title: '结束节点',
    summary: '工作流终点，执行到达此节点后流程结束（可与输出节点配合使用）。',
    example: { label: '结束' },
  },
}

export function getFieldHint(nodeType, fieldKey) {
  return FIELD_HINTS[nodeType]?.[fieldKey] || FIELD_HINTS.common[fieldKey] || ''
}

export function getNodeExample(nodeType, options = {}) {
  const meta = NODE_EXAMPLES[nodeType]
  if (!meta) return null
  if (nodeType === 'script') {
    const lang = options.scriptLanguage || 'javascript'
    const example = meta.examplesByLanguage?.[lang] || getScriptExampleConfig(lang)
    return {
      title: meta.title,
      summary: meta.summary,
      example,
    }
  }
  return meta
}

export function getScriptTemplate(language) {
  return SCRIPT_TEMPLATES_BY_LANGUAGE[language] || SCRIPT_TEMPLATES_BY_LANGUAGE.javascript
}

/** 示例配置含外部资源 ID，禁止整包覆盖到当前节点 */
const NODE_TYPES_DISABLE_APPLY = new Set([
  'retrieval',
  'tool',
  'mcp',
  'llm',
  'classifier',
  'parameter_extractor',
  'app_component',
])

/**
 * 是否允许「应用到当前节点」
 * 知识库/工具/MCP/模型等需选择已有资源，强制应用会导致非法 ID
 */
export function canApplyNodeExample(nodeType) {
  return !NODE_TYPES_DISABLE_APPLY.has(nodeType)
}
