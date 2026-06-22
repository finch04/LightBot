import axios from 'axios'
import { message } from 'ant-design-vue'
import router from '../router'

/**
 * 将 JSON 字符串中的 Long 类型数字转为字符串，防止前端精度丢失
 * 只转换字符串外部的数字，跳过字符串值内部的内容（避免破坏嵌套 JSON）
 */
function convertLongToString(data) {
  if (typeof data !== 'string') return data
  let result = ''
  let i = 0
  let inString = false
  while (i < data.length) {
    const ch = data[i]
    if (inString) {
      if (ch === '\\') {
        result += data[i] + data[i + 1]
        i += 2
        continue
      }
      if (ch === '"') {
        inString = false
        result += ch
        i++
        continue
      }
      result += ch
      i++
      continue
    }
    // 非字符串区域：检测连续数字（16位及以上）
    if (ch >= '0' && ch <= '9' || (ch === '-' && i + 1 < data.length && data[i + 1] >= '0' && data[i + 1] <= '9')) {
      let num = ch
      let j = i + 1
      while (j < data.length && data[j] >= '0' && data[j] <= '9') {
        num += data[j]
        j++
      }
      if (num.replace('-', '').length >= 16) {
        result += '"' + num + '"'
      } else {
        result += num
      }
      i = j
      continue
    }
    if (ch === '"') {
      inString = true
    }
    result += ch
    i++
  }
  return result
}

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  // 在 transformResponse 中处理 Long 类型精度丢失
  transformResponse: [
    ...axios.defaults.transformResponse,
    (data) => {
      if (typeof data === 'string') {
        try {
          const converted = convertLongToString(data)
          return JSON.parse(converted)
        } catch {
          return data
        }
      }
      return data
    },
  ],
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['Authorization'] = token
  }
  return config
})

/** HTTP状态码对应的用户友好提示 */
const HTTP_STATUS_MSG = {
  400: '请求参数错误',
  401: '未登录或登录已过期',
  403: '无权访问',
  404: '请求的资源不存在',
  500: '服务器内部错误',
  502: '服务暂时不可用',
  503: '服务暂时不可用',
}

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code && res.code !== 200) {
      // 业务错误（HTTP 200 但 code !== 200）：使用后端返回的 message
      if (!response.config?.silent) {
        message.error(res.message || '请求失败')
      }
      if (res.code === 401) {
        localStorage.removeItem('token')
        router.push('/login')
      }
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  (error) => {
    const status = error.response?.status

    // 无响应（后端未启动/网络断开）→ 记录当前路径后跳 Landing 页
    if (!error.response) {
      if (!error.config?.silent) {
        message.error('服务不可用，请稍后重试')
      }
      try {
        const current = router.currentRoute.value
        if (current && !current.meta?.public) {
          const fullPath = current.fullPath
          localStorage.setItem('lastRoute', fullPath)
        }
      } catch { /* ignore */ }
      router.push('/')
      return Promise.reject(error)
    }

    // 401 → 跳登录页
    if (status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    }

    // 优先从 response body 中提取业务错误信息（GlobalExceptionHandler 返回的 Result）
    const res = error.response?.data
    const msg = (res?.code && res?.message)
      ? res.message
      : HTTP_STATUS_MSG[status] || '网络异常，请稍后重试'
    if (!error.config?.silent) {
      message.error(msg)
    }
    return Promise.reject(new Error(msg))
  }
)

export default request

/**
 * 安全的 JSON.parse，处理 Long 类型精度丢失
 * 用于 SSE 流式数据等绕过 axios 拦截器的场景
 */
export function safeJsonParse(jsonStr) {
  if (typeof jsonStr !== 'string') return jsonStr
  try {
    return JSON.parse(convertLongToString(jsonStr))
  } catch {
    return null
  }
}
