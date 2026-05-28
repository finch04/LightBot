/**
 * Agent 绑定能力统一主题色（工具 / 知识库 / MCP / SubAgent / Skill）
 * 用于 Agent 详情、扩展管理、知识库列表等所有带「头像/图标底色」的展示场景
 */

export const BINDING_TYPES = {
  tool: 'tool',
  knowledge: 'knowledge',
  mcp: 'mcp',
  subagent: 'subagent',
  skill: 'skill',
}

/** 渐变背景（图标容器） */
export const BINDING_GRADIENTS = {
  tool: 'linear-gradient(135deg, #10b981, #059669)',
  knowledge: 'linear-gradient(135deg, #6366f1, #4f46e5)',
  mcp: 'linear-gradient(135deg, #8b5cf6, #7c3aed)',
  subagent: 'linear-gradient(135deg, #f59e0b, #d97706)',
  skill: 'linear-gradient(135deg, #ec4899, #db2777)',
}

/** 标签/选中态浅色背景 */
export const BINDING_TAG_BG = {
  tool: '#ecfdf5',
  knowledge: '#eef2ff',
  mcp: '#f5f3ff',
  subagent: '#fffbeb',
  skill: '#fdf2f8',
}

export const BINDING_TAG_BORDER = {
  tool: '#a7f3d0',
  knowledge: '#c7d2fe',
  mcp: '#ddd6fe',
  subagent: '#fcd34d',
  skill: '#f9a8d4',
}

export const BINDING_TAG_TEXT = {
  tool: '#047857',
  knowledge: '#4338ca',
  mcp: '#6d28d9',
  subagent: '#b45309',
  skill: '#be185d',
}

/** 内置标记色（统一蓝，与能力类型无关） */
export const BUILTIN_BADGE_STYLE = {
  background: '#3b82f6',
  color: '#fff',
}

/**
 * @param {string} type - tool | knowledge | mcp | subagent | skill
 * @returns {{ gradient: string, tagBg: string, tagBorder: string, tagText: string }}
 */
export function getBindingTheme(type) {
  const key = BINDING_TYPES[type] ? type : 'tool'
  return {
    gradient: BINDING_GRADIENTS[key],
    tagBg: BINDING_TAG_BG[key],
    tagBorder: BINDING_TAG_BORDER[key],
    tagText: BINDING_TAG_TEXT[key],
  }
}

/** 图标容器行内样式 */
export function iconBoxStyle(type) {
  return { background: BINDING_GRADIENTS[type] || BINDING_GRADIENTS.tool }
}

/** 标签行内样式 */
export function tagStyle(type) {
  return {
    background: BINDING_TAG_BG[type],
    borderColor: BINDING_TAG_BORDER[type],
    color: BINDING_TAG_TEXT[type],
  }
}
