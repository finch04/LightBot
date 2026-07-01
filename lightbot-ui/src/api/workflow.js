import request from '../utils/request'

export function getWorkflowConfig(agentId) {
  return request.get(`/agents/${agentId}/workflow`)
}

export function saveWorkflowDraft(agentId, graph) {
  return request.post(`/agents/${agentId}/workflow/draft`, graph)
}

export function publishWorkflow(agentId, graph) {
  return request.post(`/agents/${agentId}/workflow/publish`, graph)
}

export function validateWorkflow(agentId, graph) {
  return request.post(`/agents/${agentId}/workflow/validate`, graph)
}

export function listWorkflowVersions(agentId) {
  return request.get(`/agents/${agentId}/workflow/versions`)
}

export function getWorkflowVersionDetail(agentId, version) {
  return request.get(`/agents/${agentId}/workflow/versions/${version}`)
}

export function restoreWorkflowVersion(agentId, version) {
  return request.post(`/agents/${agentId}/workflow/versions/${version}/restore`)
}

export function testWorkflow(agentId, data) {
  return request.post(`/agents/${agentId}/workflow/test`, data)
}

export function testWorkflowNode(agentId, data) {
  return request.post(`/agents/${agentId}/workflow/test-node`, data)
}

export function resumeWorkflow(agentId, data) {
  return request.post(`/agents/${agentId}/workflow/resume`, data)
}
