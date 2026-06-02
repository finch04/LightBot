/**
 * 节点样式公共工具
 * <p>从 NODE_META 派生 SVG 颜色和 emoji 图标，供链路追踪等非画布场景使用</p>
 */
import { NODE_META } from '@/views/workflow/nodeMeta'

/** emoji 图标映射（SVG / 纯文本场景） */
const ICON_TEXT_MAP = {
  start: '▶', end: '⏹', input: '📥', output: '📤',
  llm: '🤖', retrieval: '🔍', classifier: '🏷️', condition: '🔀',
  variable: '📝', variable_handle: '🔧', script: '📜', code: '💻',
  api: '🌐', mcp: '🔌', tool: '🛠️', parameter_extractor: '📋',
  batch: '📦', loop: '🔄', app_component: '🧩',
  loop_start: '🔄', loop_end: '🔄', batch_start: '📦', batch_end: '📦',
}

/**
 * 获取节点 emoji 图标（用于 SVG / 纯文本渲染）
 * @param {string} type 节点类型
 * @returns {string} emoji 字符
 */
export function getNodeIconText(type) {
  return ICON_TEXT_MAP[type] || '⬜'
}

/**
 * 获取节点 SVG 三色（fill / stroke / textColor）
 * <p>从 NODE_META.color 自动派生，确保与画布颜色一致</p>
 * @param {string} type 节点类型
 * @param {string} [status] 节点状态，'failed' 时返回红色
 * @returns {{ fill: string, stroke: string, textColor: string }}
 */
export function getNodeColors(type, status) {
  if (status === 'failed') {
    return { fill: '#fff2f0', stroke: '#ff4d4f', textColor: '#cf1322' }
  }
  const meta = NODE_META[type]
  if (!meta) return { fill: '#fafafa', stroke: '#d9d9d9', textColor: '#595959' }
  return deriveColors(meta.color)
}

/**
 * 获取节点单色（画布场景）
 * @param {string} type 节点类型
 * @returns {string} hex 颜色
 */
export function getNodeColor(type) {
  const meta = NODE_META[type]
  return meta ? meta.color : '#6b7280'
}

/**
 * 从 hex 主色派生 fill / stroke / textColor
 * - fill: 8% 透明度背景
 * - stroke: 原色
 * - textColor: 原色加深 25%
 */
function deriveColors(hex) {
  const { r, g, b } = hexToRgb(hex)
  return {
    fill: `rgba(${r},${g},${b},0.08)`,
    stroke: hex,
    textColor: rgbToHex(
      Math.max(0, Math.round(r * 0.75)),
      Math.max(0, Math.round(g * 0.75)),
      Math.max(0, Math.round(b * 0.75))
    ),
  }
}

function hexToRgb(hex) {
  const h = hex.replace('#', '')
  return {
    r: parseInt(h.substring(0, 2), 16),
    g: parseInt(h.substring(2, 4), 16),
    b: parseInt(h.substring(4, 6), 16),
  }
}

function rgbToHex(r, g, b) {
  return '#' + [r, g, b].map(v => v.toString(16).padStart(2, '0')).join('')
}
