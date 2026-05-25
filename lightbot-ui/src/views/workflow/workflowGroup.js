/**
 * 循环 / 批处理容器节点（对齐 spring-ai-alibaba-admin Iterator / Parallel）
 */

export const GROUP_NODE_TYPES = new Set(['loop', 'batch'])

/** 容器内置系统节点（不可单独删除） */
export const GROUP_BUILTIN_TYPES = new Set(['loop_start', 'loop_end', 'batch_start', 'batch_end'])

export const GROUP_DEFAULT_SIZE = {
  loop: { width: 560, height: 380 },
  batch: { width: 560, height: 380 },
}

const BUILTIN_NODE_SIZE = { width: 128, height: 48 }
const DEFAULT_NODE_SIZE = { width: 200, height: 88 }

/** 容器内禁止放入的节点类型 */
export const GROUP_CHILD_BLOCK_TYPES = new Set([
  'start', 'end', 'loop', 'batch',
  'loop_start', 'loop_end', 'batch_start', 'batch_end',
])

export const GROUP_HEADER_HEIGHT = 48
export const GROUP_INNER_PADDING = { top: 56, left: 24, right: 24, bottom: 24 }

export function isGroupNodeType(type) {
  return GROUP_NODE_TYPES.has(type)
}

export function isGroupBuiltinType(type) {
  return GROUP_BUILTIN_TYPES.has(type)
}

export function getGroupBuiltinPair(groupType) {
  if (groupType === 'loop') {
    return {
      start: 'loop_start',
      end: 'loop_end',
      startLabel: '迭代开始',
      endLabel: '迭代结束',
    }
  }
  return {
    start: 'batch_start',
    end: 'batch_end',
    startLabel: '并行处理',
    endLabel: '并行结束',
  }
}

export function parseSize(value, fallback) {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string') {
    const n = parseFloat(value)
    if (Number.isFinite(n)) return n
  }
  return fallback
}

export function getGroupDefaultStyle(type) {
  const size = GROUP_DEFAULT_SIZE[type] || GROUP_DEFAULT_SIZE.loop
  return { width: `${size.width}px`, height: `${size.height}px` }
}

/** 解析容器在画布上的绝对边界（优先 Vue Flow computedPosition / dimensions） */
export function resolveGroupBounds(group, findNode) {
  if (!group) return null
  const size = GROUP_DEFAULT_SIZE[group.type] || GROUP_DEFAULT_SIZE.loop
  const graphNode = findNode?.(group.id)
  const pos = graphNode?.computedPosition || group.position || { x: 0, y: 0 }
  let w = parseSize(group.style?.width, size.width)
  let h = parseSize(group.style?.height, size.height)
  if (graphNode?.dimensions?.width) w = graphNode.dimensions.width
  if (graphNode?.dimensions?.height) h = graphNode.dimensions.height
  return { x: pos.x, y: pos.y, width: w, height: h }
}

export function getGroupBounds(node, findNode) {
  return resolveGroupBounds(node, findNode)
}

/** 判断坐标是否在容器内容区内 */
export function isPointInsideGroupInner(group, point, findNode) {
  if (!group || !point) return false
  const b = resolveGroupBounds(group, findNode)
  if (!b) return false
  return (
    point.x >= b.x + GROUP_INNER_PADDING.left
    && point.x <= b.x + b.width - GROUP_INNER_PADDING.right
    && point.y >= b.y + GROUP_INNER_PADDING.top
    && point.y <= b.y + b.height - GROUP_INNER_PADDING.bottom
  )
}

/** 节点是否与同一容器内的其他子节点存在连线 */
export function hasEdgesToGroupSiblings(nodeId, groupId, nodeList, edgeList) {
  const siblingIds = new Set(
    (nodeList || [])
      .filter(n => getNodeParentId(n) === groupId && n.id !== nodeId)
      .map(n => n.id),
  )
  if (!siblingIds.size) return false
  for (const e of edgeList || []) {
    if (e.source === nodeId && siblingIds.has(e.target)) return true
    if (e.target === nodeId && siblingIds.has(e.source)) return true
  }
  return false
}

/** 节点中心点 → 画布绝对左上角 */
export function absoluteTopLeftFromCenter(node, center, findNode) {
  const dims = getChildNodeDimensions(node, findNode)
  return {
    x: center.x - dims.w / 2,
    y: center.y - dims.h / 2,
  }
}

/** 判断画布坐标是否落在某容器内（用于拖入） */
export function findGroupAtPoint(nodes, point, { excludeId, findNode } = {}) {
  if (!point) return null
  const groups = nodes.filter(n => isGroupNodeType(n.type) && n.id !== excludeId)
  for (let i = groups.length - 1; i >= 0; i--) {
    const g = groups[i]
    const b = resolveGroupBounds(g, findNode)
    if (!b) continue
    if (
      point.x >= b.x + GROUP_INNER_PADDING.left
      && point.x <= b.x + b.width - GROUP_INNER_PADDING.right
      && point.y >= b.y + GROUP_INNER_PADDING.top
      && point.y <= b.y + b.height - GROUP_INNER_PADDING.bottom
    ) {
      return g
    }
  }
  return null
}

/** 根据节点包围盒中心/角点查找可落入的容器 */
export function findGroupForNode(nodes, node, { excludeId, findNode } = {}) {
  if (!node) return null
  const center = getNodeDropPoint(node, nodes, findNode)
  let group = findGroupAtPoint(nodes, center, { excludeId, findNode })
  if (group) return group

  const abs = getAbsolutePosition(node, nodes, findNode)
  const gn = findNode?.(node.id)
  const w = gn?.dimensions?.width
    || (isGroupBuiltinType(node.type) ? BUILTIN_NODE_SIZE.width : DEFAULT_NODE_SIZE.width)
  const h = gn?.dimensions?.height
    || (isGroupBuiltinType(node.type) ? BUILTIN_NODE_SIZE.height : DEFAULT_NODE_SIZE.height)
  const probes = [
    { x: abs.x, y: abs.y },
    { x: abs.x + w, y: abs.y },
    { x: abs.x, y: abs.y + h },
    { x: abs.x + w, y: abs.y + h },
  ]
  for (const p of probes) {
    group = findGroupAtPoint(nodes, p, { excludeId, findNode })
    if (group) return group
  }
  return null
}

export function getNodeDropPoint(node, nodes, findNode) {
  const abs = getAbsolutePosition(node, nodes, findNode)
  const gn = findNode?.(node.id)
  let w = gn?.dimensions?.width
  let h = gn?.dimensions?.height
  if (!w) {
    if (isGroupBuiltinType(node.type)) w = BUILTIN_NODE_SIZE.width
    else if (isGroupNodeType(node.type)) w = GROUP_DEFAULT_SIZE[node.type]?.width || DEFAULT_NODE_SIZE.width
    else w = DEFAULT_NODE_SIZE.width
  }
  if (!h) {
    if (isGroupBuiltinType(node.type)) h = BUILTIN_NODE_SIZE.height
    else if (isGroupNodeType(node.type)) h = GROUP_DEFAULT_SIZE[node.type]?.height || DEFAULT_NODE_SIZE.height
    else h = DEFAULT_NODE_SIZE.height
  }
  return { x: abs.x + w / 2, y: abs.y + h / 2 }
}

export function canBeGroupChild(nodeType) {
  return nodeType && !GROUP_CHILD_BLOCK_TYPES.has(nodeType)
}

function getChildNodeDimensions(node, findNode) {
  const gn = findNode?.(node?.id)
  if (gn?.dimensions?.width && gn?.dimensions?.height) {
    return { w: gn.dimensions.width, h: gn.dimensions.height }
  }
  if (isGroupBuiltinType(node?.type)) {
    return { w: BUILTIN_NODE_SIZE.width, h: BUILTIN_NODE_SIZE.height }
  }
  if (node?.type === 'start' || node?.type === 'end') return { w: 64, h: 64 }
  return { w: DEFAULT_NODE_SIZE.width, h: DEFAULT_NODE_SIZE.height }
}

/** 循环/批处理不能放入另一容器（禁止嵌套） */
export function isGroupNestedDrop(nodeType, nodes, point, findNode) {
  if (!isGroupNodeType(nodeType) || !point) return false
  return !!findGroupAtPoint(nodes, point, { findNode })
}

/** 父节点必须在子节点之前，否则 Vue Flow 无法正确渲染嵌套 */
export function sortNodesParentFirst(nodeList) {
  if (!nodeList?.length) return []
  const out = []
  const seen = new Set()

  function appendTree(root) {
    if (!root || seen.has(root.id)) return
    seen.add(root.id)
    out.push(root)
    nodeList
      .filter(n => getNodeParentId(n) === root.id)
      .forEach(child => appendTree(child))
  }

  nodeList.filter(n => !getNodeParentId(n)).forEach(appendTree)
  nodeList.forEach(n => {
    if (!seen.has(n.id)) out.push(n)
  })
  return out
}

/** 根据子节点范围撑开容器，实现「大框包住小节点」 */
export function fitGroupBoundsToChildren(group, allNodes, findNode) {
  if (!group) return group
  const children = allNodes.filter(n => getNodeParentId(n) === group.id)
  if (!children.length) return group

  const sizeDefault = GROUP_DEFAULT_SIZE[group.type] || GROUP_DEFAULT_SIZE.loop
  let maxX = 0
  let maxY = 0
  for (const c of children) {
    const p = normalizePoint(c.position)
    const d = getChildNodeDimensions(c, findNode)
    maxX = Math.max(maxX, p.x + d.w)
    maxY = Math.max(maxY, p.y + d.h)
  }

  const w = Math.max(sizeDefault.width, maxX + GROUP_INNER_PADDING.right)
  const h = Math.max(sizeDefault.height, maxY + GROUP_INNER_PADDING.bottom)
  return {
    ...group,
    style: {
      ...(group.style || {}),
      width: `${Math.ceil(w)}px`,
      height: `${Math.ceil(h)}px`,
    },
  }
}

/** 将节点挂到容器下（相对坐标）；absolutePoint 为节点中心点画布坐标 */
export function attachNodeToGroup(node, group, absolutePoint, findNode) {
  const b = resolveGroupBounds(group, findNode)
  const dims = getChildNodeDimensions(node, findNode)
  const center = absolutePoint || {
    x: b.x + (node.position?.x ?? GROUP_INNER_PADDING.left) + dims.w / 2,
    y: b.y + (node.position?.y ?? GROUP_INNER_PADDING.top) + dims.h / 2,
  }
  const relX = Math.max(12, center.x - b.x - dims.w / 2)
  const relY = Math.max(GROUP_HEADER_HEIGHT + 8, center.y - b.y - dims.h / 2)
  return {
    ...node,
    parentNode: group.id,
    extent: 'parent',
    expandParent: true,
    zIndex: 10,
    position: { x: relX, y: relY },
  }
}

/** 挂入容器并撑开父节点尺寸 */
export function attachNodeToGroupAndResize(node, group, allNodes, absolutePoint, findNode) {
  if (!group) return { node, group: null }
  const attached = attachNodeToGroup(node, group, absolutePoint, findNode)
  const resizedGroup = fitGroupBoundsToChildren(group, [
    ...allNodes.filter(n => n.id !== node.id),
    attached,
  ], findNode)
  return { node: attached, group: resizedGroup }
}

export function detachFromGroup(node) {
  if (!node?.parentNode) return node
  const { parentNode, extent, expandParent, ...rest } = node
  return rest
}

function normalizePoint(pos) {
  return { x: Number(pos?.x ?? 100), y: Number(pos?.y ?? 100) }
}

function createBuiltinChild(groupId, type, label, position) {
  return {
    id: `${groupId}_${type}`,
    type,
    parentNode: groupId,
    extent: 'parent',
    expandParent: true,
    draggable: true,
    selectable: true,
    zIndex: 10,
    data: { label, builtin: true, groupId },
    position: normalizePoint(position),
  }
}

/** 创建容器并附带默认「开始/结束」子节点与连线 */
export function createGroupWithBuiltins(type, position, data) {
  const groupId = `node_${Date.now()}`
  const { start, end, startLabel, endLabel } = getGroupBuiltinPair(type)
  const group = {
    id: groupId,
    type,
    position: normalizePoint(position),
    style: getGroupDefaultStyle(type),
    data: data || {},
    zIndex: 0,
    selectable: true,
    draggable: true,
  }
  const startNode = createBuiltinChild(groupId, start, startLabel, { x: 56, y: 108 })
  const endNode = createBuiltinChild(groupId, end, endLabel, { x: 320, y: 108 })
  // 开始/结束默认不连线，由用户在容器内自行编排
  return { group, children: [startNode, endNode], edge: null }
}

/** @deprecated 请使用 createGroupWithBuiltins */
export function createGroupNode(type, position, data) {
  return createGroupWithBuiltins(type, position, data).group
}

/** 加载旧图时补齐容器内置节点 */
export function ensureGroupBuiltins(nodeList, edgeList = []) {
  const nodes = [...(nodeList || [])]
  let edges = [...(edgeList || [])]
  const groups = nodes.filter(n => isGroupNodeType(n.type))

  for (const g of groups) {
    const { start, end, startLabel, endLabel } = getGroupBuiltinPair(g.type)
    let startNode = nodes.find(n => getNodeParentId(n) === g.id && n.type === start)
    let endNode = nodes.find(n => getNodeParentId(n) === g.id && n.type === end)

    if (!startNode) {
      startNode = createBuiltinChild(g.id, start, startLabel, { x: 56, y: 108 })
      nodes.push(startNode)
    } else {
      const idx = nodes.findIndex(n => n.id === startNode.id)
      nodes[idx] = {
        ...startNode,
        parentNode: g.id,
        extent: 'parent',
        draggable: true,
        zIndex: 10,
        data: { ...startNode.data, label: startNode.data?.label || startLabel, builtin: true, groupId: g.id },
      }
      startNode = nodes[idx]
    }

    if (!endNode) {
      endNode = createBuiltinChild(g.id, end, endLabel, { x: 320, y: 108 })
      nodes.push(endNode)
    } else {
      const idx = nodes.findIndex(n => n.id === endNode.id)
      nodes[idx] = {
        ...endNode,
        parentNode: g.id,
        extent: 'parent',
        draggable: true,
        zIndex: 10,
        data: { ...endNode.data, label: endNode.data?.label || endLabel, builtin: true, groupId: g.id },
      }
      endNode = nodes[idx]
    }

    // 不再自动补 start→end 连线
  }

  return { nodes: sortNodesParentFirst(nodes), edges }
}

export function migrateGroupNodeFields(node) {
  if (!isGroupNodeType(node.type)) return node
  const size = GROUP_DEFAULT_SIZE[node.type]
  const style = node.style || {}
  return {
    ...node,
    style: {
      width: style.width || `${size.width}px`,
      height: style.height || `${size.height}px`,
    },
    zIndex: node.zIndex ?? 0,
  }
}

export function getNodeParentId(node) {
  return node?.parentNode || node?.parentId || null
}

export function getAbsolutePosition(node, nodes, findNode) {
  const pos = normalizePoint(node?.position)
  const pid = getNodeParentId(node)
  if (!pid) return pos
  const parent = nodes.find(n => n.id === pid)
  if (!parent) return pos
  const b = resolveGroupBounds(parent, findNode)
  return { x: b.x + pos.x, y: b.y + pos.y }
}

/** 连线是否跨容器边界 */
export function isCrossGroupEdge(sourceNode, targetNode) {
  const sp = getNodeParentId(sourceNode)
  const tp = getNodeParentId(targetNode)
  if (sp !== tp) return true
  return false
}
