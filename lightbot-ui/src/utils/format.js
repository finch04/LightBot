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
