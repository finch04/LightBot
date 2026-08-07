import request from '../utils/request'

/** 获取企业长期记忆默认策略（管理员） */
export function getEnterpriseLongMemoryPolicy() {
  return request.get('/system-config/long-memory')
}

/** 更新企业长期记忆默认策略（管理员） */
export function updateEnterpriseLongMemoryPolicy(data) {
  return request.put('/system-config/long-memory', data)
}

/** 获取 API Key 长期记忆策略（管理员） */
export function getApiKeyMemoryConfig(id) {
  return request.get(`/api-keys/${id}/memory-config`)
}

/** 更新 API Key 长期记忆策略（管理员） */
export function updateApiKeyMemoryConfig(id, data) {
  return request.put(`/api-keys/${id}/memory-config`, data)
}
