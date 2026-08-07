import request from '../utils/request'

/** 查询企业 API Key 列表（管理员） */
export function listApiKeys() {
  return request.get('/api-keys')
}

/** 创建企业 API Key（管理员） */
export function createApiKey(data) {
  return request.post('/api-keys', data)
}

/** 启用/禁用企业 API Key（管理员） */
export function toggleApiKey(id) {
  return request.patch(`/api-keys/${id}/toggle`)
}

/** 删除企业 API Key（管理员） */
export function deleteApiKey(id) {
  return request.delete(`/api-keys/${id}`)
}

/** 重新生成企业 API Key（管理员） */
export function regenerateApiKey(id) {
  return request.post(`/api-keys/${id}/regenerate`)
}

/** 获取 API Key 长期记忆策略（管理员） */
export function getApiKeyMemoryConfig(id) {
  return request.get(`/api-keys/${id}/memory-config`)
}

/** 更新 API Key 长期记忆策略（管理员） */
export function updateApiKeyMemoryConfig(id, data) {
  return request.put(`/api-keys/${id}/memory-config`, data)
}

/** 查询企业 API Key 下外部用户记忆汇总（管理员） */
export function listApiKeyMemoryUsers(id) {
  return request.get(`/api-keys/${id}/memory-users`)
}

/** 查询企业 API Key 下外部用户记忆明细（管理员） */
export function listApiKeyMemories(id, params = {}) {
  return request.get(`/api-keys/${id}/memories`, { params })
}

/** 清空某外部用户在该 Key 下的全部记忆（管理员） */
export function clearApiKeyUserMemories(id, externalUserId) {
  return request.delete(`/api-keys/${id}/memories`, { params: { externalUserId } })
}

/** 删除单条外部用户记忆（管理员） */
export function deleteApiKeyMemory(id, memoryId) {
  return request.delete(`/api-keys/${id}/memories/${memoryId}`)
}
