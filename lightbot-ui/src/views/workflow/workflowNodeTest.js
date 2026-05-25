/**
 * 支持单节点测试的节点类型（对齐 spring-ai-alibaba-admin allowSingleTest）
 */
export const NODE_TYPES_SINGLE_TEST = new Set([
  'script',
  'api',
  'variable',
  'tool',
  'llm',
  'retrieval',
  'output',
  'mcp',
  'classifier',
  'parameter_extractor',
  'variable_handle',
  'condition',
  'loop',
  'batch',
])

export function canSingleTestNodeType(type) {
  return NODE_TYPES_SINGLE_TEST.has(type)
}

/** 从节点配置推断单节点测试需要的输入字段 */
export function buildNodeTestInputs(node) {
  if (!node?.data) return []
  const d = node.data
  const inputs = []

  const push = (key, label, defaultValue = '') => {
    if (!key) return
    inputs.push({ key, label: label || key, value: defaultValue })
  }

  if (node.type === 'script' && Array.isArray(d.inputParams)) {
    d.inputParams.forEach(p => push(p.key, p.key, ''))
  }
  if (node.type === 'script' && Array.isArray(d.input_params)) {
    d.input_params.forEach(p => push(p.key, p.key, ''))
  }
  if (node.type === 'variable') {
    push('input', '测试输入', d.variableValue || '{{query}}')
  }
  if (node.type === 'api' || node.type === 'llm' || node.type === 'retrieval' || node.type === 'output') {
    push('query', '用户问题 / 测试输入', '{{query}}')
    push('input', '流程输入', '')
  }
  if (node.type === 'loop' || node.type === 'batch') {
    const arr = d.input_params?.[0]?.value || d.arrayVariable || '{{input}}'
    push('input', '测试数组/输入', arr)
  }
  if (node.type === 'classifier' || node.type === 'parameter_extractor') {
    push('query', '待处理文本', d.inputVariable || '{{query}}')
  }

  if (!inputs.length) {
    push('query', '测试输入', '')
    push('input', '流程输入', '')
  }

  const seen = new Set()
  return inputs.filter(item => {
    if (seen.has(item.key)) return false
    seen.add(item.key)
    return true
  })
}
