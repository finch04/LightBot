import request from '../utils/request'

export function getExperiments(params) {
  return request.get('/eval/experiments', { params })
}

export function getExperiment(id) {
  return request.get(`/eval/experiments/${id}`)
}

export function createExperiment(data) {
  return request.post('/eval/experiments', data)
}

export function stopExperiment(id) {
  return request.put(`/eval/experiments/${id}/stop`)
}

export function deleteExperiment(id) {
  return request.delete(`/eval/experiments/${id}`)
}

export function restartExperiment(id) {
  return request.put(`/eval/experiments/${id}/restart`)
}

export function getExperimentResults(id) {
  return request.get(`/eval/experiments/${id}/results`)
}

export function getExperimentDetailResults(id, params) {
  return request.get(`/eval/experiments/${id}/detail`, { params })
}
