import request from '../utils/request'

export function getEvalDatasets(params) {
  return request.get('/eval/datasets', { params })
}

export function getEvalDataset(id) {
  return request.get(`/eval/datasets/${id}`)
}

export function createEvalDataset(data) {
  return request.post('/eval/datasets', data)
}

export function updateEvalDataset(id, data) {
  return request.put(`/eval/datasets/${id}`, data)
}

export function deleteEvalDataset(id) {
  return request.delete(`/eval/datasets/${id}`)
}

export function getEvalDatasetVersions(datasetId) {
  return request.get(`/eval/datasets/${datasetId}/versions`)
}

export function createEvalDatasetVersion(data) {
  return request.post('/eval/datasets/versions', data)
}

export function getEvalDatasetItems(params) {
  return request.get('/eval/datasets/items', { params })
}

export function createEvalDatasetItem(data) {
  return request.post('/eval/datasets/items', data)
}

export function batchCreateEvalDatasetItems(data) {
  return request.post('/eval/datasets/items/batch', data)
}

export function deleteEvalDatasetItem(id) {
  return request.delete(`/eval/datasets/items/${id}`)
}
