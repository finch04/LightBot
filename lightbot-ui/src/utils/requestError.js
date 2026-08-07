/**
 * 请求层可预期错误（业务码 / HTTP 4xx/5xx / 网络不可用）
 *
 * request 拦截器已做 toast / 跳转时标记 handled=true。
 * ErrorBoundary / vue.errorHandler / unhandledrejection 必须忽略此类错误，
 * 否则 Modal @ok 等未 catch 的 await 会把整页打成「页面出现异常」。
 */
export class RequestError extends Error {
  /**
   * @param {string} message 用户可见文案
   * @param {{ code?: number|string, httpStatus?: number, handled?: boolean }} [options]
   */
  constructor(message, options = {}) {
    super(message || '请求失败')
    this.name = 'RequestError'
    this.code = options.code
    this.httpStatus = options.httpStatus
    /** 拦截器是否已提示/跳转，无需再当渲染崩溃处理 */
    this.handled = options.handled !== false
  }
}

/**
 * @param {unknown} err
 * @returns {boolean}
 */
export function isRequestError(err) {
  return !!(err && (err instanceof RequestError || err.name === 'RequestError' || err.isRequestError))
}

/**
 * 已由 request 层消化的错误：不应触发 ErrorBoundary 整页兜底
 * @param {unknown} err
 * @returns {boolean}
 */
export function isHandledRequestError(err) {
  return isRequestError(err) && err.handled !== false
}
