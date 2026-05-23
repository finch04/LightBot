import request from '../utils/request'

export function getTools(params) {
  return request.get('/tools', { params })
}

export function getTool(id) {
  return request.get(`/tools/${id}`)
}

export function createTool(data) {
  return request.post('/tools', data)
}

export function updateTool(data) {
  return request.put('/tools', data)
}

export function deleteTool(id) {
  return request.delete(`/tools/${id}`)
}

export function testTool(id, args) {
  return request.post(`/tools/${id}/test`, { args })
}
