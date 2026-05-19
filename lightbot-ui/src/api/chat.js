import request from '../utils/request'

export function chat(data) {
  return request.post('/chat', data)
}

export async function chatStream(data, onChunk, onDone) {
  const token = localStorage.getItem('token')
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: token || '',
    },
    body: JSON.stringify(data),
  })

  if (!response.ok) {
    throw new Error('流式请求失败')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      onDone?.()
      break
    }
    const text = decoder.decode(value, { stream: true })
    // SSE 格式: data:xxx\n\n
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
}
