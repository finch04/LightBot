/**
 * 工作流连线规则：左侧入(in) / 右侧出(out)，禁止逆向与非法端口
 */

import { getNodeParentId, isGroupNodeType, isCrossGroupEdge } from './workflowGroup'

const GROUP_START_BUILTIN = new Set(['loop_start', 'batch_start'])
const GROUP_END_BUILTIN = new Set(['loop_end', 'batch_end'])

export const HANDLE_IN = 'in'
export const HANDLE_OUT = 'out'

/** 条件节点多出口前缀 */
export const HANDLE_OUT_PREFIX = 'out_'

const START_TYPES = new Set(['start'])
const END_TYPES = new Set(['end'])

function getNode(nodes, nodeId) {
  return nodes.find(item => item.id === nodeId)
}

function getNodeType(nodes, nodeId) {
  return getNode(nodes, nodeId)?.type || ''
}

/** 是否为分支出口（条件 if / 意图分类等） */
export function isBranchSourceHandle(handleId) {
  if (!handleId || handleId === HANDLE_IN || handleId === HANDLE_OUT) return false
  if (handleId.startsWith(HANDLE_OUT_PREFIX)) return true
  // 意图分类：nodeId_intentId
  return handleId.includes('_')
}

/** 归一化连接：目标默认 in，普通源默认 out */
export function normalizeConnection(params) {
  if (!params?.source || !params?.target) return params

  let { sourceHandle, targetHandle } = params

  if (!targetHandle || targetHandle === HANDLE_OUT) {
    targetHandle = HANDLE_IN
  }

  if (!sourceHandle || sourceHandle === HANDLE_IN) {
    sourceHandle = HANDLE_OUT
  } else if (['a', 'b', 'c'].includes(sourceHandle)) {
    sourceHandle = `${HANDLE_OUT_PREFIX}${sourceHandle}`
  }

  return {
    ...params,
    sourceHandle,
    targetHandle,
  }
}

/** 为缺少 id 的连线生成稳定唯一标识 */
export function ensureEdgeId(edge, index = 0) {
  if (!edge) return edge
  const e = { ...edge }
  if (e.id != null && String(e.id).trim() !== '') {
    return e
  }
  const normalized = normalizeConnection({
    source: e.source,
    target: e.target,
    sourceHandle: e.sourceHandle,
    targetHandle: e.targetHandle,
  })
  const suffix = `${index}_${Date.now().toString(36).slice(2, 8)}`
  e.id = `${buildEdgeId(normalized)}_${suffix}`
  return e
}

/** 加载/保存时修正历史连线端口并补齐 id */
export function migrateWorkflowEdge(edge, index = 0) {
  if (!edge) return edge
  let e = { ...edge }
  if (!e.targetHandle || e.targetHandle === HANDLE_OUT) {
    e.targetHandle = HANDLE_IN
  }
  if (!e.sourceHandle) {
    e.sourceHandle = HANDLE_OUT
  } else if (e.sourceHandle === HANDLE_IN) {
    e.sourceHandle = HANDLE_OUT
  } else if (['a', 'b', 'c'].includes(e.sourceHandle)) {
    e.sourceHandle = `${HANDLE_OUT_PREFIX}${e.sourceHandle}`
  }
  e = ensureEdgeId(e, index)
  return e
}

/** 加载图时去重并补齐连线 id */
export function normalizeWorkflowEdges(edgeList) {
  if (!Array.isArray(edgeList)) return []
  const seen = new Set()
  return edgeList.map((edge, index) => {
    let e = migrateWorkflowEdge(edge, index)
    if (seen.has(e.id)) {
      e = { ...e, id: `${e.id}_dup_${index}` }
    }
    seen.add(e.id)
    return e
  })
}

/** 是否允许跨容器边界的合法连线（经内置起止节点与主流程衔接） */
function isAllowedGroupBoundaryConnection(sourceNode, targetNode, sourceType, targetType) {
  const sp = getNodeParentId(sourceNode)
  const tp = getNodeParentId(targetNode)
  if (sp === tp) return true

  // 主流程 → 迭代开始 / 并行处理
  if (GROUP_START_BUILTIN.has(targetType) && tp != null && sp == null) return true
  // 迭代结束 / 并行结束 → 主流程
  if (GROUP_END_BUILTIN.has(sourceType) && sp != null && tp == null) return true
  return false
}

/** 容器内置起止节点的连线方向约束 */
function isValidGroupBuiltinConnection(sourceNode, targetNode, sourceType, targetType) {
  const sp = getNodeParentId(sourceNode)
  const tp = getNodeParentId(targetNode)

  if (GROUP_START_BUILTIN.has(sourceType) && tp !== sp) return false
  if (GROUP_START_BUILTIN.has(targetType)) {
    if (sp === tp) return false
    if (sp != null) return false
  }

  if (GROUP_END_BUILTIN.has(targetType) && sp !== tp) return false
  if (GROUP_END_BUILTIN.has(sourceType)) {
    if (sp === tp) return false
    if (tp != null) return false
  }

  return true
}

/**
 * @param {object} connection vue-flow Connection
 * @param {{ nodes: array, edges: array, excludeEdgeId?: string }} ctx
 */
export function isValidWorkflowConnection(connection, ctx) {
  const { nodes = [], edges = [], excludeEdgeId } = ctx
  if (!connection?.source || !connection?.target) return false

  const normalized = normalizeConnection(connection)
  const { source, target, sourceHandle, targetHandle } = normalized
  // Vue Flow setEdges 校验时会带上已存在边的 id，须排除自身，否则会误判为重复并清空画布
  const selfEdgeId = connection?.id ?? excludeEdgeId

  if (source === target) return false

  const sourceType = getNodeType(nodes, source)
  const targetType = getNodeType(nodes, target)

  if (END_TYPES.has(sourceType)) return false
  if (START_TYPES.has(targetType)) return false

  const sourceNode = getNode(nodes, source)
  const targetNode = getNode(nodes, target)

  // 循环/批处理容器壳层禁止连线，经内置起止节点与主流程衔接
  if (isGroupNodeType(sourceType) || isGroupNodeType(targetType)) return false

  if (!isValidGroupBuiltinConnection(sourceNode, targetNode, sourceType, targetType)) return false

  // 容器内外默认不能互连；内置起止节点允许与主流程衔接
  if (sourceNode && targetNode && isCrossGroupEdge(sourceNode, targetNode)) {
    if (!isAllowedGroupBoundaryConnection(sourceNode, targetNode, sourceType, targetType)) return false
  }

  // 目标必须是入端口（归一化后应为 in；兼容拖拽过程中 handle 暂未解析）
  if (targetHandle != null && targetHandle !== '' && targetHandle !== HANDLE_IN) return false
  if (sourceHandle === HANDLE_IN) return false

  const dup = edges.some(e => {
    if (selfEdgeId && e.id === selfEdgeId) return false
    if (excludeEdgeId && e.id === excludeEdgeId) return false
    return e.source === source
      && e.target === target
      && (e.sourceHandle || HANDLE_OUT) === (sourceHandle || HANDLE_OUT)
  })
  if (dup) return false

  return true
}

export function getHandleDisplayName(handleId) {
  if (!handleId || handleId === HANDLE_IN) return '入'
  if (handleId === HANDLE_OUT) return '出'
  if (handleId.startsWith(HANDLE_OUT_PREFIX)) return `出(${handleId.slice(4)})`
  return `出(${handleId})`
}

export function buildEdgeId(params) {
  const sh = params.sourceHandle || HANDLE_OUT
  const th = params.targetHandle || HANDLE_IN
  return `edge_${params.source}_${params.target}_${sh}_${th}`
}
