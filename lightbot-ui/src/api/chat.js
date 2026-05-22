import request from '../utils/request'

export function chat(data) {
  return request.post('/chat', data)
}

export async function chatStream(data, { onChunk, onStatus, onMetadata, onToolEvent, onDone }, signal) {
  const token = localStorage.getItem('token')
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: token || '',
    },
    body: JSON.stringify(data),
    signal,
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
        processSseLines(buffer, { onChunk, onStatus, onMetadata, onToolEvent })
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
    processSseLines(complete, { onChunk, onStatus, onMetadata, onToolEvent })
  }
}

function processSseLines(text, { onChunk, onStatus, onMetadata, onToolEvent }) {
  const lines = text.split('\n')
  for (const line of lines) {
    if (line.startsWith('data:')) {
      const content = line.substring(5)
      if (content && content !== '[DONE]') {
        // 判断消息类型
        if (content.startsWith('[STATUS]')) {
          const statusContent = content.substring(8)
          // 尝试解析为工具事件 JSON
          try {
            const parsed = JSON.parse(statusContent)
            if (parsed.type === 'tool_call' || parsed.type === 'tool_result') {
              onToolEvent?.(parsed)
              continue
            }
          } catch {
            // 不是 JSON，作为普通状态消息处理
          }
          onStatus?.(statusContent)
        } else if (content.startsWith('[METADATA]')) {
          onMetadata?.(content.substring(10)) // 移除 [METADATA] 前缀
        } else {
          onChunk?.(content)
        }
      }
    }
  }
}

export function getRagReferences(sessionId, agentId, question) {
  return request.get('/chat/rag-references', {
    params: { sessionId, agentId, question }
  })
}
