import { getAgentStatuses } from '../api/enum'

let statusLabelMap = null
let loadPromise = null

/** 默认兜底（与后端 AgentStatus 枚举一致） */
const FALLBACK_LABELS = {
  draft: '草稿',
  published: '已发布',
  published_editing: '编辑中',
  archived: '已归档',
}

/**
 * 加载 Agent 状态枚举中文（全局缓存）
 */
export async function loadAgentStatusLabels() {
  if (statusLabelMap) return statusLabelMap
  if (!loadPromise) {
    loadPromise = getAgentStatuses()
      .then(res => {
        const map = { ...FALLBACK_LABELS }
        ;(res.data || []).forEach(item => {
          if (item.value) map[item.value] = item.label || item.value
        })
        statusLabelMap = map
        return map
      })
      .catch(() => {
        statusLabelMap = { ...FALLBACK_LABELS }
        return statusLabelMap
      })
  }
  return loadPromise
}

/**
 * 解析状态 code
 */
export function resolveAgentStatusCode(status) {
  if (!status) return 'draft'
  if (typeof status === 'string') return status
  return status.code || status.value || 'draft'
}

/**
 * 格式化 Agent 状态展示文案
 * @param {string|object} status 状态 code 或 { code }
 * @param {number} version 已发布版本号（可选）
 * @param {Record<string,string>} map 枚举映射，不传则用缓存/兜底
 */
export function formatAgentStatus(status, version = 0, map = statusLabelMap) {
  const code = resolveAgentStatusCode(status)
  const labels = map || FALLBACK_LABELS
  const label = labels[code] || code
  if ((code === 'published' || code === 'published_editing') && version > 0) {
    return `${label} v${version}`
  }
  return label
}
