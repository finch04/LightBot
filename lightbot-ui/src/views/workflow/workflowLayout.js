/**
 * 工作流布局：分层排列 + 对齐前驱 Y，使连线尽量水平、节点间距适中
 */

import {
  getNodeParentId,
  isGroupNodeType,
  isGroupBuiltinType,
  fitGroupBoundsToChildren,
  GROUP_INNER_PADDING,
  parseSize,
} from './workflowGroup'

/** @deprecated 保留兼容 */
export const EXTREME_BEND_DY = 96

const DEFAULT_W = 200
const DEFAULT_H = 96
const ROUND_SIZE = 64
const LAYER_GAP_X = 160
const NODE_GAP_Y = 72
const TOP_ORIGIN = { x: 80, y: 120 }

function nodeSize(type) {
  if (type === 'start' || type === 'end') {
    return { w: ROUND_SIZE, h: ROUND_SIZE }
  }
  if (isGroupBuiltinType(type)) {
    return { w: 128, h: 48 }
  }
  if (type === 'loop' || type === 'batch') {
    return { w: 560, h: 380 }
  }
  return { w: DEFAULT_W, h: DEFAULT_H }
}

function getLayoutNodeSize(node) {
  if (isGroupNodeType(node?.type)) {
    const base = nodeSize(node.type)
    return {
      w: parseSize(node.style?.width, base.w),
      h: parseSize(node.style?.height, base.h),
    }
  }
  return nodeSize(node?.type)
}

function normalizePoint(p) {
  return { x: Number(p?.x) || 0, y: Number(p?.y) || 0 }
}

function scopeEdges(edges, scopeIds) {
  const set = new Set(scopeIds)
  return (edges || []).filter(e => set.has(e.source) && set.has(e.target))
}

function buildDegreeMaps(nodeIds, edges) {
  const outCount = Object.fromEntries(nodeIds.map(id => [id, 0]))
  const inCount = Object.fromEntries(nodeIds.map(id => [id, 0]))
  for (const e of edges || []) {
    if (outCount[e.source] != null) outCount[e.source]++
    if (inCount[e.target] != null) inCount[e.target]++
  }
  return { outCount, inCount }
}

function getCenterY(nodeId, positions, nodeMap) {
  const node = nodeMap.get(nodeId)
  const pos = positions[nodeId]
  if (!node || !pos) return 0
  const { h } = getLayoutNodeSize(node)
  return pos.y + h / 2
}

/** 最长路径分层：源节点在左，汇节点在右 */
function assignLayers(nodeIds, edges) {
  const inEdges = new Map(nodeIds.map(id => [id, []]))
  for (const e of edges || []) {
    if (!inEdges.has(e.target)) continue
    inEdges.get(e.target).push(e.source)
  }

  const layer = new Map()
  const visiting = new Set()

  function resolveLayer(id) {
    if (layer.has(id)) return layer.get(id)
    if (visiting.has(id)) return 0
    visiting.add(id)
    const preds = inEdges.get(id) || []
    let lv = 0
    if (preds.length) {
      lv = Math.max(...preds.map(p => resolveLayer(p) + 1))
    }
    visiting.delete(id)
    layer.set(id, lv)
    return lv
  }

  for (const id of nodeIds) resolveLayer(id)
  return layer
}

function isScopeSourceNode(node) {
  return node.type === 'start'
    || node.type === 'loop_start'
    || node.type === 'batch_start'
}

/** 根据前驱节点中心 Y 计算期望 Y，串行边尽量拉直 */
function resolveDesiredCenterY(node, layerIndex, layerById, edges, positions, nodeMap, origin) {
  const preds = (edges || [])
    .filter(e => e.target === node.id)
    .map(e => e.source)
    .filter(id => (layerById.get(id) ?? 0) < layerIndex && positions[id])

  if (preds.length === 1) {
    return getCenterY(preds[0], positions, nodeMap)
  }
  if (preds.length > 1) {
    const sum = preds.reduce((acc, id) => acc + getCenterY(id, positions, nodeMap), 0)
    return sum / preds.length
  }
  if (isScopeSourceNode(node)) {
    return origin.y + getLayoutNodeSize(node).h / 2
  }
  return origin.y + getLayoutNodeSize(node).h / 2
}

/** 同层节点按期望 Y 排序并错开，避免重叠 */
function placeLayerNodes(layerNodes, layerIndex, layerById, edges, positions, nodeMap, origin, baseX) {
  if (!layerNodes.length) return baseX

  const maxW = Math.max(...layerNodes.map(n => getLayoutNodeSize(n).w), DEFAULT_W)
  const planned = layerNodes.map(node => {
    const { h } = getLayoutNodeSize(node)
    const desiredCy = resolveDesiredCenterY(node, layerIndex, layerById, edges, positions, nodeMap, origin)
    return { node, h, desiredTop: desiredCy - h / 2 }
  })

  planned.sort((a, b) => a.desiredTop - b.desiredTop || String(a.node.id).localeCompare(String(b.node.id)))

  let cursorY = planned[0].desiredTop
  for (const item of planned) {
    const top = Math.max(Math.round(item.desiredTop), Math.round(cursorY))
    positions[item.node.id] = { x: Math.round(baseX), y: top }
    cursorY = top + item.h + NODE_GAP_Y
  }

  return baseX + maxW + LAYER_GAP_X
}

/** 串行链路强制 Y 对齐，消除残余弯折 */
function alignSerialChains(scopeNodes, edges, positions, nodeMap) {
  const nodeIds = scopeNodes.map(n => n.id)
  const scopedEdges = scopeEdges(edges, nodeIds)
  const { outCount, inCount } = buildDegreeMaps(nodeIds, scopedEdges)

  for (let pass = 0; pass < nodeIds.length; pass++) {
    let changed = false
    for (const e of scopedEdges) {
      if (outCount[e.source] !== 1 || inCount[e.target] !== 1) continue
      const src = nodeMap.get(e.source)
      const tgt = nodeMap.get(e.target)
      const sp = positions[e.source]
      const tp = positions[e.target]
      if (!src || !tgt || !sp || !tp) continue

      const srcCy = sp.y + getLayoutNodeSize(src).h / 2
      const { h: th } = getLayoutNodeSize(tgt)
      const nextY = Math.round(srcCy - th / 2)
      if (tp.y !== nextY) {
        positions[e.target] = { ...tp, y: nextY }
        changed = true
      }
    }
    if (!changed) break
  }

  resolveLayerOverlaps(scopeNodes, assignLayers(nodeIds, scopedEdges), positions, nodeMap)
}

/** 同层节点 Y 重叠时向下推开 */
function resolveLayerOverlaps(scopeNodes, layerById, positions, nodeMap) {
  const layers = new Map()
  for (const n of scopeNodes) {
    const lv = layerById.get(n.id) ?? 0
    if (!layers.has(lv)) layers.set(lv, [])
    layers.get(lv).push(n)
  }

  for (const layerNodes of layers.values()) {
    if (layerNodes.length <= 1) continue
    const sorted = [...layerNodes].sort((a, b) => (positions[a.id]?.y ?? 0) - (positions[b.id]?.y ?? 0))
    for (let i = 1; i < sorted.length; i++) {
      const prev = sorted[i - 1]
      const curr = sorted[i]
      const pp = positions[prev.id]
      const cp = positions[curr.id]
      if (!pp || !cp) continue
      const minTop = pp.y + getLayoutNodeSize(prev).h + NODE_GAP_Y
      if (cp.y < minTop) {
        positions[curr.id] = { ...cp, y: minTop }
      }
    }
  }
}

/**
 * 对同一 parent 作用域内的节点做分层布局
 * @returns {Record<string, {x:number,y:number}>}
 */
function layoutNodesInScope(scopeNodes, edges, origin) {
  if (!scopeNodes?.length) return {}

  const nodeMap = new Map(scopeNodes.map(n => [n.id, n]))
  const nodeIds = scopeNodes.map(n => n.id)
  const scopedEdges = scopeEdges(edges, nodeIds)
  const layerById = assignLayers(nodeIds, scopedEdges)

  const layers = new Map()
  for (const n of scopeNodes) {
    const lv = layerById.get(n.id) ?? 0
    if (!layers.has(lv)) layers.set(lv, [])
    layers.get(lv).push(n)
  }

  const positions = {}
  const sortedLayerKeys = [...layers.keys()].sort((a, b) => a - b)
  let currentX = origin.x

  for (const lv of sortedLayerKeys) {
    currentX = placeLayerNodes(
      layers.get(lv) || [],
      lv,
      layerById,
      scopedEdges,
      positions,
      nodeMap,
      origin,
      currentX,
    )
  }

  alignSerialChains(scopeNodes, edges, positions, nodeMap)
  return positions
}

/**
 * 格式化工作流：先整理容器内子节点，再整理顶层节点
 */
export function formatWorkflowLayout(nodes, edges) {
  if (!nodes?.length) return nodes

  let result = nodes.map(n => ({
    ...n,
    position: normalizePoint(n.position),
  }))

  const edgeList = edges || []

  // 1. 容器内子节点（相对坐标）
  for (const group of result.filter(n => isGroupNodeType(n.type))) {
    const children = result.filter(n => getNodeParentId(n) === group.id)
    if (!children.length) continue

    const positions = layoutNodesInScope(
      children,
      scopeEdges(edgeList, children.map(c => c.id)),
      { x: GROUP_INNER_PADDING.left, y: GROUP_INNER_PADDING.top },
    )

    result = result.map(n => (positions[n.id] ? { ...n, position: positions[n.id] } : n))
    const resized = fitGroupBoundsToChildren(group, result, null)
    result = result.map(n => (n.id === group.id ? resized : n))
  }

  // 2. 顶层节点（绝对坐标）
  const topNodes = result.filter(n => !getNodeParentId(n))
  const topPositions = layoutNodesInScope(
    topNodes,
    scopeEdges(edgeList, topNodes.map(n => n.id)),
    TOP_ORIGIN,
  )

  result = result.map(n => (topPositions[n.id] ? { ...n, position: topPositions[n.id] } : n))
  return result
}

/** @deprecated 使用 formatWorkflowLayout */
export function correctExtremeEdgeBends(nodes, edges) {
  return formatWorkflowLayout(nodes, edges)
}

/** @deprecated */
export function layoutWorkflowNodes(nodes, edges) {
  return formatWorkflowLayout(nodes, edges)
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

/** 格式化布局 + 统一边样式 */
export function applyWorkflowAutoLayout(nodes, edges) {
  return {
    nodes: formatWorkflowLayout(nodes, edges),
    edges: applyWorkflowBezierEdgeStyle(edges),
  }
}

/** @deprecated */
export function straightenWorkflowEdges(edges) {
  return applyWorkflowBezierEdgeStyle(edges)
}
