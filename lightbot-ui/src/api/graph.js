import request from '../utils/request'

// ========== 独立知识图谱 ==========

export function importGraphFromJsonl(file, providerId) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/graph/import/jsonl', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    params: providerId ? { providerId } : {},
  })
}

export function importGraphTriples(data, providerId) {
  return request.post('/graph/import', data, { params: providerId ? { providerId } : {} })
}

export function getStandaloneSubgraph(params) {
  return request.get('/graph/subgraph', { params })
}

export function semanticSearchGraph(query, topK = 10, providerId) {
  return request.get('/graph/search', { params: { query, topK, ...(providerId ? { providerId } : {}) } })
}

export function getStandaloneStats() {
  return request.get('/graph/stats')
}

export function deleteStandaloneGraph() {
  return request.delete('/graph')
}

export function createStandaloneNode(params) {
  return request.post('/graph/nodes', null, { params })
}

export function updateStandaloneNode(elementId, params) {
  return request.put(`/graph/nodes/${elementId}`, null, { params })
}

export function deleteStandaloneNode(elementId) {
  return request.delete(`/graph/nodes/${elementId}`)
}

export function createStandaloneEdge(params) {
  return request.post('/graph/edges', null, { params })
}

export function updateStandaloneEdge(elementId, params) {
  return request.put(`/graph/edges/${elementId}`, null, { params })
}

export function deleteStandaloneEdge(elementId) {
  return request.delete(`/graph/edges/${elementId}`)
}

export function getStandaloneNodeNames() {
  return request.get('/graph/node-names')
}

export function rebuildVectorIndex() {
  return request.post('/graph/rebuild-index')
}
