import request from '../utils/request'

/** 管理端：全部业务页 */
export function listBusinessPages() {
  return request.get('/business-pages')
}

/** 建设者：已启用业务页 */
export function listEnabledBusinessPages() {
  return request.get('/business-pages/enabled')
}

/** 对话渲染：按 pageType 取启用中的 pageHtml / pageUrl */
export function getBusinessPageRuntime(pageType) {
  return request.get(`/business-pages/runtime/${encodeURIComponent(pageType)}`)
}

export function upsertBusinessPage(data) {
  return request.post('/business-pages', data)
}

/** AI 辅助生成内嵌业务页 HTML */
export function generateBusinessPageHtml(data) {
  return request.post('/business-pages/generate-html', data, { timeout: 120000 })
}

/** AI 对齐平台样式（规范化当前 HTML） */
export function normalizeBusinessPageHtml(data) {
  return request.post('/business-pages/normalize-html', data, { timeout: 120000 })
}

export function setBusinessPageEnabled(id, enabled) {
  return request.put(`/business-pages/${id}/enabled`, null, { params: { enabled } })
}

export function deleteBusinessPage(id) {
  return request.delete(`/business-pages/${id}`)
}

export function getApiKeyBusinessPageConfig(apiKeyId) {
  return request.get(`/business-pages/api-keys/${apiKeyId}/config`)
}

export function updateApiKeyBusinessPageConfig(apiKeyId, data) {
  return request.put(`/business-pages/api-keys/${apiKeyId}/config`, data)
}
