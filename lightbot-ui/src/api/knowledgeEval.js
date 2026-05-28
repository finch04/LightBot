import request from '../utils/request'

// ==================== 评估基准 ====================

export function listBenchmarks(knowledgeId) {
  return request.get(`/knowledge/${knowledgeId}/eval/benchmarks`)
}

export function getBenchmarkDetail(knowledgeId, benchmarkId, params) {
  return request.get(`/knowledge/${knowledgeId}/eval/benchmarks/${benchmarkId}`, { params })
}

export function generateBenchmark(knowledgeId, data) {
  return request.post(`/knowledge/${knowledgeId}/eval/benchmarks/generate`, data)
}

export function uploadBenchmark(knowledgeId, name, description, file) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('name', name)
  if (description) formData.append('description', description)
  return request.post(`/knowledge/${knowledgeId}/eval/benchmarks/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deleteBenchmark(knowledgeId, benchmarkId) {
  return request.delete(`/knowledge/${knowledgeId}/eval/benchmarks/${benchmarkId}`)
}

export function downloadBenchmark(knowledgeId, benchmarkId) {
  return request.get(`/knowledge/${knowledgeId}/eval/benchmarks/${benchmarkId}/download`, {
    responseType: 'blob',
  })
}

// ==================== 评估结果 ====================

export function runEvaluation(knowledgeId, data) {
  return request.post(`/knowledge/${knowledgeId}/eval/run`, data)
}

export function listEvalResults(knowledgeId, params) {
  return request.get(`/knowledge/${knowledgeId}/eval/results`, { params })
}

export function getEvalResultDetail(knowledgeId, resultId, params) {
  return request.get(`/knowledge/${knowledgeId}/eval/results/${resultId}`, { params })
}

export function deleteEvalResult(knowledgeId, resultId) {
  return request.delete(`/knowledge/${knowledgeId}/eval/results/${resultId}`)
}
