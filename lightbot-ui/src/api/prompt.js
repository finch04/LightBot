import request from '../utils/request'

export function getPrompts(params) {
  return request.get('/prompts', { params })
}

export function getPrompt(id) {
  return request.get(`/prompts/${id}`)
}

export function createPrompt(data) {
  return request.post('/prompts', data)
}

export function updatePrompt(id, data) {
  return request.put('/prompts', data, { params: { id } })
}

export function deletePrompt(id) {
  return request.delete(`/prompts/${id}`)
}

export function getPromptVersions(promptKey) {
  return request.get(`/prompts/${promptKey}/versions`)
}

export function createPromptVersion(data) {
  return request.post('/prompts/versions', data)
}

export function getPromptVersionDetail(promptKey, version) {
  return request.get('/prompts/versions/detail', { params: { promptKey, version } })
}

export function getPromptTemplates() {
  return request.get('/prompts/templates')
}

export function getPromptTemplate(key) {
  return request.get(`/prompts/templates/${key}`)
}

/**
 * 流式运行Prompt调试（SSE）
 */
export async function runPromptStream(data, { onChunk, onDone, onError }, signal) {
  const token = localStorage.getItem('token')
  const response = await fetch('/api/prompts/run', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: token || '',
    },
    body: JSON.stringify(data),
    signal,
  })

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    onError?.(text || '流式请求失败')
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      onDone?.()
      break
    }
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''
    for (const line of lines) {
      if (line.startsWith('data:')) {
        const content = line.substring(5).trimStart()
        if (content) {
          onChunk?.(content)
        }
      }
    }
  }
}
