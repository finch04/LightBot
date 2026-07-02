/**
 * Trace / 测试 nodeEvents → 画板执行态适配
 */

/** span.status → 节点 debugStatus（与编排页测试高亮一致） */
export function spanStatusToDebugStatus(status) {
  if (status === 'completed') return 'success'
  if (status === 'failed') return 'fail'
  if (status === 'running') return 'executing'
  return null
}

/** workflow_node_complete success → debugStatus */
export function eventSuccessToDebugStatus(success) {
  if (success === false) return 'fail'
  return 'success'
}

/**
 * @param {Array} nodeSpans trace 中的 node spans
 * @returns {Record<string, { debugStatus?: string, durationMs?: number }>}
 */
export function spansToNodeStates(nodeSpans) {
  const map = {}
  for (const span of nodeSpans || []) {
    const nodeId = span.spanId?.replace(/^node:/, '')
    if (!nodeId) continue
    map[nodeId] = {
      debugStatus: spanStatusToDebugStatus(span.status),
      durationMs: span.durationMs,
    }
  }
  return map
}

/**
 * @param {Array} nodeEvents 工作流 SSE / 测试 nodeEvents
 * @returns {Record<string, { debugStatus?: string, durationMs?: number }>}
 */
export function eventsToNodeStates(nodeEvents) {
  const map = {}
  for (const ev of nodeEvents || []) {
    if (ev.type === 'workflow_node_start' && ev.nodeId) {
      map[ev.nodeId] = { ...map[ev.nodeId], debugStatus: 'executing' }
    }
    if (ev.type === 'workflow_node_complete' && ev.nodeId) {
      map[ev.nodeId] = {
        debugStatus: ev.suspended ? 'executing' : eventSuccessToDebugStatus(ev.success),
        durationMs: ev.durationMs,
      }
    }
  }
  return map
}

/**
 * 已执行节点之间的边 ID 集合（用于高亮路径）
 * @param {Array} wfEdges
 * @param {string[]} executedNodeIds
 * @returns {Set<string>}
 */
export function buildExecutedEdgeIds(wfEdges, executedNodeIds) {
  const executed = new Set(executedNodeIds || [])
  return new Set(
    (wfEdges || [])
      .filter(e => executed.has(e.source) && executed.has(e.target))
      .map(e => e.id || `${e.source}->${e.target}`),
  )
}

/**
 * 将 nodeStates 合并进 Vue Flow 节点 data
 * @param {Array} nodes
 * @param {Record<string, object>} nodeStates
 * @param {string|null} selectedNodeId 画布节点 id（非 spanId）
 */
export function mergeNodeStates(nodes, nodeStates, selectedNodeId = null) {
  if (!nodes?.length) return []
  return nodes.map(n => {
    const state = nodeStates?.[n.id] || {}
    return {
      ...n,
      selected: selectedNodeId != null && n.id === selectedNodeId,
      data: {
        ...n.data,
        ...(state.debugStatus ? { debugStatus: state.debugStatus } : {}),
        ...(state.durationMs != null ? { runDurationMs: state.durationMs } : {}),
      },
    }
  })
}

/**
 * 高亮已走过的边
 * @param {Array} edges
 * @param {Set<string>} highlightedEdgeIds
 */
export function highlightExecutedEdges(edges, highlightedEdgeIds) {
  if (!edges?.length || !highlightedEdgeIds?.size) return edges
  return edges.map(e => {
    const id = e.id || `${e.source}->${e.target}`
    if (!highlightedEdgeIds.has(id)) return e
    return {
      ...e,
      style: { ...(e.style || {}), stroke: '#1890ff', strokeWidth: 2.5 },
      animated: false,
    }
  })
}
