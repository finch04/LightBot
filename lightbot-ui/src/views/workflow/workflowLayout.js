/**
 * 工作流布局：基于 Sugiyama 框架的分层布局算法
 *
 * 4 阶段：分层 → 交叉减少 → 坐标分配 → 父子节点后处理
 * 特性：确定性、无重叠、最小化连线交叉、父子节点递归布局
 */

import {
  getNodeParentId,
  isGroupNodeType,
  isGroupBuiltinType,
  fitGroupBoundsToChildren,
  GROUP_INNER_PADDING,
  parseSize,
} from './workflowGroup'

// ==================== 常量 ====================

const DEFAULT_W = 200
const DEFAULT_H = 96
const ROUND_SIZE = 64
const LAYER_GAP_X = 160
const NODE_GAP_Y = 72
const TOP_ORIGIN = { x: 80, y: 120 }
const CROSSING_REDUCTION_ITERATIONS = 8

// ==================== 工具函数 ====================

function nodeSize(type) {
  if (type === 'start' || type === 'end') return { w: ROUND_SIZE, h: ROUND_SIZE }
  if (isGroupBuiltinType(type)) return { w: 128, h: 48 }
  if (type === 'loop' || type === 'batch') return { w: 560, h: 380 }
  return { w: DEFAULT_W, h: DEFAULT_H }
}

function getLayoutNodeSize(node) {
  if (isGroupNodeType(node?.type)) {
    const base = nodeSize(node.type)
    return { w: parseSize(node.style?.width, base.w), h: parseSize(node.style?.height, base.h) }
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

/** 确定性排序：按节点 ID 字典序 */
function deterministicSort(nodes) {
  return [...nodes].sort((a, b) => String(a.id).localeCompare(String(b.id)))
}

// ==================== 阶段 1：分层 ====================

/**
 * 最长路径分层：源节点在左，汇节点在右
 * 使用拓扑排序 + DP，保证确定性
 */
function assignLayers(nodeIds, edges) {
  const inEdges = new Map(nodeIds.map(id => [id, []]))
  const outEdges = new Map(nodeIds.map(id => [id, []]))
  for (const e of edges || []) {
    if (inEdges.has(e.target)) inEdges.get(e.target).push(e.source)
    if (outEdges.has(e.source)) outEdges.get(e.source).push(e.target)
  }

  // 拓扑排序（Kahn 算法），入度为 0 的按 ID 排序保证确定性
  const inDegree = new Map(nodeIds.map(id => [id, (inEdges.get(id) || []).length]))
  const queue = nodeIds.filter(id => inDegree.get(id) === 0).sort()
  const topoOrder = []
  while (queue.length) {
    const id = queue.shift()
    topoOrder.push(id)
    for (const succ of (outEdges.get(id) || []).sort()) {
      const deg = inDegree.get(succ) - 1
      inDegree.set(succ, deg)
      if (deg === 0) queue.push(succ)
    }
  }

  // 按拓扑序分配层级
  const layer = new Map()
  for (const id of topoOrder) {
    const preds = inEdges.get(id) || []
    let lv = 0
    for (const p of preds) {
      lv = Math.max(lv, (layer.get(p) ?? 0) + 1)
    }
    layer.set(id, lv)
  }

  // 未在拓扑序中的节点（孤立节点）设为层级 0
  for (const id of nodeIds) {
    if (!layer.has(id)) layer.set(id, 0)
  }

  return layer
}

// ==================== 阶段 2：交叉减少 ====================

/**
 * 计算两层之间的交叉数
 */
function countCrossings(layerAbove, layerBelow, edges, orderAbove, orderBelow) {
  const posAbove = new Map(orderAbove.map((id, i) => [id, i]))
  const posBelow = new Map(orderBelow.map((id, i) => [id, i]))

  // 收集跨层边
  const crossEdges = edges.filter(e => posAbove.has(e.source) && posBelow.has(e.target))

  let crossings = 0
  for (let i = 0; i < crossEdges.length; i++) {
    for (let j = i + 1; j < crossEdges.length; j++) {
      const a = posAbove.get(crossEdges[i].source)
      const b = posAbove.get(crossEdges[j].source)
      const c = posBelow.get(crossEdges[i].target)
      const d = posBelow.get(crossEdges[j].target)
      if ((a - b) * (c - d) < 0) crossings++
    }
  }
  return crossings
}

/**
 * 中位数启发式：按前驱位置的中位数排序
 */
function medianOrder(layerNodes, prevLayer, edges, prevOrder) {
  const prevPos = new Map(prevOrder.map((id, i) => [id, i]))
  const medianMap = new Map()

  for (const nodeId of layerNodes) {
    const preds = edges
      .filter(e => e.target === nodeId && prevPos.has(e.source))
      .map(e => prevPos.get(e.source))
      .sort((a, b) => a - b)

    if (preds.length === 0) {
      medianMap.set(nodeId, Number.MAX_SAFE_INTEGER)
    } else if (preds.length % 2 === 1) {
      medianMap.set(nodeId, preds[Math.floor(preds.length / 2)])
    } else {
      medianMap.set(nodeId, (preds[preds.length / 2 - 1] + preds[preds.length / 2]) / 2)
    }
  }

  return [...layerNodes].sort((a, b) => {
    const ma = medianMap.get(a)
    const mb = medianMap.get(b)
    if (ma !== mb) return ma - mb
    return String(a).localeCompare(String(b))
  })
}

/**
 * 重心启发式：按前驱位置的平均值排序
 */
function barycenterOrder(layerNodes, prevLayer, edges, prevOrder) {
  const prevPos = new Map(prevOrder.map((id, i) => [id, i]))
  const baryMap = new Map()

  for (const nodeId of layerNodes) {
    const preds = edges
      .filter(e => e.target === nodeId && prevPos.has(e.source))
      .map(e => prevPos.get(e.source))

    if (preds.length === 0) {
      baryMap.set(nodeId, Number.MAX_SAFE_INTEGER)
    } else {
      baryMap.set(nodeId, preds.reduce((s, v) => s + v, 0) / preds.length)
    }
  }

  return [...layerNodes].sort((a, b) => {
    const ba = baryMap.get(a)
    const bb = baryMap.get(b)
    if (ba !== bb) return ba - bb
    return String(a).localeCompare(String(b))
  })
}

/**
 * 交叉减少主函数：多轮上下扫描，选择最优排列
 */
function minimizeCrossings(layerMap, edges) {
  const sortedLayerKeys = [...layerMap.keys()].sort((a, b) => a - b)
  if (sortedLayerKeys.length <= 1) return layerMap

  // 初始化：每层按 ID 排序（确定性）
  let currentOrders = new Map()
  for (const lv of sortedLayerKeys) {
    currentOrders.set(lv, deterministicSort(layerMap.get(lv)).map(n => n.id))
  }

  let bestOrders = currentOrders
  let bestCrossings = 0

  // 计算初始交叉数
  for (let i = 0; i < sortedLayerKeys.length - 1; i++) {
    const lvA = sortedLayerKeys[i]
    const lvB = sortedLayerKeys[i + 1]
    bestCrossings += countCrossings(
      layerMap.get(lvA), layerMap.get(lvB), edges,
      currentOrders.get(lvA), currentOrders.get(lvB),
    )
  }

  for (let iter = 0; iter < CROSSING_REDUCTION_ITERATIONS; iter++) {
    const newOrders = new Map()

    if (iter % 2 === 0) {
      // 正向扫描：从第 1 层到最后一层
      newOrders.set(sortedLayerKeys[0], currentOrders.get(sortedLayerKeys[0]))
      for (let i = 1; i < sortedLayerKeys.length; i++) {
        const lv = sortedLayerKeys[i]
        const prevLv = sortedLayerKeys[i - 1]
        const fn = iter < CROSSING_REDUCTION_ITERATIONS / 2 ? medianOrder : barycenterOrder
        newOrders.set(lv, fn(
          layerMap.get(lv).map(n => n.id),
          layerMap.get(prevLv).map(n => n.id),
          edges,
          newOrders.get(prevLv),
        ))
      }
    } else {
      // 反向扫描：从最后一层到第 0 层
      const last = sortedLayerKeys.length - 1
      newOrders.set(sortedLayerKeys[last], currentOrders.get(sortedLayerKeys[last]))
      for (let i = last - 1; i >= 0; i--) {
        const lv = sortedLayerKeys[i]
        const nextLv = sortedLayerKeys[i + 1]
        const fn = iter < CROSSING_REDUCTION_ITERATIONS / 2 ? medianOrder : barycenterOrder
        newOrders.set(lv, fn(
          layerMap.get(lv).map(n => n.id),
          layerMap.get(nextLv).map(n => n.id),
          edges,
          newOrders.get(nextLv),
        ))
      }
    }

    // 计算新交叉数
    let crossings = 0
    for (let i = 0; i < sortedLayerKeys.length - 1; i++) {
      const lvA = sortedLayerKeys[i]
      const lvB = sortedLayerKeys[i + 1]
      crossings += countCrossings(
        layerMap.get(lvA), layerMap.get(lvB), edges,
        newOrders.get(lvA), newOrders.get(lvB),
      )
    }

    currentOrders = newOrders
    if (crossings < bestCrossings) {
      bestCrossings = crossings
      bestOrders = newOrders
    }

    // 已无交叉，提前终止
    if (bestCrossings === 0) break
  }

  // 按最优顺序重排 layerMap
  const result = new Map()
  for (const lv of sortedLayerKeys) {
    const order = bestOrders.get(lv)
    const nodeMap = new Map(layerMap.get(lv).map(n => [n.id, n]))
    result.set(lv, order.map(id => nodeMap.get(id)).filter(Boolean))
  }
  return result
}

// ==================== 阶段 3：坐标分配 ====================

/**
 * 坐标分配：垂直排列 + 中位数对齐 + 重叠消除
 */
function assignCoordinates(layerMap, edges, nodeMap, origin) {
  const sortedLayerKeys = [...layerMap.keys()].sort((a, b) => a - b)
  const positions = {}

  // 构建前驱/后继映射
  const predsOf = new Map()
  const succsOf = new Map()
  for (const lv of sortedLayerKeys) {
    for (const node of layerMap.get(lv)) {
      predsOf.set(node.id, [])
      succsOf.set(node.id, []
      )
    }
  }
  for (const e of edges) {
    if (predsOf.has(e.target)) predsOf.get(e.target).push(e.source)
    if (succsOf.has(e.source)) succsOf.get(e.source).push(e.target)
  }

  const layerOf = new Map()
  for (const lv of sortedLayerKeys) {
    for (const node of layerMap.get(lv)) {
      layerOf.set(node.id, lv)
    }
  }

  // 初始放置：按排序顺序垂直排列
  let currentX = origin.x
  for (const lv of sortedLayerKeys) {
    const nodes = layerMap.get(lv)
    let currentY = origin.y

    for (const node of nodes) {
      const { h } = getLayoutNodeSize(node)
      positions[node.id] = { x: Math.round(currentX), y: Math.round(currentY) }
      currentY += h + NODE_GAP_Y
    }

    // 计算下一层的 X
    const maxW = Math.max(...nodes.map(n => getLayoutNodeSize(n).w), DEFAULT_W)
    currentX += maxW + LAYER_GAP_X
  }

  // 中位数对齐：单入单出串行链 Y 对齐
  for (let pass = 0; pass < sortedLayerKeys.length; pass++) {
    let changed = false
    for (const e of edges) {
      if (!positions[e.source] || !positions[e.target]) continue
      if (predsOf.get(e.target)?.length !== 1) continue

      const srcNode = nodeMap.get(e.source)
      const tgtNode = nodeMap.get(e.target)
      if (!srcNode || !tgtNode) continue

      const srcCy = positions[e.source].y + getLayoutNodeSize(srcNode).h / 2
      const { h: th } = getLayoutNodeSize(tgtNode)
      const newY = Math.round(srcCy - th / 2)

      if (positions[e.target].y !== newY) {
        positions[e.target] = { ...positions[e.target], y: newY }
        changed = true
      }
    }
    if (!changed) break
  }

  // 多前驱节点：Y 对齐到前驱中心的中位数
  for (const lv of sortedLayerKeys) {
    for (const node of layerMap.get(lv)) {
      const preds = predsOf.get(node.id) || []
      if (preds.length <= 1) continue

      const predCenters = preds
        .filter(pid => positions[pid])
        .map(pid => {
          const pn = nodeMap.get(pid)
          return positions[pid].y + getLayoutNodeSize(pn).h / 2
        })
        .sort((a, b) => a - b)

      if (predCenters.length === 0) continue

      const medianCy = predCenters.length % 2 === 1
        ? predCenters[Math.floor(predCenters.length / 2)]
        : (predCenters[predCenters.length / 2 - 1] + predCenters[predCenters.length / 2]) / 2

      const { h } = getLayoutNodeSize(node)
      positions[node.id] = { ...positions[node.id], y: Math.round(medianCy - h / 2) }
    }
  }

  // 重叠消除：从上到下扫描每层
  resolveLayerOverlaps(sortedLayerKeys, layerMap, positions, nodeMap)

  // 反向扫描优化
  for (let i = sortedLayerKeys.length - 2; i >= 0; i--) {
    const lv = sortedLayerKeys[i]
    const nextLv = sortedLayerKeys[i + 1]
    const nodes = layerMap.get(lv)
    if (!nodes || nodes.length <= 1) continue

    // 检查是否可以向下移动以拉直连线
    for (const node of nodes) {
      const succs = succsOf.get(node.id) || []
      if (succs.length !== 1) continue

      const succNode = nodeMap.get(succs[0])
      if (!succNode || !positions[succs[0]]) continue

      const succCy = positions[succs[0]].y + getLayoutNodeSize(succNode).h / 2
      const { h } = getLayoutNodeSize(node)
      const newY = Math.round(succCy - h / 2)

      // 检查移动后是否与同层其他节点重叠
      const nodeIdx = nodes.indexOf(node)
      let canMove = true

      if (nodeIdx > 0) {
        const prevNode = nodes[nodeIdx - 1]
        const pp = positions[prevNode.id]
        if (pp) {
          const minTop = pp.y + getLayoutNodeSize(prevNode).h + NODE_GAP_Y
          if (newY < minTop) canMove = false
        }
      }

      if (nodeIdx < nodes.length - 1) {
        const nextNode = nodes[nodeIdx + 1]
        const np = positions[nextNode.id]
        if (np) {
          const maxTop = np.y - h - NODE_GAP_Y
          if (newY > maxTop) canMove = false
        }
      }

      if (canMove) {
        positions[node.id] = { ...positions[node.id], y: newY }
      }
    }
  }

  // 最终重叠消除
  resolveLayerOverlaps(sortedLayerKeys, layerMap, positions, nodeMap)

  return positions
}

/** 同层节点 Y 重叠时向下推开（确定性：按当前 Y 排序） */
function resolveLayerOverlaps(sortedLayerKeys, layerMap, positions, nodeMap) {
  for (const lv of sortedLayerKeys) {
    const nodes = layerMap.get(lv)
    if (!nodes || nodes.length <= 1) continue

    const sorted = [...nodes].sort((a, b) => {
      const ya = positions[a.id]?.y ?? 0
      const yb = positions[b.id]?.y ?? 0
      if (ya !== yb) return ya - yb
      return String(a.id).localeCompare(String(b.id))
    })

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

// ==================== 阶段 4：父子节点后处理 ====================

/**
 * 对同一 parent 作用域内的节点做完整 Sugiyama 布局
 */
function layoutNodesInScope(scopeNodes, edges, origin) {
  if (!scopeNodes?.length) return {}

  const nodeIds = scopeNodes.map(n => n.id)
  const scopedEdges = scopeEdges(edges, nodeIds)
  const nodeMap = new Map(scopeNodes.map(n => [n.id, n]))

  // 阶段 1：分层
  const layerById = assignLayers(nodeIds, scopedEdges)

  // 按层级分组
  const layerMap = new Map()
  for (const n of scopeNodes) {
    const lv = layerById.get(n.id) ?? 0
    if (!layerMap.has(lv)) layerMap.set(lv, [])
    layerMap.get(lv).push(n)
  }

  // 阶段 2：交叉减少
  const optimizedLayers = minimizeCrossings(layerMap, scopedEdges)

  // 阶段 3：坐标分配
  return assignCoordinates(optimizedLayers, scopedEdges, nodeMap, origin)
}

/**
 * 格式化工作流：先整理容器内子节点，再整理顶层节点
 * 对外接口保持不变
 */
export function formatWorkflowLayout(nodes, edges) {
  if (!nodes?.length) return nodes

  let result = nodes.map(n => ({
    ...n,
    position: normalizePoint(n.position),
  }))

  const edgeList = edges || []

  // 1. 容器内子节点（相对坐标）— 递归处理
  for (const group of deterministicSort(result.filter(n => isGroupNodeType(n.type)))) {
    const children = result.filter(n => getNodeParentId(n) === group.id)
    if (!children.length) continue

    const positions = layoutNodesInScope(
      children,
      scopeEdges(edgeList, children.map(c => c.id)),
      { x: GROUP_INNER_PADDING.left, y: GROUP_INNER_PADDING.top },
    )

    result = result.map(n => (positions[n.id] ? { ...n, position: positions[n.id] } : n))

    // 容器尺寸自动调整
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

/** 格式化布局 + 统一边样式 */
export function applyWorkflowAutoLayout(nodes, edges) {
  return {
    nodes: formatWorkflowLayout(nodes, edges),
    edges: applyWorkflowBezierEdgeStyle(edges),
  }
}

/** @deprecated 使用 formatWorkflowLayout */
export function correctExtremeEdgeBends(nodes, edges) {
  return formatWorkflowLayout(nodes, edges)
}

/** @deprecated */
export function layoutWorkflowNodes(nodes, edges) {
  return formatWorkflowLayout(nodes, edges)
}

/** @deprecated */
export function applySmoothstepEdgeStyle(edges) {
  return applyWorkflowBezierEdgeStyle(edges)
}

/** @deprecated */
export function straightenWorkflowEdges(edges) {
  return applyWorkflowBezierEdgeStyle(edges)
}

/** @deprecated 保留兼容 */
export const EXTREME_BEND_DY = 96
