import request from '../utils/request'

export function getDefaultAiConfig() {
  return request.get('/system-config/default-ai')
}

export function updateDefaultAiConfig(data) {
  return request.put('/system-config/default-ai', data)
}