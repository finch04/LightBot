import DOMPurify from 'dompurify'

/**
 * 净化 HTML 字符串，防止 XSS 攻击
 * @param {string} html 原始 HTML
 * @param {object} [options] DOMPurify 配置选项
 * @returns {string} 净化后的 HTML
 */
export function sanitizeHtml(html, options = {}) {
  if (!html) return ''
  return DOMPurify.sanitize(html, {
    ADD_TAGS: ['input'],
    ADD_ATTR: ['class', 'style', 'target', 'rel', 'type', 'checked', 'disabled'],
    ...options,
  })
}
