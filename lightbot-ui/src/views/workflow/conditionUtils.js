import { createConditionId } from './nodeMeta'

const HANDLE_SEQUENCE = ['out_a', 'out_b', 'out_c']

/** 从变量引用解析变量名 */
export function resolveVariableKey(variable) {
  if (!variable) return ''
  const m = String(variable).match(/\{\{\s*([^}]+)\s*\}\}/)
  return (m ? m[1] : variable).trim()
}

/** 单条规则编译为后端可解析的表达式（兼容旧 ConditionNodeProcessor） */
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

/** 同步 conditionGroups -> branches（兼容后端旧字段） */
export function syncConditionBranches(nodeData, edges, nodeId) {
  const groups = nodeData.conditionGroups || []
  nodeData.branches = groups.map((group, index) => {
    const handle = group.sourceHandle || HANDLE_SEQUENCE[index] || 'out_c'
    const edge = (edges || []).find(
      e => e.source === nodeId && (e.sourceHandle || 'out') === handle
    )
    return {
      condition: compileGroupToCondition(group),
      targetNodeId: edge?.target || '',
      sourceHandle: handle,
      label: group.label,
    }
  })
}

/** 迁移/初始化条件组 */
export function ensureConditionGroups(data) {
  if (data.conditionGroups?.length) {
    return data.conditionGroups
  }
  if (data.branches?.length) {
    return data.branches.map((b, i) => ({
      id: createConditionId(),
      label: i === 0 ? '如果' : i === data.branches.length - 1 ? '否则' : '否则如果',
      relation: 'and',
      sourceHandle: b.sourceHandle || HANDLE_SEQUENCE[i] || 'out_c',
      rules: [
        {
          id: createConditionId(),
          variable: '{{query}}',
          operator: 'contains',
          value: (b.condition || '').replace(/.*contains\s*/i, '').trim(),
        },
      ],
    }))
  }
  return [
    {
      id: createConditionId(),
      label: '如果',
      relation: 'and',
      sourceHandle: 'out_a',
      rules: [{ id: createConditionId(), variable: '{{query}}', operator: 'contains', value: '' }],
    },
    {
      id: createConditionId(),
      label: '否则',
      relation: 'and',
      sourceHandle: 'out_c',
      rules: [],
    },
  ]
}
