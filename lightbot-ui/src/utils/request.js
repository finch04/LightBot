import axios from 'axios'
import { message } from 'ant-design-vue'
import router from '../router'

/**
 * 将 JSON 字符串中的 Long 类型数字转为字符串，防止前端精度丢失
 * 匹配规则：连续 16 位及以上的数字（雪花算法 ID 长度为 18-19 位）
 */
function convertLongToString(data) {
  if (typeof data !== 'string') return data
  // 匹配 JSON 值中的大数字（包括负数），避免误伤小数和普通数字
  return data.replace(/:\s*(-?\d{16,})/g, ':"$1"')
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

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code && res.code !== 200) {
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
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    }
    message.error(error.response?.data?.message || error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default request
