/** 需拉取文本内容后预览的扩展名（与知识库 FilePreview 一致） */
export const TEXT_LIKE_EXTENSIONS = new Set([
  'txt', 'md', 'markdown', 'csv', 'json', 'xml', 'log', 'html', 'htm',
])

/** 知识库不提供「源文件预览」的 Office 类型（与 KnowledgeDetail.hasSourcePreview 一致） */
export const OFFICE_NO_SOURCE_PREVIEW_EXTENSIONS = new Set([
  'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx',
])

/** iframe 源文件预览（pdf / html） */
export const IFRAME_PREVIEW_EXTENSIONS = new Set(['pdf', 'html', 'htm'])

/** 文本拉取后源文件预览 */
export const TEXT_SOURCE_PREVIEW_EXTENSIONS = new Set([
  'md', 'markdown', 'txt', 'csv', 'json', 'xml', 'log',
])

/**
 * @param {string} fileName
 * @returns {string} 小写扩展名，无点
 */
export function getFileExtension(fileName) {
  if (!fileName) return ''
  const dot = fileName.lastIndexOf('.')
  return dot > 0 ? fileName.substring(dot + 1).toLowerCase() : ''
}

/**
 * 归一化扩展名：支持文件名或纯扩展名（含/不含点）
 * @param {string} fileNameOrExt
 * @returns {string}
 */
export function normalizeFileExtension(fileNameOrExt) {
  if (!fileNameOrExt) return ''
  const v = String(fileNameOrExt).toLowerCase()
  if (v.includes('.')) return getFileExtension(v)
  return v.replace(/^\./, '')
}

/**
 * 是否提供源文件预览（知识库规则：Office 文档仅文本化，不提供源文件预览）
 * @param {string} fileNameOrExt
 */
export function hasSourceFilePreview(fileNameOrExt) {
  const ext = normalizeFileExtension(fileNameOrExt)
  if (!ext) return false
  return !OFFICE_NO_SOURCE_PREVIEW_EXTENSIONS.has(ext)
}

/** @param {string} fileNameOrExt */
export function isIframeSourcePreviewable(fileNameOrExt) {
  return IFRAME_PREVIEW_EXTENSIONS.has(normalizeFileExtension(fileNameOrExt))
}

/** @param {string} fileNameOrExt */
export function isTextSourcePreviewable(fileNameOrExt) {
  return TEXT_SOURCE_PREVIEW_EXTENSIONS.has(normalizeFileExtension(fileNameOrExt))
}

/**
 * 是否为需读取文本内容后预览的类型
 * @param {string} fileName
 */
export function isTextLikeFile(fileName) {
  return TEXT_LIKE_EXTENSIONS.has(getFileExtension(fileName))
}

/**
 * 从 URL 拉取文本内容（用于 md/txt 等源文件预览）
 * @param {string} url
 * @returns {Promise<string>}
 */
export async function fetchTextContent(url) {
  const resp = await fetch(url)
  if (!resp.ok) {
    throw new Error(`HTTP ${resp.status}`)
  }
  return resp.text()
}
