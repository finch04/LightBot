import { getSmoothStepPath, Position } from '@vue-flow/core'
import { HANDLE_IN, HANDLE_OUT } from './workflowConnection'

const DEFAULT_OFFSET = 20
const DEFAULT_BORDER_RADIUS = 5

/** 根据 handleId 推断连接方位（与 WorkflowHandle 布局一致） */
function resolveHandleMeta(handleId, role, nodeType) {
  const id = handleId || (role === 'target' ? HANDLE_IN : HANDLE_OUT)
  if (role === 'target' || id === HANDLE_IN) {
    return { position: Position.Left, id: HANDLE_IN }
  }
  if (id === 'out_a') return { position: Position.Top, id }
  if (id === 'out_b') return { position: Position.Bottom, id }
  if (id === 'out_c') return { position: Position.Right, id }
  if (nodeType === 'condition' && id?.startsWith('out_')) {
    if (id === 'out_a') return { position: Position.Top, id }
    if (id === 'out_b') return { position: Position.Bottom, id }
    return { position: Position.Right, id }
  }
  return { position: Position.Right, id: HANDLE_OUT }
}

function nodeBox(node) {
  const p = node.computedPosition || node.position || { x: 0, y: 0 }
  const isRound = node.type === 'start' || node.type === 'end'
  const w = node.dimensions?.width ?? (isRound ? 64 : 180)
  const h = node.dimensions?.height ?? (isRound ? 64 : 88)
  return { x: p.x, y: p.y, w, h, cx: p.x + w / 2, cy: p.y + h / 2 }
}

/** 从 Vue Flow 节点 handleBounds 或几何回退计算连接点坐标 */
export function getHandlePoint(node, handleId, role) {
  if (!node) return null
  const box = nodeBox(node)
  const meta = resolveHandleMeta(handleId, role, node.type)
  const bounds = role === 'target' ? node.handleBounds?.target : node.handleBounds?.source
  const list = bounds || []
  const h = list.find(item => item.id === meta.id)
    || list.find(item => item.id === handleId)
    || (list.length === 1 ? list[0] : null)

  if (h) {
    return {
      x: box.x + h.x + (h.width ?? 14) / 2,
      y: box.y + h.y + (h.height ?? 14) / 2,
      position: h.position ?? meta.position,
    }
  }

  switch (meta.position) {
    case Position.Top:
      return { x: box.cx, y: box.y, position: Position.Top }
    case Position.Bottom:
      return { x: box.cx, y: box.y + box.h, position: Position.Bottom }
    case Position.Left:
      return { x: box.x, y: box.cy, position: Position.Left }
    case Position.Right:
    default:
      return { x: box.x + box.w, y: box.cy, position: Position.Right }
  }
}

/** 沿 SVG 路径按弧长取中点（保证 + 落在折线上） */
function samplePathMidpoint(pathD) {
  if (!pathD) return null
  const points = []
  const tokens = pathD.match(/[MLQ]|[-+]?[\d.]+(?:e[-+]?\d+)?/gi) || []
  let i = 0
  let cmd = ''
  let cur = { x: 0, y: 0 }

  const push = (x, y) => {
    if (!Number.isFinite(x) || !Number.isFinite(y)) return
    points.push({ x, y })
    cur = { x, y }
  }

  while (i < tokens.length) {
    const t = tokens[i]
    if (t === 'M' || t === 'L' || t === 'Q') {
      cmd = t
      i++
      continue
    }
    if (cmd === 'M' || cmd === 'L') {
      push(parseFloat(tokens[i]), parseFloat(tokens[i + 1]))
      i += 2
    } else if (cmd === 'Q') {
      const x1 = parseFloat(tokens[i])
      const y1 = parseFloat(tokens[i + 1])
      const x2 = parseFloat(tokens[i + 2])
      const y2 = parseFloat(tokens[i + 3])
      for (let s = 1; s <= 8; s++) {
        const tt = s / 8
        const mt = 1 - tt
        push(
          mt * mt * cur.x + 2 * mt * tt * x1 + tt * tt * x2,
          mt * mt * cur.y + 2 * mt * tt * y1 + tt * tt * y2
        )
      }
      i += 4
    } else {
      i++
    }
  }

  if (points.length < 2) return points[0] || null

  const segLen = []
  let total = 0
  for (let j = 1; j < points.length; j++) {
    const dx = points[j].x - points[j - 1].x
    const dy = points[j].y - points[j - 1].y
    const len = Math.hypot(dx, dy)
    segLen.push(len)
    total += len
  }
  if (total < 1) return points[0]

  const half = total / 2
  let acc = 0
  for (let j = 0; j < segLen.length; j++) {
    if (acc + segLen[j] >= half) {
      const t = (half - acc) / (segLen[j] || 1)
      return {
        x: points[j].x + (points[j + 1].x - points[j].x) * t,
        y: points[j].y + (points[j + 1].y - points[j].y) * t,
      }
    }
    acc += segLen[j]
  }
  return points[points.length - 1]
}

/**
 * 计算连线上插入按钮中心（与 Vue Flow smoothstep 边一致）
 */
export function getEdgeInsertCenter(edge, findNode, nodesFallback = []) {
  if (!edge?.source || !edge?.target) return null

  let sourceNode = findNode?.(edge.source)
  let targetNode = findNode?.(edge.target)
  if (!sourceNode) sourceNode = nodesFallback.find(n => n.id === edge.source)
  if (!targetNode) targetNode = nodesFallback.find(n => n.id === edge.target)
  if (!sourceNode || !targetNode) return null

  const src = getHandlePoint(sourceNode, edge.sourceHandle, 'source')
  const tgt = getHandlePoint(targetNode, edge.targetHandle || HANDLE_IN, 'target')
  if (!src || !tgt) return null

  const [path, labelX, labelY] = getSmoothStepPath({
    sourceX: src.x,
    sourceY: src.y,
    sourcePosition: src.position,
    targetX: tgt.x,
    targetY: tgt.y,
    targetPosition: tgt.position,
    offset: DEFAULT_OFFSET,
    borderRadius: DEFAULT_BORDER_RADIUS,
  })

  const onPath = samplePathMidpoint(path)
  if (onPath) return onPath
  return { x: labelX, y: labelY }
}
