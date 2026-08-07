import { createConditionId } from './nodeMeta'

/** 条件组出口：{nodeId}_{groupId} */
export function conditionGroupHandleId(nodeId, groupId) {
  if (!nodeId || !groupId) return ''
  return `${nodeId}_${groupId}`
}

/** 未命中兜底出口：{nodeId}_default */
export function conditionDefaultHandleId(nodeId) {
  if (!nodeId) return 'default'
  return `${nodeId}_default`
}

/** 从变量引用解析变量名 */
export function resolveVariableKey(variable) {
  if (!variable) return ''
  const m = String(variable).match(/\{\{\s*([^}]+)\s*\}\}/)
  return (m ? m[1] : variable).trim()
}

/** 单条规则编译为后端可解析的表达式 */
export function compileRuleToCondition(rule) {
  if (!rule) return ''
  const key = resolveVariableKey(rule.variable)
  const val = (rule.value ?? '').trim()
  switch (rule.operator) {
    case 'eq':
      return `${key} == ${val}`
    case 'neq':
      return `${key} != ${val}`
    case 'contains':
      return `${key} contains ${val}`
    case 'not_contains':
      return `${key} not_contains ${val}`
    case 'empty':
      return `${key} == `
    case 'not_empty':
      return `${key} != `
    default:
      return `${key} contains ${val}`
  }
}

/** 条件组编译为表达式 */
export function compileGroupToCondition(group) {
  const rules = (group?.rules || []).filter(r => r.variable)
  if (!rules.length) return ''
  const parts = rules.map(compileRuleToCondition).filter(Boolean)
  if (!parts.length) return ''
  if (group.relation === 'or') {
    return parts.join(' OR ')
  }
  return parts.join(' AND ')
}

/**
 * 规范化条件组：仅保留带规则的匹配组（否则出口独立，不进数组）
 * @param {object} data 节点 data
 * @returns {Array}
 */
export function ensureConditionGroups(data) {
  const raw = data?.conditionGroups
  if (Array.isArray(raw) && raw.length) {
    const groups = raw
      .filter(g => Array.isArray(g?.rules) && g.rules.length > 0)
      .map((g, i) => ({
        id: g.id || createConditionId(),
        label: g.label || (i === 0 ? '如果' : '否则如果'),
        relation: g.relation === 'or' ? 'or' : 'and',
        rules: (g.rules || []).map(r => ({
          id: r.id || createConditionId(),
          variable: r.variable || '{{query}}',
          operator: r.operator || 'contains',
          value: r.value ?? '',
        })),
      }))
    if (groups.length) return groups
  }
  return [
    {
      id: createConditionId(),
      label: '如果',
      relation: 'and',
      rules: [{ id: createConditionId(), variable: '{{query}}', operator: 'contains', value: '' }],
    },
  ]
}

/**
 * 同步 conditionGroups -> branches（供调试/旧读路径；handle 使用新约定）
 */
export function syncConditionBranches(nodeData, edges, nodeId) {
  const groups = ensureConditionGroups(nodeData)
  nodeData.conditionGroups = groups
  nodeData.branches = groups.map(group => {
    const handle = conditionGroupHandleId(nodeId, group.id)
    const edge = (edges || []).find(
      e => e.source === nodeId && (e.sourceHandle || '') === handle
    )
    return {
      condition: compileGroupToCondition(group),
      targetNodeId: edge?.target || '',
      sourceHandle: handle,
      label: group.label,
    }
  })
}

/**
 * 条件节点允许的出口 handle 集合（含默认口）
 * @param {string} nodeId
 * @param {Array} groups
 * @returns {Set<string>}
 */
export function collectConditionSourceHandles(nodeId, groups) {
  const set = new Set()
  for (const g of groups || []) {
    if (g?.id) set.add(conditionGroupHandleId(nodeId, g.id))
  }
  set.add(conditionDefaultHandleId(nodeId))
  return set
}
