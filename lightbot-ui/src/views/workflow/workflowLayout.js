/**
 * 工作流布局：仅纠正弯折过大的连线，不做全量重排或磁吸对齐
 */

/** 中心 Y 偏差超过此值视为「弯得离谱」（约一个普通节点高度） */
export const EXTREME_BEND_DY = 96

const DEFAULT_W = 200
const DEFAULT_H = 96
const ROUND_SIZE = 64

function nodeSize(type) {
  if (type === 'start' || type === 'end') {
    return { w: ROUND_SIZE, h: ROUND_SIZE }
  }
  if (type === 'loop' || type === 'batch') {
    return { w: 560, h: 380 }
  }
  return { w: DEFAULT_W, h: DEFAULT_H }
}

function getParentId(node) {
  return node?.parentNode || node?.parentId || null
}

function normalizePoint(p) {
  return { x: Number(p?.x) || 0, y: Number(p?.y) || 0 }
}

function getNodeCenter(node) {
  const { w, h } = nodeSize(node?.type)
  const p = normalizePoint(node?.position)
  return { x: p.x + w / 2, y: p.y + h / 2, w, h }
}

function positionFromCenter(node, centerX, centerY) {
  const { w, h } = nodeSize(node?.type)
  return {
    ...node,
    position: {
      x: Math.round(centerX - w / 2),
      y: Math.round(centerY - h / 2),
    },
  }
}

function buildDegreeMaps(nodes, edges) {
  const outCount = Object.fromEntries(nodes.map(n => [n.id, 0]))
  const inCount = Object.fromEntries(nodes.map(n => [n.id, 0]))
  for (const e of edges || []) {
    if (outCount[e.source] != null) outCount[e.source]++
    if (inCount[e.target] != null) inCount[e.target]++
  }
  return { outCount, inCount }
}

/**
 * 仅调整串行链路上弯折过大的目标节点 Y（保持 X 不变）
 * @param {Array} nodes
 * @param {Array} edges
 * @param {number} threshold 中心 Y 偏差阈值
 */
export function correctExtremeEdgeBends(nodes, edges, threshold = EXTREME_BEND_DY) {
  if (!nodes?.length || !edges?.length) return nodes

  let result = nodes.map(n => ({
    ...n,
    position: normalizePoint(n.position),
  }))
  const { outCount, inCount } = buildDegreeMaps(result, edges)

  const getNode = id => result.find(n => n.id === id)

  for (let pass = 0; pass < result.length; pass++) {
    let changed = false
    for (const e of edges) {
      if (outCount[e.source] !== 1 || inCount[e.target] !== 1) continue

      const src = getNode(e.source)
      const tgt = getNode(e.target)
      if (!src || !tgt) continue
      if (getParentId(src) !== getParentId(tgt)) continue

      const sc = getNodeCenter(src)
      const tc = getNodeCenter(tgt)
      if (Math.abs(sc.y - tc.y) <= threshold) continue
      // 只处理从左到右的常规连线，避免误动回连/逆向边
      if (tc.x < sc.x - 40) continue

      const idx = result.findIndex(n => n.id === tgt.id)
      const updated = positionFromCenter(tgt, tc.x, sc.y)
      if (updated.position.y !== tgt.position.y) {
        result[idx] = updated
        changed = true
      }
    }
    if (!changed) break
  }

  return result
}

/** @deprecated 使用 correctExtremeEdgeBends */
export function layoutWorkflowNodes(nodes, edges) {
  return correctExtremeEdgeBends(nodes, edges)
}

/** 水平贝塞尔边 */
export function applyWorkflowBezierEdgeStyle(edges) {
  if (!edges?.length) return edges
  return edges.map(e => ({
    ...e,
    type: 'workflow-bezier',
    pathOptions: undefined,
    style: { ...(e.style || {}), strokeWidth: 2, stroke: '#94a3b8' },
  }))
}

/** @deprecated */
export function applySmoothstepEdgeStyle(edges) {
  return applyWorkflowBezierEdgeStyle(edges)
}

/** 自动整理：仅纠正极端弯折 + 统一边样式 */
export function applyWorkflowAutoLayout(nodes, edges) {
  return {
    nodes: correctExtremeEdgeBends(nodes, edges),
    edges: applyWorkflowBezierEdgeStyle(edges),
  }
}

/** @deprecated */
export function straightenWorkflowEdges(edges) {
  return applyWorkflowBezierEdgeStyle(edges)
}
