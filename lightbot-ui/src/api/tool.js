import request from '../utils/request'

export function getTools(params) {
  return request.get('/tools', { params })
}

export function getToolById(id) {
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
