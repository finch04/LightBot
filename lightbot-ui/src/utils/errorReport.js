/**
 * 前端错误上报工具
 *
 * 当前仅 console + localStorage 滚动缓冲；接入 Sentry / 自建后端时替换 captureException 实现
 */

import { isHandledRequestError } from './requestError'

const BUFFER_KEY = 'lightbot:errorLog'
const BUFFER_MAX = 50
// 与 request.js 约定的 sessionStorage 键名，用于读取最近一次后端 traceId
const TRACE_ID_KEY = 'lightbot:traceId'

/**
 * 上报异常到监控平台
 * @param {Error|any} err 错误对象
 * @param {object} [context] 额外上下文（路由、用户、自定义标签）
 */
export function captureException(err, context = {}) {
  // 1. 控制台打印完整堆栈（开发态友好）
  // eslint-disable-next-line no-console
  console.error('[ErrorBoundary]', err, context)

  // 2. 滚动缓冲到 localStorage（便于用户反馈时复制）
  try {
    const entry = {
      time: new Date().toISOString(),
      msg: err?.message || String(err),
      stack: err?.stack?.split('\n').slice(0, 5).join('\n'),
      url: location.href,
      // 附带最近一次请求的后端 traceId，便于排查"前端报错 + 后端日志"关联场景
      traceId: sessionStorage.getItem(TRACE_ID_KEY) || null,
      context,
    }
    const arr = JSON.parse(localStorage.getItem(BUFFER_KEY) || '[]')
    arr.unshift(entry)
    if (arr.length > BUFFER_MAX) arr.length = BUFFER_MAX
    localStorage.setItem(BUFFER_KEY, JSON.stringify(arr))
  } catch {
    // localStorage 满或被禁用，忽略
  }

  // 3. TODO: 接入 Sentry / 后端 /api/log/frontend
  // if (window.__errorReporter?.captureException) {
  //   window.__errorReporter.captureException(err, { extra: context })
  // }
}

/**
 * 读取近期错误日志（用于用户反馈 / 调试面板）
 */
export function getRecentErrors() {
  try {
    return JSON.parse(localStorage.getItem(BUFFER_KEY) || '[]')
  } catch {
    return []
  }
}

/**
 * 清空错误日志
 */
export function clearRecentErrors() {
  localStorage.removeItem(BUFFER_KEY)
}

/**
 * 安装全局兜底监听：捕获 Vue 之外的异常（setTimeout、fetch、Promise 等）
 */
export function installGlobalErrorHandlers() {
  // Promise reject 未被 catch
  window.addEventListener('unhandledrejection', (event) => {
    // request 已 toast 的业务错误：阻止浏览器默认噪音，也不当崩溃上报
    if (isHandledRequestError(event.reason)) {
      event.preventDefault()
      return
    }
    captureException(event.reason instanceof Error
      ? event.reason
      : new Error(String(event.reason)), { source: 'unhandledrejection' })
  })

  // 同步异常（setTimeout、事件回调等）
  window.addEventListener('error', (event) => {
    if (event.error) {
      if (isHandledRequestError(event.error)) {
        event.preventDefault()
        return
      }
      captureException(event.error, {
        source: 'window.onerror',
        filename: event.filename,
        lineno: event.lineno,
        colno: event.colno,
      })
    }
  })
}
