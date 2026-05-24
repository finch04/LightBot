import request from '../utils/request'

export function getMcpServers(params) {
  return request.get('/mcp-servers', { params })
}

export function getMcpServer(id) {
  return request.get(`/mcp-servers/${id}`)
}

export function createMcpServer(data) {
  return request.post('/mcp-servers', data)
}

export function updateMcpServer(data) {
  return request.put('/mcp-servers', data)
}

export function deleteMcpServer(id) {
  return request.delete(`/mcp-servers/${id}`)
}

export function testMcpServer(id) {
  return request.post(`/mcp-servers/${id}/test`)
}

export function getMcpServerTools(id) {
  return request.get(`/mcp-servers/${id}/tools`)
}

export function refreshMcpServerTools(id) {
  return request.post(`/mcp-servers/${id}/tools/refresh`)
}

export function toggleMcpTool(serverId, toolName) {
  return request.put(`/mcp-servers/${serverId}/tools/${toolName}/toggle`)
}