import request from '../utils/request'

export function getAgents(params) {
  return request.get('/agents', { params })
}

export function getAgent(id) {
  return request.get(`/agents/${id}`)
}

export function getAgentDetail(id) {
  return request.get(`/agents/${id}/detail`)
}

export function createAgent(data) {
  return request.post('/agents', data)
}

export function updateAgent(data) {
  return request.put('/agents', data)
}

export function deleteAgent(id) {
  return request.delete(`/agents/${id}`)
}

export function updateAgentKnowledge(id, knowledgeIds) {
  return request.put(`/agents/${id}/knowledge`, knowledgeIds)
}

export function getAgentKnowledgeIds(id) {
  return request.get(`/agents/${id}/knowledge`)
}
