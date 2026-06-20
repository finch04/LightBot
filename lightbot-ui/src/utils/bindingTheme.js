/**
 * 实体统一主题色
 * 用于卡片头像、绑定列表、下拉选项等所有带「头像/图标底色」的展示场景
 */

export const BINDING_TYPES = {
  tool: 'tool',
  knowledge: 'knowledge',
  mcp: 'mcp',
  subagent: 'subagent',
  skill: 'skill',
  agent: 'agent',
  prompt: 'prompt',
  dataset: 'dataset',
  evaluator: 'evaluator',
  template: 'template',
}

/** 渐变背景（图标容器） */
export const BINDING_GRADIENTS = {
  tool: 'linear-gradient(135deg, #10b981, #059669)',
  knowledge: 'linear-gradient(135deg, #6366f1, #4f46e5)',
  mcp: 'linear-gradient(135deg, #8b5cf6, #7c3aed)',
  subagent: 'linear-gradient(135deg, #f59e0b, #d97706)',
  skill: 'linear-gradient(135deg, #ec4899, #db2777)',
  agent: 'linear-gradient(135deg, #7928ca, #ff0080)',
  prompt: 'linear-gradient(135deg, #e11d48, #be123c)',
  dataset: 'linear-gradient(135deg, #0891b2, #0e7490)',
  evaluator: 'linear-gradient(135deg, #f97316, #ea580c)',
  template: 'linear-gradient(135deg, #475569, #334155)',
}

/** 标签/选中态浅色背景 */
export const BINDING_TAG_BG = {
  tool: '#ecfdf5',
  knowledge: '#eef2ff',
  mcp: '#f5f3ff',
  subagent: '#fffbeb',
  skill: '#fdf2f8',
  agent: '#fdf4ff',
  prompt: '#fff1f2',
  dataset: '#ecfeff',
  evaluator: '#fff7ed',
  template: '#f8fafc',
}

export const BINDING_TAG_BORDER = {
  tool: '#a7f3d0',
  knowledge: '#c7d2fe',
  mcp: '#ddd6fe',
  subagent: '#fcd34d',
  skill: '#f9a8d4',
  agent: '#e9d5ff',
  prompt: '#fecdd3',
  dataset: '#a5f3fc',
  evaluator: '#fed7aa',
  template: '#cbd5e1',
}

export const BINDING_TAG_TEXT = {
  tool: '#047857',
  knowledge: '#4338ca',
  mcp: '#6d28d9',
  subagent: '#b45309',
  skill: '#be185d',
  agent: '#7c3aed',
  prompt: '#be123c',
  dataset: '#0e7490',
  evaluator: '#c2410c',
  template: '#334155',
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

/** 工具类型中文标签 */
const TOOL_TYPE_LABEL_MAP = { builtin: '内置', knowledge: '知识库', custom: '自定义', api: 'API调用', mcp: 'MCP协议' }

export function getToolTypeLabel(toolType) {
  return TOOL_TYPE_LABEL_MAP[toolType?.code || toolType] || toolType || ''
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
