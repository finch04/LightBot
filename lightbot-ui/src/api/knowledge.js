import request from '../utils/request'

export function getKnowledgeList(params) {
  return request.get('/knowledge', { params })
}

export function getKnowledge(id) {
  return request.get(`/knowledge/${id}`)
}

export function createKnowledge(data) {
  return request.post('/knowledge', data)
}

export function updateKnowledge(data) {
  return request.put('/knowledge', data)
}

export function deleteKnowledge(id) {
  return request.delete(`/knowledge/${id}`)
}

export function uploadDocument(knowledgeId, file, ocrEnabled = false) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/knowledge/${knowledgeId}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    params: { ocrEnabled },
  })
}

export function uploadDocuments(knowledgeId, files, ocrEnabled = false) {
  const formData = new FormData()
  files.forEach(file => formData.append('files', file))
  return request.post(`/knowledge/${knowledgeId}/documents/batch`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    params: { ocrEnabled },
  })
}

export function previewUrlDocument(knowledgeId, url) {
  return request.post(`/knowledge/${knowledgeId}/documents/preview-url`, null, {
    params: { url },
  })
}

export function saveUrlDocument(knowledgeId, data) {
  return request.post(`/knowledge/${knowledgeId}/documents/save-url`, data)
}

export function fetchUrlDocument(knowledgeId, url) {
  return request.post(`/knowledge/${knowledgeId}/documents/fetch-url`, null, {
    params: { url },
  })
}

export function ingestDocument(docId, data) {
  return request.post(`/knowledge/documents/${docId}/ingest`, data)
}

export function previewChunks(docId, data) {
  return request.post(`/knowledge/documents/${docId}/preview-chunks`, data)
}

export function getDocuments(knowledgeId, params) {
  return request.get(`/knowledge/${knowledgeId}/documents`, { params })
}

export function getDocument(docId) {
  return request.get(`/knowledge/documents/${docId}`)
}

export function deleteDocument(docId) {
  return request.delete(`/knowledge/documents/${docId}`)
}

export function previewDocument(docId) {
  return request.get(`/knowledge/documents/${docId}/preview`)
}

export function getChunks(docId) {
  return request.get(`/knowledge/documents/${docId}/chunks`)
}

export function getDocumentDownloadUrl(docId) {
  return request.get(`/knowledge/documents/${docId}/download`)
}

export function getDefaultIngestConfig(knowledgeId) {
  return request.get(`/knowledge/${knowledgeId}/default-ingest-config`)
}

export function searchKnowledge(knowledgeId, question) {
  return request.get(`/knowledge/${knowledgeId}/search`, { params: { question } })
}

export function generateMindmap(knowledgeId) {
  return request.post(`/knowledge/${knowledgeId}/mindmap`)
}

export function getMindmap(knowledgeId) {
  return request.get(`/knowledge/${knowledgeId}/mindmap`)
}

// ========== 成员管理 ==========

export function getKnowledgeMembers(knowledgeId) {
  return request.get(`/knowledge/${knowledgeId}/members`)
}

export function addKnowledgeMember(knowledgeId, userId, role) {
  return request.post(`/knowledge/${knowledgeId}/members`, null, { params: { userId, role } })
}

export function updateKnowledgeMemberRole(knowledgeId, userId, role) {
  return request.put(`/knowledge/${knowledgeId}/members/${userId}`, null, { params: { role } })
}

export function removeKnowledgeMember(knowledgeId, userId) {
  return request.delete(`/knowledge/${knowledgeId}/members/${userId}`)
}

// ========== 示例问题 ==========

export function getExampleQuestions(knowledgeId) {
  return request.get(`/knowledge/${knowledgeId}/example-questions`)
}

export function updateExampleQuestions(knowledgeId, questions) {
  return request.put(`/knowledge/${knowledgeId}/example-questions`, questions)
}

export function generateOneExampleQuestion(knowledgeId) {
  return request.post(`/knowledge/${knowledgeId}/example-questions/generate`)
}

export function generateExampleQuestions(knowledgeId) {
  return request.post(`/knowledge/${knowledgeId}/generate-questions`)
}

// ========== OCR ==========

export function checkOcrHealth() {
  return request.get('/ocr/health')
}

// ========== 知识图谱 ==========

export function getGraphSubgraph(knowledgeId, params) {
  return request.get(`/knowledge/${knowledgeId}/graph/subgraph`, { params })
}

export function getGraphStats(knowledgeId, documentId) {
  return request.get(`/knowledge/${knowledgeId}/graph/stats`, {
    params: documentId ? { documentId } : {}
  })
}

export function extractGraph(knowledgeId, documentIds, providerId, modelId) {
  const params = {}
  if (documentIds && documentIds.length > 0) params.documentIds = documentIds
  if (providerId) params.providerId = providerId
  if (modelId) params.modelId = modelId
  return request.post(`/knowledge/${knowledgeId}/graph/extract`, null, {
    params,
    paramsSerializer: p => {
      const parts = []
      for (const key in p) {
        const val = p[key]
        if (Array.isArray(val)) {
          val.forEach(v => parts.push(`${key}=${v}`))
        } else {
          parts.push(`${key}=${val}`)
        }
      }
      return parts.join('&')
    }
  })
}

export function importGraphTriples(knowledgeId, data) {
  return request.post(`/knowledge/${knowledgeId}/graph/import`, data)
}

export function deleteGraph(knowledgeId) {
  return request.delete(`/knowledge/${knowledgeId}/graph`)
}

export function deleteDocGraph(knowledgeId, documentId) {
  return request.delete(`/knowledge/${knowledgeId}/graph/documents/${documentId}`)
}

export function getExistingDocIds(knowledgeId, documentIds) {
  return request.get(`/knowledge/${knowledgeId}/graph/existing-docs`, {
    params: { documentIds },
    paramsSerializer: p => {
      const parts = []
      for (const key in p) {
        const val = p[key]
        if (Array.isArray(val)) {
          val.forEach(v => parts.push(`${key}=${v}`))
        } else if (val != null) {
          parts.push(`${key}=${val}`)
        }
      }
      return parts.join('&')
    }
  })
}

export function createGraphNode(knowledgeId, params) {
  return request.post(`/knowledge/${knowledgeId}/graph/nodes`, null, { params })
}

export function deleteGraphNode(knowledgeId, elementId) {
  return request.delete(`/knowledge/${knowledgeId}/graph/nodes/${elementId}`)
}

export function createGraphEdge(knowledgeId, params) {
  return request.post(`/knowledge/${knowledgeId}/graph/edges`, null, { params })
}

export function deleteGraphEdge(knowledgeId, elementId) {
  return request.delete(`/knowledge/${knowledgeId}/graph/edges/${elementId}`)
}

// ========== 问答对 ==========

export function getQAPairs(knowledgeId, params) {
  return request.get(`/knowledge/${knowledgeId}/qa-pairs`, { params })
}

export function createQAPair(knowledgeId, data) {
  return request.post(`/knowledge/${knowledgeId}/qa-pairs`, data)
}

export function updateQAPair(qaPairId, data) {
  return request.put(`/knowledge/qa-pairs/${qaPairId}`, data)
}

export function deleteQAPair(qaPairId) {
  return request.delete(`/knowledge/qa-pairs/${qaPairId}`)
}

export function batchImportQAPairs(knowledgeId, items) {
  return request.post(`/knowledge/${knowledgeId}/qa-pairs/batch-import`, items)
}

export function generateQAPairs(knowledgeId, params) {
  return request.post(`/knowledge/${knowledgeId}/qa-pairs/ai-generate`, null, { params })
}
