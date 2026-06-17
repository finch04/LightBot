import request from '../utils/request'

/**
 * 获取文档可编辑内容
 */
export function getEditableContent(documentId) {
  return request.get(`/documents/${documentId}/editable-content`)
}

/**
 * 保存文档编辑内容
 */
export function saveDocumentContent(documentId, data) {
  return request.put(`/documents/${documentId}/content`, data)
}

/**
 * 获取文档版本列表
 */
export function listVersions(documentId) {
  return request.get(`/documents/${documentId}/versions`)
}

/**
 * 获取指定版本内容
 */
export function getVersionContent(documentId, versionId) {
  return request.get(`/documents/${documentId}/versions/${versionId}`)
}

/**
 * 回滚到指定版本
 */
export function rollbackVersion(documentId, versionId) {
  return request.post(`/documents/${documentId}/versions/${versionId}/rollback`)
}
