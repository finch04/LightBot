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

export function generateAgentPrompt(id) {
  return request.post(`/agents/${id}/generate-prompt`)
}

export function generateAgentQuestions(id) {
  return request.post(`/agents/${id}/generate-questions`)
}

export function uploadAgentAvatar(id, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/agents/${id}/avatar`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function setDefaultAgent(id) {
  return request.put(`/agents/${id}/default`)
}