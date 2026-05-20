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
