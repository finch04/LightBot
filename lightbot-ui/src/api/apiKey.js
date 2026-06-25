import request from '../utils/request'

/** 查询当前用户的 API Key 列表 */
export function listApiKeys() {
  return request.get('/api-keys')
}

/** 创建 API Key */
export function createApiKey(data) {
  return request.post('/api-keys', data)
}

/** 启用/禁用 API Key */
export function toggleApiKey(id) {
  return request.patch(`/api-keys/${id}/toggle`)
}

/** 删除 API Key */
export function deleteApiKey(id) {
  return request.delete(`/api-keys/${id}`)
}

/** 重新生成 API Key */
export function regenerateApiKey(id) {
  return request.post(`/api-keys/${id}/regenerate`)
}
