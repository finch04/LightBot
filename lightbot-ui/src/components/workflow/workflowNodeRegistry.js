/**
 * 工作流节点 Chat/测试 渲染注册表（参考 toolRegistry）
 */
import {
  truncateText, parseStepOutputs, previewValue, listOutputKeys,
  extractUserInputText, buildAssignmentRows,
} from './workflowStepUtils.js'
import { getNodeTypeName } from '../../views/workflow/composables/useWorkflowNodeSteps.js'

/** 画板全量节点类型（与 NodeType 枚举对齐） */
export const ALL_WORKFLOW_NODE_TYPES = [
  'start', 'end', 'llm', 'condition', 'retrieval', 'tool', 'script', 'api',
  'loop', 'loop_start', 'loop_end', 'variable', 'classifier', 'batch',
  'batch_start', 'batch_end', 'mcp', 'input', 'output', 'variable_handle',
  'parameter_extractor', 'app_component', 'sub_agent', 'confirm',
]

/** Chat 中隐藏的内置容器边界节点 */
export const HIDDEN_IN_CHAT_TYPES = new Set([
  'loop_start', 'loop_end', 'batch_start', 'batch_end',
])

/** Chat 中弱化展示（保留步骤，降低视觉权重） */
export const WEAKENED_IN_CHAT_TYPES = new Set(['start', 'end'])

/** 详情区不重复展示正文的节点（正文在消息区流式输出） */
export const HIDE_DETAIL_BODY_TYPES = new Set(['llm', 'output'])

/**
 * Chat 步骤行样式映射（颜色对齐 nodeMeta.js / 设计文档）
 * @type {Record<string, { color: string, bg?: string, border?: string }>}
 */
export const NODE_CHAT_STYLE = {
  start: { color: 'var(--green-500)', bg: 'var(--green-50)', border: 'var(--green-200)' },
  end: { color: 'var(--color-error)', bg: 'var(--color-error-bg)', border: 'var(--color-error-soft)' },
  llm: { color: 'var(--blue-500)', bg: 'var(--color-purple-bg)', border: 'var(--color-purple-border)' },
  condition: { color: 'var(--color-warning)', bg: 'var(--color-warn-bg)', border: 'var(--color-warning-soft)' },
  retrieval: { color: 'var(--blue-500)', bg: 'var(--blue-50)', border: 'var(--blue-200)' },
  tool: { color: 'var(--green-600)', bg: 'var(--green-50)', border: 'var(--green-200)' },
  api: { color: 'var(--blue-400)', bg: 'var(--blue-50)', border: 'var(--blue-200)' },
  loop: { color: 'var(--blue-500)', bg: 'var(--color-purple-bg)', border: 'var(--color-purple-border)' },
  batch: { color: 'var(--green-500)', bg: 'var(--green-50)', border: 'var(--green-200)' },
  variable: { color: 'var(--color-warning-deep)', bg: 'var(--color-warn-bg)', border: 'var(--color-warning-soft)' },
  classifier: { color: 'var(--color-warning)', bg: 'var(--color-warn-bg)', border: 'var(--color-warning-soft)' },
  script: { color: 'var(--gray-600)', bg: 'var(--gray-50)', border: 'var(--gray-200)' },
  mcp: { color: 'var(--blue-500)', bg: 'var(--blue-50)', border: 'var(--blue-200)' },
  input: { color: 'var(--green-600)', bg: 'var(--green-50)', border: 'var(--green-200)' },
  confirm: { color: 'var(--color-warning)', bg: 'var(--color-warn-bg)', border: 'var(--color-warning-soft)' },
  output: { color: 'var(--blue-500)', bg: 'var(--blue-50)', border: 'var(--blue-200)' },
  variable_handle: { color: 'var(--color-warning-deep)', bg: 'var(--color-warn-bg)', border: 'var(--color-warning-soft)' },
  parameter_extractor: { color: 'var(--color-error)', bg: 'var(--color-error-bg)', border: 'var(--color-error-soft)' },
  app_component: { color: 'var(--blue-600)', bg: 'var(--blue-50)', border: 'var(--blue-200)' },
  sub_agent: { color: 'var(--color-warning)', bg: 'var(--color-warn-bg)', border: 'var(--color-warning-soft)' },
  loop_start: { color: 'var(--blue-400)', bg: 'var(--color-purple-bg)', border: 'var(--color-purple-border)' },
  loop_end: { color: 'var(--blue-400)', bg: 'var(--color-purple-bg)', border: 'var(--color-purple-border)' },
  batch_start: { color: 'var(--green-500)', bg: 'var(--green-50)', border: 'var(--green-200)' },
  batch_end: { color: 'var(--green-500)', bg: 'var(--green-50)', border: 'var(--green-200)' },
  _default: { color: 'var(--blue-500)', bg: 'var(--color-purple-bg)', border: 'var(--color-purple-border)' },
}

export function isHiddenInChat(nodeType) {
  return HIDDEN_IN_CHAT_TYPES.has(nodeType)
}

export function isWeakenedInChat(nodeType) {
  return WEAKENED_IN_CHAT_TYPES.has(nodeType)
}

export function isContainerNodeType(nodeType) {
  return nodeType === 'loop' || nodeType === 'batch' || nodeType === 'app_component'
}

export function getNodeChatStyle(nodeType) {
  return NODE_CHAT_STYLE[nodeType] || NODE_CHAT_STYLE._default
}

/** 步骤行左侧强调色 */
export function getStepAccentStyle(nodeType) {
  const style = getNodeChatStyle(nodeType)
  return { borderLeft: `3px solid ${style.color}` }
}

export function stepStatusClass(step) {
  if (step?.status === 'running') return 'event-running'
  if (step?.status === 'failed') return 'event-fail'
  if (step?.status === 'suspended') return 'event-suspended'
  if (step?.status === 'done') return 'event-done'
  return 'event-start'
}

export function stepStatusIcon(step) {
  if (step?.status === 'running') return 'running'
  if (step?.status === 'failed') return 'failed'
  if (step?.status === 'suspended') return 'suspended'
  if (step?.status === 'done') return 'done'
  return 'start'
}

const HANDLE_LABELS = {
  default: '都未命中',
}

function countContainerSteps(step) {
  const children = step?.children || []
  if (!children.length) return null
  const failed = children.filter(c => c.status === 'failed').length
  return { total: children.length, failed }
}

/** 步骤行右侧摘要文案（按节点业务语义） */
export function getStepSummary(step) {
  if (!step) return ''
  const type = step.nodeType
  const outputs = parseStepOutputs(step.outputs) || {}

  if (step.status === 'running') {
    return step.lastResilienceMessage || '执行中'
  }
  if (step.status === 'suspended') return type === 'confirm' ? '等待您的选择' : '等待确认'
  if (step.status === 'failed') return step.userMessage || step.message || '执行失败'

  switch (type) {
    case 'start': {
      const text = extractUserInputText(step)
      return text ? `收到输入 · ${text.length} 字` : '流程开始'
    }
    case 'end': {
      const result = outputs.result
      if (result != null && String(result).trim()) {
        const s = String(result)
        return `结果 · ${s.length} 字`
      }
      return '流程结束'
    }
    case 'llm': {
      const inTok = step.traceData?.inputTokens
      const outTok = step.traceData?.outputTokens
      if (inTok != null || outTok != null) {
        return `Token ${inTok ?? 0} → ${outTok ?? 0}`
      }
      return '生成完成'
    }
    case 'retrieval': {
      const chunks = outputs.retrievalChunks
      if (Array.isArray(chunks) && chunks.length) return `命中 ${chunks.length} 条片段`
      const text = outputs.retrievalResult || ''
      return text ? `命中 ${text.length} 字` : '未命中'
    }
    case 'tool': {
      if (step.status === 'suspended') {
        const hitl = step.confirmForm?.hitlType === 'ask_user' || step.confirmForm?.toolName === 'ask_user'
        return hitl ? '等待您的回答' : '等待确认'
      }
      const name = outputs.toolDisplayName || outputs.toolName || step.toolName
      return name ? `工具 · ${name}` : '工具执行完成'
    }
    case 'mcp': {
      const server = outputs.mcpServerName
      const tool = outputs.toolName
      if (server && tool) return `MCP · ${server}/${tool}`
      return tool ? `MCP · ${tool}` : 'MCP 调用完成'
    }
    case 'api':
      return outputs.statusCode != null ? `HTTP ${outputs.statusCode}` : 'HTTP 完成'
    case 'condition': {
      const mh = outputs.matchedHandle
      const label = outputs.matchedGroupLabel
        || (mh && String(mh).endsWith('_default') ? '都未命中' : null)
        || HANDLE_LABELS[mh]
        || mh
      return label ? `命中 · ${label}` : '路由完成'
    }
    case 'classifier':
      return outputs.subject ? `意图 · ${outputs.subject}` : '分类完成'
    case 'confirm':
      if (outputs && Object.keys(outputs).length) {
        const choice = outputs.choice ?? outputs.confirmed
        return choice ? `已选择 · ${choice}` : '已提交'
      }
      return '已提交'
    case 'app_component':
      return outputs.componentName ? `子流程 · ${outputs.componentName}` : '子工作流完成'
    case 'sub_agent':
      return outputs.subAgentName ? `SubAgent · ${outputs.subAgentName}` : 'SubAgent 完成'
    case 'loop': {
      const stat = countContainerSteps(step)
      if (stat) {
        return stat.failed ? `循环 · ${stat.total} 步 · ${stat.failed} 失败` : `循环 · ${stat.total} 步`
      }
      return '循环完成'
    }
    case 'batch': {
      const stat = countContainerSteps(step)
      if (stat) {
        return stat.failed ? `并行 · ${stat.total} 步 · ${stat.failed} 失败` : `并行 · ${stat.total} 步`
      }
      return '批处理完成'
    }
    case 'parameter_extractor': {
      const keys = listOutputKeys(outputs)
      return keys.length ? `提取 ${keys.length} 个参数` : '提取完成'
    }
    case 'script': {
      const keys = listOutputKeys(outputs)
      return keys.length ? `脚本 · ${keys.length} 个出参` : '脚本执行完成'
    }
    case 'variable': {
      const keys = listOutputKeys(outputs)
      if (keys.length === 1) {
        return `${keys[0]} = ${previewValue(outputs[keys[0]], 24)}`
      }
      return keys.length ? `赋值 ${keys.length} 项` : '变量已更新'
    }
    case 'variable_handle': {
      const keys = listOutputKeys(outputs, ['output'])
      if (keys.length) return `处理 · ${keys.join(', ')}`
      if (outputs.output != null) return `输出 · ${previewValue(outputs.output, 24)}`
      return '变量处理完成'
    }
    case 'input': {
      const keys = listOutputKeys(outputs)
      return keys.length ? `写入 · ${keys.join(', ')}` : '输入已写入'
    }
    case 'output': {
      const text = outputs.output
      return text != null && String(text).trim()
        ? `输出 · ${String(text).length} 字`
        : '已输出'
    }
    default:
      return step.message || '完成'
  }
}

export function hasExpandableStepContent(step) {
  if (!step || isHiddenInChat(step.nodeType)) return false
  if (step.status === 'failed' || step.status === 'suspended') return true
  if (step.message && step.status !== 'done') return true
  const outputs = parseStepOutputs(step.outputs)
  if (outputs && Object.keys(outputs).length) return true
  if (step.traceData && Object.keys(step.traceData).length) return true
  if (step.detail && !HIDE_DETAIL_BODY_TYPES.has(step.nodeType)) return true
  if (step.input && Object.keys(step.input).length) return true
  if (step.isContainer && step.children?.length) return true
  // start/end 弱化节点：有输入/结果时可展开
  if (step.nodeType === 'start' && extractUserInputText(step)) return true
  if (step.nodeType === 'end' && outputs?.result != null) return true
  if (step.nodeType === 'output') return true
  return false
}

export function getNodeTypeLabel(type) {
  return getNodeTypeName(type)
}

/** 节点 Chat 渲染覆盖检查（开发/文档用） */
export function getNodeChatCoverageReport() {
  return ALL_WORKFLOW_NODE_TYPES.map(type => ({
    type,
    hiddenInChat: isHiddenInChat(type),
    weakenedInChat: isWeakenedInChat(type),
    hasStyle: !!NODE_CHAT_STYLE[type],
    hasSummary: true,
    hasDetail: !isHiddenInChat(type),
  }))
}
