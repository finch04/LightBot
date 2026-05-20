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

export function generateMindmap(knowledgeId) {
  return request.post(`/knowledge/${knowledgeId}/mindmap`)
}

export function getMindmap(knowledgeId) {
  return request.get(`/knowledge/${knowledgeId}/mindmap`)
}

// ========== 成员管理 ==========

export function getKnowledgeMembers(knowledgeId) {
  return request.get(`/knowledge/${knowledgeId}/members`)
}

export function addKnowledgeMember(knowledgeId, userId, role) {
  return request.post(`/knowledge/${knowledgeId}/members`, null, { params: { userId, role } })
}

export function updateKnowledgeMemberRole(knowledgeId, userId, role) {
  return request.put(`/knowledge/${knowledgeId}/members/${userId}`, null, { params: { role } })
}

export function removeKnowledgeMember(knowledgeId, userId) {
  return request.delete(`/knowledge/${knowledgeId}/members/${userId}`)
}
