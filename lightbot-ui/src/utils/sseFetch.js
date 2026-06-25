/**
 * 通用 SSE（Server-Sent Events）流式请求工具
 * 基于 fetch API，支持 Authorization Header 传递 token
 *
 * @param {string} url - 请求地址
 * @param {Object} options
 * @param {string} options.token - 认证 token
 * @param {string} [options.method='GET'] - HTTP 方法
 * @param {Object} [options.body] - 请求体（POST 时自动 JSON.stringify）
 * @param {function} options.onEvent - 收到事件回调 ({ event, data })
 * @param {function} [options.onDone] - 流结束回调
 * @param {function} [options.onError] - 错误回调
 * @param {AbortSignal} [options.signal] - 取消信号
 * @param {number} [options.maxRetries=0] - 最大重试次数（0 表示不重试）
 * @param {number} [options.retryDelay=2000] - 重试基础延迟（ms），指数退避
 * @returns {{ close: () => void }} 控制句柄
 */
export function sseFetch(url, { token, method = 'GET', body, onEvent, onDone, onError, signal, maxRetries = 0, retryDelay = 2000 }) {
  let aborted = false
  let retries = 0

  function buildHeaders() {
    const h = {}
    if (token) h['Authorization'] = token
    if (body) h['Content-Type'] = 'application/json'
    return h
  }

  function parseSseLines(text) {
    const events = []
    let currentEvent = ''
    let currentData = ''
    for (const line of text.split('\n')) {
      if (line.startsWith('event:')) {
        currentEvent = line.substring(6).trim()
      } else if (line.startsWith('data:')) {
        const chunk = line.substring(5).trimStart()
        currentData += chunk
      } else if (line === '') {
        if (currentData) {
          events.push({ event: currentEvent || 'message', data: currentData })
        }
        currentEvent = ''
        currentData = ''
      }
    }
    return events
  }

  async function attempt() {
    const fetchOptions = { method, headers: buildHeaders(), signal }
    if (body) fetchOptions.body = JSON.stringify(body)
    const response = await fetch(url, fetchOptions)
    if (!response.ok) throw new Error(`SSE 请求失败: ${response.status}`)

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lastNewline = buffer.lastIndexOf('\n')
        if (lastNewline === -1) continue
        const complete = buffer.substring(0, lastNewline)
        buffer = buffer.substring(lastNewline + 1)
        for (const evt of parseSseLines(complete)) {
          onEvent?.(evt)
        }
      }
      if (buffer.trim()) {
        for (const evt of parseSseLines(buffer)) {
          onEvent?.(evt)
        }
      }
    } catch (err) {
      if (err.name === 'AbortError') return
      throw err
    }
  }

  async function run() {
    while (!aborted) {
      try {
        await attempt()
        onDone?.()
        return
      } catch (err) {
        if (err.name === 'AbortError' || signal?.aborted) return
        retries++
        if (retries > maxRetries) {
          onError?.(err)
          return
        }
        const delay = retryDelay * Math.pow(2, retries - 1)
        await new Promise(r => setTimeout(r, delay))
      }
    }
  }

  run()

  return {
    close() {
      aborted = true
    }
  }
}
