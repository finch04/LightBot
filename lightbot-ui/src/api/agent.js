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

export function updateAgentTools(id, toolIds) {
  return request.put(`/agents/${id}/tools`, toolIds)
}

export function getAgentToolIds(id) {
  return request.get(`/agents/${id}/tools`)
}

export function getAgentToolDetails(id) {
  return request.get(`/agents/${id}/tools/detail`)
}

export function getAgentMcpServerIds(id) {
  return request.get(`/agents/${id}/mcp-servers`)
}

export function updateAgentMcpServers(id, mcpServerIds) {
  return request.put(`/agents/${id}/mcp-servers`, mcpServerIds)
}

export function getAgentMcpServerDetails(id) {
  return request.get(`/agents/${id}/mcp-servers/detail`)
}

export function getAgentSubAgentIds(id) {
  return request.get(`/agents/${id}/subagents`)
}

export function updateAgentSubAgents(id, subAgentIds) {
  return request.put(`/agents/${id}/subagents`, subAgentIds)
}