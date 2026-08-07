/**
 * 业务办理页回灌结果解析 / 展示辅助
 *
 * 字段展示名禁止在平台侧硬编码中英映射：标签来自页面 DOM / 提交载荷 fieldLabels。
 */

const META_VALUE_KEYS = new Set([
  'status', 'message', 'success', 'msg', 'code', 'error',
  'action', 'pageType', 'fieldLabels', '_fieldLabels',
])

/**
 * 解析字段展示名：优先 fieldLabels，其次用字段码本身（不做业务词典翻译）。
 *
 * @param {string} key
 * @param {Record<string, string>|null|undefined} fieldLabels
 * @returns {string}
 */
export function labelBusinessPageField(key, fieldLabels) {
  const k = String(key || '').trim()
  if (!k) return ''
  const fromPage = fieldLabels && typeof fieldLabels === 'object' ? fieldLabels[k] : null
  if (typeof fromPage === 'string' && fromPage.trim()) {
    return fromPage.trim()
  }
  return k
}

/**
 * 将提交 values 转为摘要行（去掉接口元数据）。
 *
 * @param {Record<string, any>|null|undefined} values
 * @param {Record<string, string>|null|undefined} fieldLabels 页面采集的字段标签
 * @returns {{ key: string, label: string, value: string }[]}
 */
export function buildBusinessPageSummaryEntries(values, fieldLabels) {
  if (!values || typeof values !== 'object' || Array.isArray(values)) return []
  const rows = []
  for (const [key, raw] of Object.entries(values)) {
    if (META_VALUE_KEYS.has(key) || raw == null || raw === '') continue
    const value = typeof raw === 'object' ? JSON.stringify(raw) : String(raw)
    rows.push({
      key,
      label: labelBusinessPageField(key, fieldLabels),
      value,
    })
  }
  return rows
}

/**
 * 从系统回灌正文解析完整办结数据（values + fieldLabels）。
 *
 * @param {string} content
 * @returns {{ values: Record<string, any>, fieldLabels: Record<string, string> }}
 */
export function parseBusinessPageSubmitDataFromContent(content) {
  const text = String(content || '')
  if (!text) return { values: {}, fieldLabels: {} }

  const marker = '<!--lightbot-bp-data:'
  const markerIdx = text.indexOf(marker)
  if (markerIdx >= 0) {
    const start = markerIdx + marker.length
    const end = text.indexOf('-->', start)
    if (end > start) {
      try {
        const parsed = JSON.parse(text.slice(start, end))
        if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
          if (parsed.values && typeof parsed.values === 'object' && !Array.isArray(parsed.values)) {
            return {
              values: parsed.values,
              fieldLabels: normalizeFieldLabels(parsed.fieldLabels),
            }
          }
          // 旧格式：整段即 values
          return { values: parsed, fieldLabels: {} }
        }
      } catch {
        // fall through to line parse
      }
    }
  }

  return {
    values: parseValuesFromPlainLines(text),
    fieldLabels: {},
  }
}

/**
 * 从系统回灌正文中解析办理字段（兼容旧调用方）。
 *
 * @param {string} content
 * @returns {Record<string, any>}
 */
export function parseBusinessPageValuesFromContent(content) {
  return parseBusinessPageSubmitDataFromContent(content).values
}

/**
 * @param {string} text
 * @returns {Record<string, any>}
 */
function parseValuesFromPlainLines(text) {
  const section = text.includes('办理数据如下：')
    ? text.split('办理数据如下：')[1]
    : text
  const body = (section.split('请直接根据')[0] || section || '').trim()
  if (!body) return {}
  const values = {}
  for (const rawLine of body.split('\n')) {
    const line = rawLine.trim()
    const m = line.match(/^-\s*([^：:]+)\s*[：:]\s*(.*)$/)
    if (!m) continue
    const key = m[1].trim()
    const val = m[2].trim()
    if (!key || val === '') continue
    if (key === 'values' || key === 'extra' || key === 'action' || key === 'pageType' || key === 'fieldLabels') continue
    values[key] = coerceBusinessPageValue(val)
  }
  return values
}

/**
 * @param {any} raw
 * @returns {Record<string, string>}
 */
function normalizeFieldLabels(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return {}
  const out = {}
  for (const [k, v] of Object.entries(raw)) {
    if (typeof v === 'string' && v.trim()) out[k] = v.trim()
  }
  return out
}

/**
 * @param {string} val
 * @returns {string|number|boolean}
 */
function coerceBusinessPageValue(val) {
  if (val === 'true') return true
  if (val === 'false') return false
  if (/^-?\d+$/.test(val) && val.length >= 8) return val
  if (/^-?\d+(\.\d+)?$/.test(val)) return Number(val)
  return val
}

/**
 * 将任意对象转为 postMessage / 注入脚本可用的纯 JSON 可克隆结构。
 * Vue reactive Proxy 直接 postMessage 会抛 DataCloneError。
 *
 * @param {any} value
 * @returns {any}
 */
export function toCloneableJson(value) {
  try {
    return JSON.parse(JSON.stringify(value ?? null))
  } catch {
    return null
  }
}
