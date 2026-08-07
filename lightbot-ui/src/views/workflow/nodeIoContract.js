/**
 * 工作流节点输出契约（与后端 NodeIoContractRegistry 对齐）
 */

const FIXED_OUTPUTS = {
  start: [{ key: 'input', label: '流程输入', desc: '用户输入或上游传入' }],
  llm: [
    { key: 'output', label: '模型输出', desc: '大模型生成正文' },
    { key: 'llmOutput', label: '模型输出（别名）', desc: '与 output 相同' },
  ],
  parameter_extractor: [
    { key: '_is_completed', label: '是否完成', desc: '提取是否成功' },
    { key: '_reason', label: '原因说明', desc: '未完成时的说明' },
  ],
  retrieval: [
    { key: 'retrievalResult', label: '检索结果', desc: '拼接后的检索文本' },
    { key: 'retrievalChunks', label: '检索片段', desc: '命中的 chunk 列表' },
    { key: 'input', label: '检索输入', desc: '实际用于检索的 query' },
  ],
  condition: [
    { key: 'matchedHandle', label: '命中出口', desc: '条件匹配的分支 handle' },
    { key: 'matchedGroupLabel', label: '命中组名', desc: '条件组标签' },
  ],
  classifier: [
    { key: 'subject', label: '命中主题', desc: '分类结果主题' },
    { key: 'intentId', label: '意图 ID', desc: '命中意图标识' },
    { key: 'matchedIntentId', label: '匹配意图', desc: '实际路由意图' },
    { key: 'thought', label: '思考过程', desc: '分类推理过程' },
  ],
  api: [
    { key: 'statusCode', label: 'HTTP 状态码', desc: '' },
    { key: 'body', label: '响应体', desc: '' },
    { key: 'result', label: '解析结果', desc: '' },
  ],
  tool: [
    { key: 'output', label: '工具输出', desc: '' },
    { key: 'toolResult', label: '工具结果', desc: '' },
    { key: 'toolResultText', label: '工具结果文本', desc: '' },
    { key: 'toolName', label: '工具名称', desc: '' },
    { key: 'toolId', label: '工具 ID', desc: '' },
  ],
  mcp: [
    { key: 'output', label: 'MCP 输出', desc: '' },
    { key: 'mcpResult', label: 'MCP 结果', desc: '' },
    { key: 'toolName', label: '工具名称', desc: '' },
    { key: 'mcpServerName', label: 'MCP 服务名', desc: '' },
  ],
  output: [{ key: 'output', label: '流程输出', desc: '渲染后的中间输出' }],
  variable_handle: [{ key: 'output', label: '处理结果', desc: '模板或分组输出' }],
  loop: [{ key: 'iterations', label: '迭代结果', desc: '各轮输出聚合' }],
  batch: [{ key: 'iterations', label: '并行结果', desc: '各分支输出聚合' }],
  app_component: [
    { key: 'result', label: '子流程结果', desc: '' },
    { key: 'output', label: '子流程输出', desc: '' },
  ],
  sub_agent: [
    { key: 'reply', label: 'SubAgent 回复', desc: '子智能体最终文本' },
    { key: 'output', label: '输出（别名）', desc: '与 reply 相同' },
    { key: 'threadId', label: '线程 ID', desc: 'SubAgent 会话线程' },
    { key: 'subAgentName', label: 'SubAgent 名称', desc: '' },
    { key: 'subAgentId', label: 'SubAgent ID', desc: '' },
  ],
  end: [{ key: 'result', label: '最终结果', desc: '字符串，非 Map' }],
}

const DEBUG_KEYS = new Set(['extractRaw', 'classificationRaw', 'traceData'])

/** ask_user 工具节点额外输出（与后端 NodeIoContractRegistry / HITL 对齐） */
const ASK_USER_OUTPUT_FIELDS = [
  { key: 'question', label: '提问内容', desc: '挂起后即可用 {{nodeId.question}}' },
  { key: 'options', label: '选项列表', desc: '选择题时的选项（Array）' },
  { key: 'is_open_ended', label: '是否开放题', desc: 'true=开放题，false=选择题' },
  { key: 'answer', label: '用户回答', desc: 'resume 后可用 {{nodeId.answer}} 或 {{answer}}' },
]

function isAskUserToolNode(data) {
  const name = data?.toolName
  return name === 'ask_user' || name === '向用户提问'
}

export function getFixedOutputFields(nodeType) {
  return FIXED_OUTPUTS[nodeType] ? [...FIXED_OUTPUTS[nodeType]] : []
}

export function isDebugOutputKey(key) {
  return DEBUG_KEYS.has(key)
}

export function mergeNodeOutputFields(node) {
  if (!node) return []
  const type = node.type
  const fixed = getFixedOutputFields(type)
  const dynamic = extractDynamicOutputFields(type, node.data || {})
  const seen = new Set(fixed.map(f => f.key))
  const merged = [...fixed]
  for (const f of dynamic) {
    if (!seen.has(f.key) && !isDebugOutputKey(f.key)) {
      seen.add(f.key)
      merged.push(f)
    }
  }
  if (type === 'tool' && isAskUserToolNode(node.data)) {
    for (const f of ASK_USER_OUTPUT_FIELDS) {
      if (!seen.has(f.key)) {
        seen.add(f.key)
        merged.push(f)
      }
    }
  }
  return merged
}

function extractDynamicOutputFields(type, data) {
  const fields = []
  if (type === 'parameter_extractor') {
    collectKeys(data.extractParams, fields)
  } else if (type === 'script' || type === 'input' || type === 'loop' || type === 'batch') {
    collectKeys(data.output_params || data.outputParams, fields)
  } else if (type === 'variable') {
    if (data.variableName) {
      fields.push({ key: data.variableName, label: data.variableName, desc: '赋值变量' })
    }
  } else if (type === 'variable_handle') {
    const handleType = data.handleType || data.type || 'template'
    if (handleType === 'group' && Array.isArray(data.groups)) {
      for (const g of data.groups) {
        const name = g.groupName || g.group_name
        if (name) fields.push({ key: name, label: name, desc: '分组变量' })
      }
    }
  } else if (type === 'tool' || type === 'app_component' || type === 'sub_agent') {
    collectKeys(data.outputMappings, fields)
  }
  return fields
}

function collectKeys(list, fields) {
  if (!Array.isArray(list)) return
  for (const row of list) {
    const key = row?.key?.trim?.()
    if (key) {
      fields.push({ key, label: row.label || key, desc: row.desc || '' })
    }
  }
}

export function syncOutputParamsFromConfig(node) {
  if (!node?.data) return
  const fields = mergeNodeOutputFields(node)
  node.data.output_params = fields.map(f => ({
    key: f.key,
    type: f.type || 'String',
    desc: f.desc || f.label || '',
  }))
}
