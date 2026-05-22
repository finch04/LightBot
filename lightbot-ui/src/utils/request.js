import axios from 'axios'
import { message } from 'ant-design-vue'
import router from '../router'

/**
 * 将 JSON 字符串中的 Long 类型数字转为字符串，防止前端精度丢失
 * 匹配规则：连续 16 位及以上的整数（雪花算法 ID 长度为 18-19 位）
 * 同时处理对象属性值（冒号后）和数组元素（逗号/方括号后）
 */
function convertLongToString(data) {
  if (typeof data !== 'string') return data
  // 匹配 JSON 值中的大数字：冒号后、逗号后、左方括号后
  return data.replace(/(?<=:\s*|,\s*|\[\s*)(-?\d{16,})(?=\s*[,\]}])/g, '"$1"')
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
      message.error(res.message || '请求失败')
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
    if (status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    }
    // 优先从 response body 中提取业务错误信息（GlobalExceptionHandler 返回的 Result）
    const res = error.response?.data
    const msg = (res?.code && res?.message)
      ? res.message
      : HTTP_STATUS_MSG[status] || '网络异常，请稍后重试'
    message.error(msg)
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
