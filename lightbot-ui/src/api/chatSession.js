import request from '../utils/request'

export function createSession(agentId) {
  return request.post('/chat/sessions', null, { params: { agentId } })
}

export function getSessions(params) {
  return request.get('/chat/sessions', { params })
}

/** 企业 API 集成会话（建设者排障只读） */
export function getApiSessions(params) {
  return request.get('/chat/sessions/api-integration', { params })
}

/** 自动化任务会话（建设者排障只读） */
export function getAutomationSessions(params) {
  return request.get('/chat/sessions/automation', { params })
}

export function getSession(id, config = {}) {
  return request.get(`/chat/sessions/${id}`, config)
}

export function getSessionTitle(id) {
  return request.get(`/chat/sessions/${id}/title`)
}

export function getSessionMessages(id, params = {}) {
  return request.get(`/chat/sessions/${id}/messages`, { params })
}

export function updateSessionTitle(id, title) {
  return request.put(`/chat/sessions/${id}/title`, null, { params: { title } })
}

export function archiveSession(id) {
  return request.put(`/chat/sessions/${id}/archive`)
}

export function deleteSession(id) {
  return request.delete(`/chat/sessions/${id}`)
}

export function togglePinSession(id) {
  return request.put(`/chat/sessions/${id}/pin`)
}

export function deleteSessionsBatch(ids) {
  return request.delete('/chat/sessions/batch', { data: ids })
}

export function deleteMessage(sessionId, messageId) {
  return request.delete(`/chat/sessions/${sessionId}/messages/${messageId}`)
}

export function searchMessages(sessionId, keyword, params = {}) {
  return request.get(`/chat/sessions/${sessionId}/messages/search`, {
    params: { keyword, ...params },
  })
}

export function searchConversations(q, limit = 20) {
  return request.get('/chat/sessions/search', { params: { q, limit } })
}

export function getToolResultDetail(toolCallId) {
  return request.get(`/chat/sessions/tool-calls/${toolCallId}/result`)
}

export function exportSession(id, format = 'markdown') {
  return request.get(`/chat/sessions/${id}/export`, {
    params: { format },
    responseType: 'blob',
  })
}

export function toggleMessageStar(messageId) {
  return request.put(`/chat/sessions/messages/${messageId}/star`)
}

export function getStarredMessages(params = {}) {
  return request.get('/chat/sessions/messages/starred', { params })
}

export function getSessionAttachments(id) {
  return request.get(`/chat/sessions/${id}/attachments`)
}

export function removeSessionAttachment(sessionId, attachmentId) {
  return request.delete(`/chat/sessions/${sessionId}/attachments/${attachmentId}`)
}

export function getSessionFileTree(sessionId, path = '') {
  return request.get(`/chat/sessions/${sessionId}/files/tree`, { params: { path } })
}

export function getSessionFileContent(sessionId, path) {
  return request.get(`/chat/sessions/${sessionId}/files/content`, { params: { path } })
}

export function getSessionFileDownloadUrl(sessionId, path) {
  return request.get(`/chat/sessions/${sessionId}/files/download`, { params: { path } })
}

export function deleteSessionFile(sessionId, path) {
  return request.delete(`/chat/sessions/${sessionId}/files`, { params: { path } })
}
