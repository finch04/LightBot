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
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      // 处理 buffer 中残留的数据
      if (buffer.trim()) {
        processSseLines(buffer, onChunk)
      }
      onDone?.()
      break
    }
    buffer += decoder.decode(value, { stream: true })
    // 按完整行处理，保留末尾不完整的行在 buffer 中
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
