import request from '../utils/request'

export function checkHealth() {
  return request.get('/system-config/health')
}

export function getDefaultAiConfig() {
  return request.get('/system-config/default-ai')
}

export function updateDefaultAiConfig(data) {
  return request.put('/system-config/default-ai', data)
}

// 默认对话模型
export function getDefaultChatModel() {
  return request.get('/system-config/default-chat-model')
}

export function updateDefaultChatModel(data) {
  return request.put('/system-config/default-chat-model', data)
}

// 默认向量模型
export function getDefaultEmbeddingModel() {
  return request.get('/system-config/default-embedding-model')
}

export function updateDefaultEmbeddingModel(data) {
  return request.put('/system-config/default-embedding-model', data)
}

// 默认TTS模型
export function getDefaultTtsModel() {
  return request.get('/system-config/default-tts-model')
}

export function updateDefaultTtsModel(data) {
  return request.put('/system-config/default-tts-model', data)
}

// 默认重排模型
export function getDefaultRerankModel() {
  return request.get('/system-config/default-rerank-model')
}

export function updateDefaultRerankModel(data) {
  return request.put('/system-config/default-rerank-model', data)
}