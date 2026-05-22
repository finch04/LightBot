import request from '../utils/request'

export function getModelProviders(params) {
  return request.get('/model-providers', { params })
}

export function getModelProvider(id) {
  return request.get(`/model-providers/${id}`)
}

export function createModelProvider(data) {
  return request.post('/model-providers', data)
}

export function updateModelProvider(data) {
  return request.put('/model-providers', data)
}

export function deleteModelProvider(id) {
  return request.delete(`/model-providers/${id}`)
}

export function getProviderConfigFields(id) {
  return request.get(`/model-providers/${id}/config-fields`)
}

export function checkModelProvider(id) {
  return request.get(`/model-providers/${id}/check`)
}

export function checkModelProviderByForm(data) {
  return request.post('/model-providers/check', data)
}

export function fetchProviderModels(id) {
  return request.get(`/model-providers/${id}/fetch-models`)
}

export function refreshModelProviderCache() {
  return request.post('/model-providers/refresh-cache')
}
