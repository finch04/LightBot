import Pinyin from 'tiny-pinyin'

/** pageType 允许格式：小写字母开头，后接小写字母/数字/下划线，最长 64 */
export const PAGE_TYPE_PATTERN = /^[a-z][a-z0-9_]{0,63}$/

/**
 * 从展示名称生成业务页 pageType（snake_case slug）。
 * 中文转拼音分词，英文按词保留；可编辑，仅用于新建时的默认值。
 *
 * @param {string} displayName
 * @returns {string}
 */
export function displayNameToPageType(displayName) {
  const raw = String(displayName || '').trim()
  if (!raw) return ''

  let out = ''
  let asciiBuf = ''

  const appendToken = (token) => {
    if (!token) return
    if (out && !out.endsWith('_')) out += '_'
    out += token
  }

  const flushAscii = () => {
    if (!asciiBuf) return
    const piece = asciiBuf
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '_')
      .replace(/^_+|_+$/g, '')
    appendToken(piece)
    asciiBuf = ''
  }

  for (const ch of raw) {
    if (/[\u4e00-\u9fff]/.test(ch)) {
      flushAscii()
      if (Pinyin.isSupported()) {
        const py = String(Pinyin.convertToPinyin(ch, '', true) || '')
          .toLowerCase()
          .replace(/[^a-z0-9]/g, '')
        appendToken(py)
      }
    } else {
      asciiBuf += ch
    }
  }
  flushAscii()

  out = out.replace(/_+/g, '_').replace(/^_+|_+$/g, '')
  if (!out) return 'business_page'
  if (/^\d/.test(out)) out = `p_${out}`
  if (out.length > 64) {
    out = out.slice(0, 64).replace(/_+$/g, '')
  }
  if (!PAGE_TYPE_PATTERN.test(out)) {
    // 极端兜底：再清洗一次
    out = out.replace(/[^a-z0-9_]/g, '').replace(/^_+|_+$/g, '')
    if (!out || !/^[a-z]/.test(out)) out = `p_${out || 'page'}`
    if (out.length > 64) out = out.slice(0, 64).replace(/_+$/g, '')
  }
  return out
}

/**
 * @param {string} pageType
 * @returns {boolean}
 */
export function isValidPageType(pageType) {
  return PAGE_TYPE_PATTERN.test(String(pageType || '').trim())
}
