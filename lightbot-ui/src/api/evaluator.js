import request from '../utils/request'

export function getEvaluators(params) {
  return request.get('/eval/evaluators', { params })
}

export function getEvaluator(id) {
  return request.get(`/eval/evaluators/${id}`)
}

export function createEvaluator(data) {
  return request.post('/eval/evaluators', data)
}

export function updateEvaluator(id, data) {
  return request.put(`/eval/evaluators/${id}`, data)
}

export function deleteEvaluator(id) {
  return request.delete(`/eval/evaluators/${id}`)
}

export function getEvaluatorVersions(evaluatorId) {
  return request.get(`/eval/evaluators/${evaluatorId}/versions`)
}

export function createEvaluatorVersion(data) {
  return request.post('/eval/evaluators/versions', data)
}

export function getEvaluatorTemplates() {
  return request.get('/eval/evaluators/templates')
}

export function getEvaluatorTemplate(key) {
  return request.get(`/eval/evaluators/templates/${key}`)
}

export function testEvaluator(data) {
  return request.post('/eval/evaluators/test', data)
}
