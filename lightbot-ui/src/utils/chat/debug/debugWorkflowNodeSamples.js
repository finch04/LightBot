/**
 * 工作流节点级 mock 样例（start + complete 成对，含 outputs / 返回信息）
 * 用于 Debug Lab 组合各节点样式渲染
 */

let seq = 0
function nextId(prefix) {
  seq += 1
  return `${prefix}-${seq}`
}

/** @returns {object[]} workflowEvents */
export function buildWorkflowNodeSample(nodeType) {
  const builders = {
    start: buildStartEndPair,
    end: buildEndNode,
    llm: buildLlmNode,
    retrieval: buildRetrievalNode,
    tool: buildToolNode,
    condition: buildConditionNode,
    classifier: buildClassifierNode,
    api: buildApiNode,
    script: buildScriptNode,
    confirm: buildConfirmNode,
    variable: buildVariableNode,
    input: buildInputNode,
    output: buildOutputNode,
  }
  const fn = builders[nodeType]
  if (!fn) return buildLlmNode()
  return fn()
}

export const WORKFLOW_NODE_TYPE_OPTIONS = [
  { value: 'start', label: '开始 (start)' },
  { value: 'llm', label: '大模型 (llm)' },
  { value: 'retrieval', label: '知识检索 (retrieval)' },
  { value: 'tool', label: '工具调用 (tool)' },
  { value: 'condition', label: '条件判断 (condition)' },
  { value: 'classifier', label: '意图分类 (classifier)' },
  { value: 'api', label: 'API (api)' },
  { value: 'script', label: '脚本 (script)' },
  { value: 'variable', label: '变量 (variable)' },
  { value: 'input', label: '输入 (input)' },
  { value: 'output', label: '输出 (output)' },
  { value: 'confirm', label: '人工确认 (confirm)' },
  { value: 'end', label: '结束 (end)' },
]

function buildStartEndPair() {
  const id = nextId('start')
  return [
    { type: 'workflow_node_start', nodeId: id, nodeType: 'start', nodeLabel: '开始', stepIndex: 0 },
    {
      type: 'workflow_node_complete',
      nodeId: id,
      nodeType: 'start',
      nodeLabel: '开始',
      stepIndex: 0,
      success: true,
      durationMs: 12,
      outputs: { triggered: true },
      message: '工作流已启动',
    },
  ]
}

function buildEndNode() {
  const id = nextId('end')
  return [
    { type: 'workflow_node_start', nodeId: id, nodeType: 'end', nodeLabel: '结束', stepIndex: 0 },
    {
      type: 'workflow_node_complete',
      nodeId: id,
      nodeType: 'end',
      nodeLabel: '结束',
      stepIndex: 0,
      success: true,
      durationMs: 5,
      outputs: { finished: true },
      message: '工作流正常结束',
    },
    { type: 'workflow_complete', success: true },
  ]
}

function buildLlmNode() {
  const id = nextId('llm')
  return [
    { type: 'workflow_node_start', nodeId: id, nodeType: 'llm', nodeLabel: '大模型生成', stepIndex: 0, input: { prompt: '总结 智元 功能' } },
    {
      type: 'workflow_node_complete',
      nodeId: id,
      nodeType: 'llm',
      nodeLabel: '大模型生成',
      stepIndex: 0,
      success: true,
      durationMs: 1280,
      outputs: {
        text: '智元 是轻量级 Java AI Agent 平台，支持 RAG、工作流与 Tool 调用。',
        tokenUsage: { prompt: 120, completion: 86 },
      },
      message: '模型回复已生成',
      traceData: { model: 'gpt-4o-mini', temperature: 0.7 },
    },
  ]
}

function buildRetrievalNode() {
  const id = nextId('retrieval')
  return [
    { type: 'workflow_node_start', nodeId: id, nodeType: 'retrieval', nodeLabel: '知识库检索', stepIndex: 0, input: { query: 'Agent 架构' } },
    {
      type: 'workflow_node_complete',
      nodeId: id,
      nodeType: 'retrieval',
      nodeLabel: '知识库检索',
      stepIndex: 0,
      success: true,
      durationMs: 340,
      outputs: {
        results: [
          { documentName: '架构设计.md', content: '智元 采用模块化单体架构...', score: 0.92 },
          { documentName: 'RAG 指南.pdf', content: '向量检索用于增强生成...', score: 0.88 },
        ],
        total: 2,
      },
      message: '检索到 2 条相关文档',
    },
  ]
}

function buildToolNode() {
  const id = nextId('tool')
  return [
    {
      type: 'workflow_node_start',
      nodeId: id,
      nodeType: 'tool',
      nodeLabel: '计算器',
      stepIndex: 0,
      toolName: 'calculator',
      input: { expression: '123*456' },
    },
    {
      type: 'workflow_node_complete',
      nodeId: id,
      nodeType: 'tool',
      nodeLabel: '计算器',
      stepIndex: 0,
      toolName: 'calculator',
      success: true,
      durationMs: 56,
      outputs: { expression: '123*456', result: 56088 },
      message: '工具执行成功',
    },
  ]
}

function buildConditionNode() {
  const id = nextId('condition')
  return [
    { type: 'workflow_node_start', nodeId: id, nodeType: 'condition', nodeLabel: '条件分支', stepIndex: 0, input: { expression: 'score > 0.8' } },
    {
      type: 'workflow_node_complete',
      nodeId: id,
      nodeType: 'condition',
      nodeLabel: '条件分支',
      stepIndex: 0,
      success: true,
      durationMs: 18,
      outputs: { matched: true, branch: 'high_confidence' },
      nextNodeId: 'llm-high',
      message: '条件命中：high_confidence 分支',
    },
  ]
}

function buildClassifierNode() {
  const id = nextId('classifier')
  return [
    { type: 'workflow_node_start', nodeId: id, nodeType: 'classifier', nodeLabel: '意图分类', stepIndex: 0, input: { text: '帮我查一下知识库' } },
    {
      type: 'workflow_node_complete',
      nodeId: id,
      nodeType: 'classifier',
      nodeLabel: '意图分类',
      stepIndex: 0,
      success: true,
      durationMs: 210,
      outputs: {
        intent: 'knowledge_query',
        confidence: 0.94,
        candidates: [
          { label: 'knowledge_query', score: 0.94 },
          { label: 'chitchat', score: 0.04 },
        ],
      },
      nextNodeId: 'retrieval-1',
      message: '分类结果：knowledge_query',
    },
  ]
}

function buildApiNode() {
  const id = nextId('api')
  return [
    { type: 'workflow_node_start', nodeId: id, nodeType: 'api', nodeLabel: 'HTTP 请求', stepIndex: 0, input: { url: 'https://api.example.com/status', method: 'GET' } },
    {
      type: 'workflow_node_complete',
      nodeId: id,
      nodeType: 'api',
      nodeLabel: 'HTTP 请求',
      stepIndex: 0,
      success: true,
      durationMs: 420,
      outputs: { statusCode: 200, body: { ok: true, version: '2.0.0' } },
      message: 'HTTP 200 OK',
    },
  ]
}

function buildScriptNode() {
  const id = nextId('script')
  return [
    { type: 'workflow_node_start', nodeId: id, nodeType: 'script', nodeLabel: '脚本处理', stepIndex: 0, input: { lang: 'javascript' } },
    {
      type: 'workflow_node_complete',
      nodeId: id,
      nodeType: 'script',
      nodeLabel: '脚本处理',
      stepIndex: 0,
      success: true,
      durationMs: 95,
      outputs: { result: { formatted: '2026-07-08', weekday: 'Wednesday' } },
      message: '脚本执行完成',
    },
  ]
}

function buildConfirmNode() {
  const id = nextId('confirm')
  return [
    { type: 'workflow_node_start', nodeId: id, nodeType: 'confirm', nodeLabel: '人工确认', stepIndex: 0 },
    {
      type: 'workflow_confirm_required',
      nodeId: id,
      runId: 'debug-run-confirm',
      confirmForm: {
        hitlType: 'ask_user',
        toolName: 'ask_user',
        question: '是否继续执行后续节点？',
        options: [
          { label: '继续', value: 'continue' },
          { label: '终止', value: 'abort' },
        ],
      },
    },
  ]
}

function buildVariableNode() {
  const id = nextId('variable')
  return [
    { type: 'workflow_node_start', nodeId: id, nodeType: 'variable', nodeLabel: '变量赋值', stepIndex: 0, input: { name: 'userName', value: 'Alice' } },
    {
      type: 'workflow_node_complete',
      nodeId: id,
      nodeType: 'variable',
      nodeLabel: '变量赋值',
      stepIndex: 0,
      success: true,
      durationMs: 8,
      outputs: { userName: 'Alice' },
      message: '变量 userName 已写入',
    },
  ]
}

function buildInputNode() {
  const id = nextId('input')
  return [
    { type: 'workflow_node_start', nodeId: id, nodeType: 'input', nodeLabel: '用户输入', stepIndex: 0, input: { question: '请输入您的需求' } },
    {
      type: 'workflow_node_complete',
      nodeId: id,
      nodeType: 'input',
      nodeLabel: '用户输入',
      stepIndex: 0,
      success: true,
      durationMs: 0,
      outputs: { userInput: '帮我总结项目文档' },
      message: '已接收用户输入',
    },
  ]
}

function buildOutputNode() {
  const id = nextId('output')
  return [
    { type: 'workflow_node_start', nodeId: id, nodeType: 'output', nodeLabel: '输出节点', stepIndex: 0 },
    {
      type: 'workflow_node_complete',
      nodeId: id,
      nodeType: 'output',
      nodeLabel: '输出节点',
      stepIndex: 0,
      success: true,
      durationMs: 6,
      outputs: { answer: '这是工作流最终输出内容，可在节点详情中查看。' },
      message: '输出已写入',
    },
  ]
}

/** 组合多个节点类型为完整 workflowEvents */
export function combineWorkflowNodeSamples(nodeTypes) {
  const events = []
  let stepIndex = 0
  for (const type of nodeTypes) {
    const chunk = buildWorkflowNodeSample(type)
    for (const ev of chunk) {
      if (ev.type === 'workflow_node_start' || ev.type === 'workflow_node_complete') {
        events.push({ ...ev, stepIndex: stepIndex++ })
      } else {
        events.push({ ...ev })
      }
    }
  }
  if (!events.some((e) => e.type === 'workflow_complete')) {
    events.push({ type: 'workflow_complete', success: true })
  }
  return events
}

/** 完整链路样例（含 LLM 输出、检索结果、工具返回） */
export function buildFullWorkflowPipelineEvents() {
  return combineWorkflowNodeSamples(['start', 'input', 'classifier', 'retrieval', 'llm', 'tool', 'output', 'end'])
}

/** 失败节点样例 */
export function buildWorkflowFailureEvents() {
  const id = nextId('tool-fail')
  return [
    { type: 'workflow_node_start', nodeId: id, nodeType: 'tool', nodeLabel: '联网搜索', stepIndex: 0, toolName: 'web_search' },
    {
      type: 'workflow_node_complete',
      nodeId: id,
      nodeType: 'tool',
      nodeLabel: '联网搜索',
      stepIndex: 0,
      toolName: 'web_search',
      success: false,
      durationMs: 30012,
      failureReason: 'TIMEOUT',
      userMessage: '联网搜索超时，请稍后重试',
      message: '工具调用超时',
      outputs: null,
    },
    { type: 'workflow_complete', success: false },
  ]
}

/** 重试样例 */
export function buildWorkflowRetryEvents() {
  const id = nextId('api-retry')
  return [
    { type: 'workflow_node_start', nodeId: id, nodeType: 'api', nodeLabel: '外部 API', stepIndex: 0 },
    { type: 'workflow_node_retry', nodeId: id, nodeLabel: '外部 API', attempt: 1, maxAttempts: 3, message: '连接重置，正在重试...' },
    { type: 'workflow_node_retry', nodeId: id, nodeLabel: '外部 API', attempt: 2, maxAttempts: 3, message: '第 2 次重试...' },
    {
      type: 'workflow_node_complete',
      nodeId: id,
      nodeType: 'api',
      nodeLabel: '外部 API',
      stepIndex: 0,
      success: true,
      durationMs: 2100,
      outputs: { statusCode: 200 },
      message: '第 3 次尝试成功',
    },
    { type: 'workflow_complete', success: true },
  ]
}
