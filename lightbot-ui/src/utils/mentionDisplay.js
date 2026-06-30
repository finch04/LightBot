/** @ mention 展示文案（输入框 / 历史消息共用） */

export const MENTION_TYPE_LABELS = {
  knowledge: '知识库',
  subagent: 'SubAgents',
  skill: 'Skill',
  tool: '工具',
}

/**
 * @param {string} type mention 类型
 * @param {boolean} valid 资源是否有效
 * @returns {string} chip 样式类名（不含 mention-chip 基础类）
 */
export function getMentionChipClass(type, valid = true) {
  if (!valid) return 'mention-chip-invalid'
  return `mention-chip-${type || 'tool'}`
}

/**
 * @param {string} type mention 类型
 * @param {string} name 展示名
 * @param {string} token 原始 token
 * @param {boolean} valid 资源是否有效
 * @returns {{ title: string, sub: string }}
 */
export function getMentionTooltip(type, name, token, valid = true) {
  const typeLabel = MENTION_TYPE_LABELS[type] || type || '资源'
  if (!valid) {
    return {
      title: `${typeLabel}（已失效）`,
      sub: '资源可能已删除或无权访问',
    }
  }
  return {
    title: `${typeLabel}：${name}`,
    sub: token || '',
  }
}
