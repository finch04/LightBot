import request from '../utils/request'

/** ---------- 问数数据集 ---------- */
export function listAskDatasets(params) {
  return request.get('/ask-data/datasets', { params })
}

/** 供 Agent 绑定 useBinding：适配 records 结构 */
export async function listAskDatasetsForBinding() {
  const res = await listAskDatasets()
  return { data: { records: res.data || [] } }
}

export function getAskDataset(id) {
  return request.get(`/ask-data/datasets/${id}`)
}

export function createAskDataset(data) {
  return request.post('/ask-data/datasets', data)
}

export function updateAskDataset(id, data) {
  return request.put(`/ask-data/datasets/${id}`, data)
}

/** 轻量问数增强：业务说明 / 默认时间 / 敏感字段 / 租户维度等 */
export function updateAskDatasetEnhancement(id, data) {
  return request.put(`/ask-data/datasets/${id}/enhancement`, data)
}

export function deleteAskDataset(id) {
  return request.delete(`/ask-data/datasets/${id}`)
}

export function refreshAskDatasetProfile(id) {
  return request.post(`/ask-data/datasets/${id}/refresh-profile`)
}

/** 模型即可问：确保并自动同步 */
export function ensureAskDatasetFromModel(dataModelId) {
  return request.post(`/ask-data/datasets/ensure-from-model/${dataModelId}`)
}

/** 从模型同步维度/默认指标 */
export function syncAskDatasetFromModel(id) {
  return request.post(`/ask-data/datasets/${id}/sync-from-model`)
}

export function getAskDatasetByModel(dataModelId) {
  return request.get(`/ask-data/datasets/by-model/${dataModelId}`)
}

export function listAskRelations() {
  return request.get('/ask-data/relations')
}

export function createAskRelation(data) {
  return request.post('/ask-data/relations', data)
}

export function deleteAskRelation(id) {
  return request.delete(`/ask-data/relations/${id}`)
}

export function executeAskDataQuery(ir) {
  return request.post('/ask-data/query', ir)
}

/** 问数增强预览：默认过滤 / 业务指标试跑（不落库） */
export function previewAskDatasetEnhancement(id, data) {
  return request.post(`/ask-data/datasets/${id}/preview`, data)
}
