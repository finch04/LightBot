import request from '../utils/request'

/** ---------- 分类 ---------- */
export function listDataModelCategories() {
  return request.get('/data-model-categories')
}

export function createDataModelCategory(data) {
  return request.post('/data-model-categories', data)
}

export function updateDataModelCategory(id, data) {
  return request.put(`/data-model-categories/${id}`, data)
}

export function deleteDataModelCategory(id) {
  return request.delete(`/data-model-categories/${id}`)
}

/** ---------- 数据模型 ---------- */
export function listDataModels(params) {
  return request.get('/data-models', { params })
}

export function getDataModel(id) {
  return request.get(`/data-models/${id}`)
}

export function createDataModel(data) {
  return request.post('/data-models', data)
}

export function updateDataModel(id, data) {
  return request.put(`/data-models/${id}`, data)
}

export function updateDataModelSchema(id, schema) {
  return request.put(`/data-models/${id}/schema`, { schema })
}

/** AI 补全字段英文名（仅传英文名为空的中文显示名） */
export function suggestDataModelFieldKeys(data) {
  return request.post('/data-models/suggest-field-keys', data)
}

export function deleteDataModel(id) {
  return request.delete(`/data-models/${id}`)
}

/** ---------- 数据池 ---------- */
export function pageDataPoolRecords(modelId, params) {
  return request.get(`/data-pools/${modelId}/records`, { params })
}

export function createDataPoolRecord(modelId, data) {
  return request.post(`/data-pools/${modelId}/records`, { data })
}

export function updateDataPoolRecord(modelId, recordId, data) {
  return request.put(`/data-pools/${modelId}/records/${recordId}`, { data })
}

export function deleteDataPoolRecord(modelId, recordId) {
  return request.delete(`/data-pools/${modelId}/records/${recordId}`)
}

export function batchDeleteDataPoolRecords(modelId, ids) {
  return request.post(`/data-pools/${modelId}/records/batch-delete`, { ids })
}

export function importDataPoolRecords(modelId, file, mode = 'append') {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/data-pools/${modelId}/import`, formData, {
    params: { mode },
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function exportDataPoolRecords(modelId, params) {
  return request.get(`/data-pools/${modelId}/export`, {
    params,
    responseType: 'blob',
  })
}

export function uploadDataPoolAttachment(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/data-pools/attachments', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 问数语义 / 轻量增强 API 见 ./askData.js */

/** 供 Agent「可问数据」绑定：以数据模型分类为维度 */
export async function listDataModelCategoriesForBinding() {
  const [catsRes, modelsRes] = await Promise.all([
    listDataModelCategories(),
    listDataModels().catch(() => ({ data: [] })),
  ])
  const modelsByCat = {}
  for (const m of modelsRes?.data || []) {
    if (m?.categoryId == null) continue
    const cid = String(m.categoryId)
    if (!modelsByCat[cid]) modelsByCat[cid] = []
    modelsByCat[cid].push(m.name || m.tableName || '未命名模型')
  }
  const records = (catsRes?.data || []).map((c) => {
    const id = String(c.id)
    const modelNames = modelsByCat[id] || []
    const modelNamesText = modelNames.join('、')
    return {
      ...c,
      id,
      modelCount: modelNames.length,
      modelNames,
      modelNamesText,
      // 供搜索：分类名 + 模型名
      description: modelNamesText || '暂无数据模型',
    }
  })
  return { data: { records } }
}
