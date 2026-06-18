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
 * 判断实体是否处于禁用状态
 * Skill/Tool/MCP 使用 CommonStatus 枚举（status === 'disabled'）
 * SubAgent 使用 Integer 布尔（enabled === 0）
 */
export function isItemDisabled(item) {
  if (!item) return false
  if (item.status === 'disabled' || item.status === 'DISABLED') return true
  if (item.enabled === 0 || item.enabled === false) return true
  return false
}

/**
 * 按已选 ID 顺序合并目录实体；查不到的保留占位并标记 _deleted；禁用的标记 _disabled
 * @param {Set<string>|Iterable<string>} selectedIdSet
 * @param {Array<object>} catalogList
 * @param {{ idKey?: string, entityLabel?: string, catalogReady?: boolean }} options
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
      return { ...found, _deleted: false, _disabled: isItemDisabled(found) }
    }
    const label = deletedBindingDisplayName(id)
    // 目录未加载完成时不判为已删除，避免 Tab 懒加载导致误报
    if (!catalogReady) {
      return {
        id,
        name: label,
        displayName: label,
        _deleted: false,
        _disabled: false,
        _catalogPending: true,
      }
    }
    return {
      id,
      name: label,
      displayName: label,
      _deleted: true,
      _disabled: false,
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

/** 统计已禁用绑定数量 */
export function countDisabledBindingItems(items) {
  return (items || []).filter(i => i._disabled && !i._deleted).length
}

/**
 * 生成失效绑定明细行（用于 Alert / 弹窗，区分已删除和已禁用）
 * @param {Array<{ label: string, items: Array<{ id, _deleted?, _disabled? }> }>} sections
 */
export function formatDeletedBindingDetailLines(sections) {
  const lines = []
  for (const { label, items } of sections || []) {
    const deleted = (items || []).filter(i => i._deleted)
    const disabled = (items || []).filter(i => i._disabled && !i._deleted)
    if (!deleted.length && !disabled.length) continue
    if (deleted.length) {
      const names = deleted.map(item => `ID：${toBindingId(item.id)}`).join('、')
      lines.push(`${label}：${deleted.length} 个已删除（${names}）`)
    }
    if (disabled.length) {
      const names = disabled.map(item => item.displayName || item.name || toBindingId(item.id)).join('、')
      lines.push(`${label}：${disabled.length} 个已禁用（${names}）`)
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

/** 后端版本快照名称含「已删除」时标记为 _deleted；检查 status/enabled 标记为 _disabled */
export function markBindingItemDeletedFlag(item) {
  if (!item) return item
  const text = String(item.name || item.displayName || '')
  if (text.includes('已删除')) {
    const id = toBindingId(item.id)
    const label = deletedBindingDisplayName(id)
    return { ...item, _deleted: true, _disabled: false, name: label, displayName: label }
  }
  return { ...item, _deleted: false, _disabled: isItemDisabled(item) }
}
