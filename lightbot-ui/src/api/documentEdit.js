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
