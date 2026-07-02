import { ensureGroupBuiltins, sortNodesParentFirst, migrateGroupNodeFields } from './workflowGroup.js'
import { normalizeWorkflowEdges } from './workflowConnection.js'
import { applyWorkflowBezierEdgeStyle } from './workflowLayout.js'
import { getDefaultNodeData } from './nodeMeta.js'

/**
 * 快照 data 与节点类型默认值合并：缺失字段用默认值填充
 * @param {string} type
 * @param {object} data
 * @returns {object}
 */
export function mergeNodeDataWithDefaults(type, data) {
  const defaults = getDefaultNodeData(type)
  return deepFillDefaults(defaults, data || {})
}

function deepFillDefaults(defaults, data) {
  const result = JSON.parse(JSON.stringify(defaults ?? {}))
  if (!data || typeof data !== 'object') return result
  for (const key of Object.keys(data)) {
    const dv = result[key]
    const sv = data[key]
    if (sv === undefined) continue
    if (
      sv !== null && typeof sv === 'object' && !Array.isArray(sv)
      && dv !== null && typeof dv === 'object' && !Array.isArray(dv)
    ) {
      result[key] = deepFillDefaults(dv, sv)
    } else {
      result[key] = sv
    }
  }
  return result
}

/**
 * 将工作流 graph JSON 转为 Vue Flow 节点/边（只读展示与编辑页共用）
 * @param {{ nodes?: Array, edges?: Array }} graph
 * @param {{ readonly?: boolean }} options
 * @returns {{ nodes: Array, edges: Array }}
 */
export function workflowGraphToVueFlow(graph, options = {}) {
  const readonly = options.readonly !== false
  if (!graph?.nodes?.length) {
    return { nodes: [], edges: [] }
  }

  const migratedNodes = (graph.nodes || []).map(n => ({
    ...n,
    type: n.type,
    id: n.id,
    data: {
      label: n.data?.label || n.label || n.id,
      ...(n.data || {}),
    },
    position: normalizePosition(n.position),
    ...(n.parentNode
      ? { parentNode: n.parentNode, extent: n.extent || 'parent', zIndex: n.zIndex ?? 10 }
      : {}),
  }))

  const withBuiltins = ensureGroupBuiltins(migratedNodes, graph.edges || [])
  const nodes = sortNodesParentFirst(
    withBuiltins.nodes.map(n => {
      const merged = migrateGroupNodeFields(n)
      if (readonly) {
        return {
          ...merged,
          draggable: false,
          selectable: true,
          connectable: false,
        }
      }
      return merged
    }),
  )
  const edges = applyWorkflowBezierEdgeStyle(normalizeWorkflowEdges(withBuiltins.edges))
  return { nodes, edges }
}

/**
 * 将 trace 节点 span 中的 config 合并进 Vue Flow 节点 data（兼容旧 trace 仅含 label 的快照）
 */
export function mergeTraceNodeData(node, span) {
  if (!node) return null
  const clone = JSON.parse(JSON.stringify(node))
  const spanCfg = span?.attributes?.config
  const raw = {
    label: clone.data?.label || span?.attributes?.nodeLabel || clone.id,
    ...(clone.data || {}),
    ...(spanCfg && typeof spanCfg === 'object' ? spanCfg : {}),
  }
  clone.data = mergeNodeDataWithDefaults(clone.type, raw)
  return clone
}

/**
 * @param {object|null|undefined} pos
 * @returns {{ x: number, y: number }}
 */
export function normalizePosition(pos) {
  if (!pos || typeof pos !== 'object') return { x: 0, y: 0 }
  return { x: Number(pos.x) || 0, y: Number(pos.y) || 0 }
}
