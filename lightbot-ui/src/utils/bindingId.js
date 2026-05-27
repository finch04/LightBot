/**
 * 绑定类 ID 统一转字符串，避免雪花 ID 在 JS Number 中精度丢失
 */
export function toBindingId(id) {
  if (id == null || id === '') return ''
  return String(id)
}

/** 从接口/ config 解析结果构建绑定 ID Set（去重） */
export function toBindingIdSet(ids) {
  const set = new Set()
  for (const id of ids || []) {
    const s = toBindingId(id)
    if (s) set.add(s)
  }
  return set
}

/** agent.config 中由独立接口维护的绑定字段，不应进入模型配置 JSON */
export const AGENT_CONFIG_BINDING_KEYS = [
  'knowledges',
  'tools',
  'mcpServers',
  'subagents',
]

export function stripBindingKeysFromConfig(config) {
  if (!config || typeof config !== 'object') return
  for (const key of AGENT_CONFIG_BINDING_KEYS) {
    delete config[key]
  }
}

/**
 * 按已选 ID 顺序合并目录实体；查不到的保留占位并标记 _deleted
 * @param {Set<string>|Iterable<string>} selectedIdSet
 * @param {Array<object>} catalogList
 * @param {{ idKey?: string, entityLabel?: string }} options
 */
export function resolveBindingItems(selectedIdSet, catalogList, options = {}) {
  const { idKey = 'id', catalogReady = true } = options
  const catalogById = new Map()
  for (const item of catalogList || []) {
    const id = toBindingId(item[idKey])
    if (id) catalogById.set(id, item)
  }
  const orderedIds = [...selectedIdSet].map(toBindingId).filter(Boolean)
  return orderedIds.map(id => {
    const found = catalogById.get(id)
    if (found) {
      return { ...found, _deleted: false }
    }
    const label = deletedBindingDisplayName(id)
    // 目录未加载完成时不判为已删除，避免 Tab 懒加载导致误报
    if (!catalogReady) {
      return {
        id,
        name: label,
        displayName: label,
        _deleted: false,
        _catalogPending: true,
      }
    }
    return {
      id,
      name: label,
      displayName: label,
      _deleted: true,
    }
  })
}

/** 已删除绑定的展示名（显示失效 ID） */
export function deletedBindingDisplayName(id) {
  const sid = toBindingId(id)
  return sid ? `ID：${sid}` : 'ID：—'
}

/** 统计已删除占位绑定数量 */
export function countDeletedBindingItems(items) {
  return (items || []).filter(i => i._deleted).length
}

/**
 * 生成已删除绑定明细行（用于 Alert / 弹窗，多行展示）
 * @param {Array<{ label: string, items: Array<{ id, _deleted? }> }>} sections
 */
export function formatDeletedBindingDetailLines(sections) {
  const lines = []
  for (const { label, items } of sections || []) {
    const deleted = (items || []).filter(i => i._deleted)
    if (!deleted.length) continue
    lines.push(`${label}：${deleted.length} 个已删除的`)
    for (const item of deleted) {
      lines.push(`（ID：${toBindingId(item.id)}）`)
    }
  }
  return lines
}

/** 从已选列表中移除所有 _deleted 项，返回移除数量 */
export function removeDeletedIdsFromSet(idSet, items) {
  let n = 0
  for (const item of items || []) {
    if (item._deleted) {
      const id = toBindingId(item.id)
      if (id && idSet.delete(id)) n++
    }
  }
  return n
}

/** 后端版本快照名称含「已删除」时标记，并统一为 ID：xxx 展示 */
export function markBindingItemDeletedFlag(item) {
  if (!item) return item
  const text = String(item.name || item.displayName || '')
  if (text.includes('已删除')) {
    const id = toBindingId(item.id)
    const label = deletedBindingDisplayName(id)
    return { ...item, _deleted: true, name: label, displayName: label }
  }
  return item
}
