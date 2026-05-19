import request from '../utils/request'

export function getKnowledgeList(params) {
  return request.get('/knowledge', { params })
}

export function getKnowledge(id) {
  return request.get(`/knowledge/${id}`)
}

export function createKnowledge(data) {
  return request.post('/knowledge', data)
}

export function updateKnowledge(data) {
  return request.put('/knowledge', data)
}

export function deleteKnowledge(id) {
  return request.delete(`/knowledge/${id}`)
}

export function uploadDocument(knowledgeId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/knowledge/${knowledgeId}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function getDocuments(knowledgeId) {
  return request.get(`/knowledge/${knowledgeId}/documents`)
}

export function getDocument(docId) {
  return request.get(`/knowledge/documents/${docId}`)
}

export function deleteDocument(docId) {
  return request.delete(`/knowledge/documents/${docId}`)
}

export function previewDocument(docId) {
  return request.get(`/knowledge/documents/${docId}/preview`)
}

export function getChunks(docId) {
  return request.get(`/knowledge/documents/${docId}/chunks`)
}

export function askKnowledge(knowledgeId, question) {
  return request.post(`/knowledge/${knowledgeId}/ask`, null, { params: { question } })
}
