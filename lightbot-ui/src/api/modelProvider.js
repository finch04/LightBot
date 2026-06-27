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

export function getProviderModelCapabilities(id) {
  return request.get(`/model-providers/${id}/model-capabilities`)
}

export function getProviderDefaultModel(type) {
  return request.get('/model-providers/default-model', { params: { type } })
}

export function checkModelProvider(id, config) {
  return request.get(`/model-providers/${id}/check`, config)
}

export function checkModelProviderByForm(data) {
  return request.post('/model-providers/check', data)
}

export function fetchProviderModels(id) {
  return request.get(`/model-providers/${id}/fetch-models`)
}

export function refreshModelProviderCache(config) {
  return request.post('/model-providers/refresh-cache', null, config)
}

export function getProvidersWithModels(type) {
  return request.get('/model-providers/with-models', { params: { type } })
}

export function toggleProviderStatus(id, status) {
  return request.patch(`/model-providers/${id}/status`, null, { params: { status } })
}
