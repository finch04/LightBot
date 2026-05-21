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

export function askKnowledge(knowledgeId, question) {
  return request.post(`/knowledge/${knowledgeId}/ask`, null, { params: { question } })
}

export async function askKnowledgeStream(knowledgeId, question, onChunk, onDone) {
  const token = localStorage.getItem('token')
  const response = await fetch(`/api/knowledge/${knowledgeId}/ask-stream?question=${encodeURIComponent(question)}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: token || '',
    },
  })

  if (!response.ok) {
    throw new Error('流式请求失败')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      if (buffer.trim()) {
        processSseLines(buffer, onChunk)
      }
      onDone?.()
      break
    }
    buffer += decoder.decode(value, { stream: true })
    const lastNewline = buffer.lastIndexOf('\n')
    if (lastNewline === -1) continue
    const complete = buffer.substring(0, lastNewline)
    buffer = buffer.substring(lastNewline + 1)
    processSseLines(complete, onChunk)
  }
}

function processSseLines(text, onChunk) {
  const lines = text.split('\n')
  for (const line of lines) {
    if (line.startsWith('data:')) {
      const content = line.substring(5)
      if (content && content !== '[DONE]') {
        onChunk?.(content)
      }
    }
  }
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

export function generateExampleQuestions(knowledgeId) {
  return request.post(`/knowledge/${knowledgeId}/generate-questions`)
}

// ========== OCR ==========

export function checkOcrHealth() {
  return request.get('/ocr/health')
}
