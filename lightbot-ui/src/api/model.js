import request from '../utils/request'

export function getModelsByProvider(providerId) {
  return request.get(`/models/by-provider/${providerId}`)
}

export function getModelsByType(type) {
  return request.get(`/models/by-type/${type}`)
}

export function createModel(data) {
  return request.post('/models', data)
}

export function deleteModel(id) {
  return request.delete(`/models/${id}`)
}
