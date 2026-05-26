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
  let doneFired = false
  const fireDone = () => {
    if (!doneFired) {
      doneFired = true
      onDone?.()
    }
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      // 处理 buffer 中残留的数据
      if (buffer.trim()) {
        processSseLines(buffer, { onChunk, onStatus, onMetadata, onToolEvent, onDone: fireDone })
      }
      fireDone()
      break
    }
    buffer += decoder.decode(value, { stream: true })
    // 按完整行处理，保留末尾不完整的行在 buffer 中
    const lastNewline = buffer.lastIndexOf('\n')
    if (lastNewline === -1) continue
    const complete = buffer.substring(0, lastNewline)
    buffer = buffer.substring(lastNewline + 1)
    processSseLines(complete, { onChunk, onStatus, onMetadata, onToolEvent, onDone: fireDone })
  }
}

function decodeSseTextContent(raw) {
  if (!raw) return ''
  let content = raw
  // Spring SseEmitter 可能对字符串做 JSON 编码
  if (content.startsWith('"') && content.endsWith('"')) {
    try {
      content = JSON.parse(content)
    } catch {
      // 保持原样
    }
  }
  // 后端将 \n 转义为 \\n 防止 SSE 行截断
  return content.replace(/\\n/g, '\n').replace(/\\r/g, '\r').replace(/\\t/g, '\t')
}

function processSseLines(text, { onChunk, onStatus, onMetadata, onToolEvent, onDone }) {
  const lines = text.split('\n')
  for (const line of lines) {
    if (line.startsWith('data:')) {
      let content = line.substring(5)
      if (content === '[DONE]') {
        onDone?.()
        continue
      }
      if (content) {
        // 判断消息类型（先检查前缀，再处理内容）
        if (content.startsWith('[STATUS]')) {
          const statusContent = content.substring(8)
          // 尝试解析为工具事件 JSON
          try {
            const parsed = JSON.parse(statusContent)
            if (parsed.type === 'tool_call' || parsed.type === 'tool_result' || parsed.type === 'tool_status' || parsed.type === 'tool_complete' || parsed.type === 'reasoning_content'
                || parsed.type === 'workflow_node_start' || parsed.type === 'workflow_node_complete' || parsed.type === 'workflow_complete'
                || parsed.type === 'sensitive_block') {
              onToolEvent?.(parsed)
              continue
            }
          } catch {
            // 不是 JSON，作为普通状态消息处理
          }
          onStatus?.(statusContent)
        } else if (content.startsWith('[METADATA]')) {
          onMetadata?.(content.substring(10))
        } else {
          onChunk?.(decodeSseTextContent(content))
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

export function uploadChatAttachment(agentId, sessionId, file) {
  const formData = new FormData()
  formData.append('file', file)
  const params = { agentId }
  if (sessionId) params.sessionId = sessionId
  return request.post('/chat/attachments', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    params,
  })
}
