/**
 * 截断文本，超出长度返回截断后的文本
 * @param {string} text 原始文本
 * @param {number} maxLen 最大长度，默认50
 * @returns {string}
 */
export function truncateText(text, maxLen = 50) {
  if (!text) return ''
  return text.length > maxLen ? text.slice(0, maxLen) + '...' : text
}

/**
 * 判断文本是否需要截断
 * @param {string} text 原始文本
 * @param {number} maxLen 最大长度，默认50
 * @returns {boolean}
 */
export function needTruncate(text, maxLen = 50) {
  if (!text) return false
  return text.length > maxLen
}

/**
 * 格式化时间
 * 支持 Java LocalDateTime 数组 [2026,6,25,10,30,0] 和 Date 对象/字符串
 * @param {Array|string|Date} t 时间值
 * @returns {string}
 */
export function formatTime(t) {
  if (!t) return ''
  if (Array.isArray(t)) {
    const [y, m, d, h = 0, mi = 0, s = 0] = t
    const pad = (n) => String(n).padStart(2, '0')
    return `${y}-${pad(m)}-${pad(d)} ${pad(h)}:${pad(mi)}:${pad(s)}`
  }
  const date = typeof t === 'string' || t instanceof Date ? new Date(t) : null
  if (!date || isNaN(date.getTime())) return String(t)
  return date.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

/**
 * 格式化 JSON 字符串
 * @param {string|object} str JSON 字符串或对象
 * @returns {string}
 */
export function formatJson(str) {
  if (!str) return ''
  try {
    const obj = typeof str === 'string' ? JSON.parse(str) : str
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(str)
  }
}
