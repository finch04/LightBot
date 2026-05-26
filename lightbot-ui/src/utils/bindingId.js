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
