import request from '../utils/request'

export function createSession(agentId) {
  return request.post('/chat/sessions', null, { params: { agentId } })
}

export function getSessions(params) {
  return request.get('/chat/sessions', { params })
}

export function getSession(id) {
  return request.get(`/chat/sessions/${id}`)
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

export function exportSession(id, format = 'markdown') {
  return request.get(`/chat/sessions/${id}/export`, {
    params: { format },
    responseType: 'blob',
  })
}
