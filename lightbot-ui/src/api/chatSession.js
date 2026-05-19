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

export function getSessionMessages(id) {
  return request.get(`/chat/sessions/${id}/messages`)
}

export function updateSessionTitle(id, title) {
  return request.put(`/chat/sessions/${id}/title`, null, { params: { title } })
}

export function archiveSession(id) {
  return request.put(`/chat/sessions/${id}/archive`)
}
