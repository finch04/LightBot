import request from '../utils/request'

export function chat(data) {
  return request.post('/chat', data)
}

/**
 * 带自动重连的流式对话
 * @param {Object} data - 对话请求数据
 * @param {Object} callbacks - 回调函数 { onChunk, onStatus, onMetadata, onToolEvent, onRequestId, onDone }
 * @param {AbortSignal} signal - 取消信号
 * @param {Object} options - 配置项 { maxRetries, retryDelay, onReconnecting }
 */
export async function chatStream(data, { onChunk, onStatus, onMetadata, onToolEvent, onRequestId, onDone }, signal, options = {}) {
  const { maxRetries = 3, retryDelay = 2000, onReconnecting } = options
  // SSE 场景必须直读 localStorage：fetch 早于 Pinia store 水合，从 store 取 token 可能为 null
  const token = localStorage.getItem('token')
  let retries = 0
  // 1.2 断线重连：追踪请求状态
  let currentRequestId = null
  let lastEventId = null
  let receivedDone = false

  // 包装回调：同步追踪 requestId / eventId / done 状态
  const trackingCallbacks = {
    onChunk,
    onStatus,
    onMetadata,
    onToolEvent,
    onRequestId: (rid) => {
      currentRequestId = rid
      onRequestId?.(rid)
    },
    onDone: (meta) => {
      receivedDone = true
      onDone?.(meta)
    },
    onEventId: (id) => { lastEventId = id },
  }

  async function attempt() {
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
      throw new Error(`流式请求失败: ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let doneFired = false
    const fireDone = (meta) => {
      if (!doneFired) {
        doneFired = true
        trackingCallbacks.onDone?.(meta)
      }
    }

    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          if (buffer.trim()) {
            processSseLines(buffer, { ...trackingCallbacks, onDone: fireDone })
          }
          fireDone()
          break
        }
        buffer += decoder.decode(value, { stream: true })
        const lastNewline = buffer.lastIndexOf('\n')
        if (lastNewline === -1) continue
        const complete = buffer.substring(0, lastNewline)
        buffer = buffer.substring(lastNewline + 1)
        processSseLines(complete, { ...trackingCallbacks, onDone: fireDone })
      }
    } catch (err) {
      if (err.name === 'AbortError') {
        fireDone()
        throw err
      }
      throw err
    }
  }

  while (retries <= maxRetries) {
    try {
      await attempt()
      return
    } catch (err) {
      if (err.name === 'AbortError') return
      retries++
      if (retries > maxRetries || signal?.aborted) {
        throw err
      }

      // 1.2 断线重连：如果有 requestId 且未收到 [DONE]，先尝试从缓冲恢复
      if (currentRequestId && !receivedDone) {
        onReconnecting?.()
        try {
          const reconnected = await tryReconnect(currentRequestId, lastEventId, trackingCallbacks, token)
          if (reconnected) return
        } catch {
          // 重连失败，退避后全量重启
        }
      }

      const delay = retryDelay * Math.pow(2, retries - 1)
      await new Promise(resolve => setTimeout(resolve, delay))
    }
  }
}

/**
 * 1.2 断线重连：从服务端事件缓冲恢复
 * @returns {boolean} 是否恢复成功
 */
async function tryReconnect(requestId, lastEventId, callbacks, token) {
  const response = await fetch('/api/chat/reconnect', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: token || '',
    },
    body: JSON.stringify({ requestId, lastEventId: lastEventId ? Number(lastEventId) : null }),
  })

  if (!response.ok) return false

  const result = await response.json()
  if (result.code !== 200) return false

  const { status, events } = result.data || {}

  if (status === 'already_delivered') {
    // 前端已收到全部事件，只需触发 onDone
    callbacks.onDone?.()
    return true
  }

  if (status === 'not_found' || status === 'cancelled') return false

  // 重放缓存事件
  if (events && events.length > 0) {
    for (const event of events) {
      processSseLines(`data:${event.data}\n`, callbacks)
    }
  }

  if (status === 'completed') {
    return true
  }

  return false
}

function decodeSseTextContent(raw) {
  if (!raw) return ''
  let content = raw
  if (content.startsWith('"') && content.endsWith('"')) {
    try {
      content = JSON.parse(content)
    } catch {
      // 保持原样
    }
  }
  return content.replace(/\\n/g, '\n').replace(/\\r/g, '\r').replace(/\\t/g, '\t')
}

function processSseLines(text, { onChunk, onStatus, onMetadata, onToolEvent, onRequestId, onDone, onEventId }) {
  const lines = text.split('\n')
  let currentEventId = null
  for (const line of lines) {
    // SSE 注释行（以冒号开头）：客户端应忽略，用于心跳保活
    if (line.startsWith(':')) continue
    // 1.2 解析 SSE 事件 ID（用于断线重连）
    if (line.startsWith('id:')) {
      currentEventId = line.substring(3).trim()
      continue
    }
    if (line.startsWith('data:')) {
      let content = line.substring(5)
      if (content.startsWith('[DONE]')) {
        const jsonStr = content.substring(6).trim()
        if (jsonStr) {
          try { onDone?.(JSON.parse(jsonStr)) } catch { onDone?.() }
        } else {
          onDone?.()
        }
        if (currentEventId) { onEventId?.(currentEventId); currentEventId = null }
        continue
      }
      if (content) {
        if (content.startsWith('[STATUS]')) {
          const statusContent = content.substring(8)
          try {
            const parsed = JSON.parse(statusContent)
            if (parsed.type === 'tool_call' || parsed.type === 'tool_result' || parsed.type === 'tool_status' || parsed.type === 'tool_complete' || parsed.type === 'reasoning_content'
                || parsed.type === 'workflow_node_start' || parsed.type === 'workflow_node_complete' || parsed.type === 'workflow_complete' || parsed.type === 'workflow_llm_chunk'
                || parsed.type === 'sensitive_block'
                || parsed.type === 'skill_active' || parsed.type === 'subagent_call' || parsed.type === 'subagent_result'
                || parsed.type === 'subagent_token' || parsed.type === 'subagent_tool_call' || parsed.type === 'subagent_tool_result'
                || parsed.type === 'error') {
              onToolEvent?.(parsed)
              if (currentEventId) { onEventId?.(currentEventId); currentEventId = null }
              continue
            }
          } catch {
            // 不是 JSON，作为普通状态消息处理
          }
          onStatus?.(statusContent)
        } else if (content.startsWith('[METADATA]')) {
          onMetadata?.(content.substring(10))
        } else if (content.startsWith('[REQUEST_ID]')) {
          onRequestId?.(content.substring(12))
        } else {
          onChunk?.(decodeSseTextContent(content))
        }
        if (currentEventId) { onEventId?.(currentEventId); currentEventId = null }
      }
    }
  }
}

export function getRagReferences(sessionId, agentId, question) {
  return request.get('/chat/rag-references', {
    params: { sessionId, agentId, question }
  })
}

export function submitMessageFeedback(messageId, data) {
  return request.post(`/chat/messages/${messageId}/feedback`, data)
}

export function getMessageFeedback(messageId) {
  return request.get(`/chat/messages/${messageId}/feedback`)
}

export function batchGetMessageFeedbacks(messageIds) {
  return request.post('/chat/messages/feedbacks/batch', messageIds)
}

export function listMessageFeedbacks(pageNum = 1, pageSize = 20) {
  return request.get('/chat/feedbacks', { params: { pageNum, pageSize } })
}

export function getFeedbackStats() {
  return request.get('/chat/feedbacks/stats')
}

export function refreshChatAttachmentPreviews(attachments) {
  return request.post('/chat/attachments/refresh-preview', attachments || [])
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
