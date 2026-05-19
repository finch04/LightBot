import request from '../utils/request'

export function getAgents(params) {
  return request.get('/agents', { params })
}

export function getAgent(id) {
  return request.get(`/agents/${id}`)
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
