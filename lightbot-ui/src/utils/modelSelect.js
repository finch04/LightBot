/** ModelSelect 复合值分隔符（避免与 Ollama modelId 中的 : 冲突） */
export const MODEL_SELECT_SEP = '|'

/**
 * 构造 ModelSelect v-model 复合值
 * @param {string|number|null|undefined} providerId
 * @param {string|null|undefined} modelId
 * @returns {string|null}
 */
export function formatModelSelectValue(providerId, modelId) {
  if (providerId == null || providerId === '' || !modelId) return null
  return `${String(providerId)}${MODEL_SELECT_SEP}${String(modelId)}`
}

/**
 * 解析 ModelSelect 复合值（兼容历史 `:` 分隔）
 * @param {string|null|undefined} value
 * @returns {{ providerId: string|null, modelId: string|null }}
 */
export function parseModelSelectValue(value) {
  if (!value) return { providerId: null, modelId: null }
  const sepIdx = value.includes(MODEL_SELECT_SEP)
    ? value.indexOf(MODEL_SELECT_SEP)
    : value.indexOf(':')
  if (sepIdx <= 0) return { providerId: null, modelId: null }
  const providerId = value.slice(0, sepIdx) || null
  const modelId = value.slice(sepIdx + 1) || null
  return { providerId, modelId }
}

/**
 * 归一化为标准复合值（`|` 分隔），供下拉回显
 * @param {string|null|undefined} value
 * @returns {string|null|undefined}
 */
export function normalizeModelSelectValue(value) {
  if (!value) return value ?? null
  const { providerId, modelId } = parseModelSelectValue(value)
  return formatModelSelectValue(providerId, modelId)
}

/**
 * 根据 modelId 在提供商列表中反查复合值（知识库 embedding 等仅存 modelId 的场景）
 * @param {Array} providers getProvidersWithModels 返回的列表
 * @param {string} modelId
 * @returns {string|null}
 */
export function findModelSelectValueByModelId(providers, modelId) {
  if (!modelId || !providers?.length) return null
  for (const p of providers) {
    for (const m of p.models || []) {
      if (m.modelId === modelId) {
        return formatModelSelectValue(p.id, m.modelId)
      }
    }
  }
  return null
}

/**
 * 构造 modelConfig JSON 字符串（Prompt 模板等）
 * @param {string|number|null} providerId
 * @param {string|null} modelId
 * @param {Record<string, unknown>} [extra]
 * @returns {string}
 */
export function buildModelConfigJson(providerId, modelId, extra = {}) {
  const cfg = { ...extra }
  if (providerId != null && providerId !== '') cfg.providerId = String(providerId)
  if (modelId) cfg.modelId = modelId
  return JSON.stringify(cfg)
}

/**
 * 从 modelConfig JSON 解析 providerId / modelId
 * @param {string|object|null} modelConfig
 * @returns {{ providerId: string|null, modelId: string|null, raw: object }}
 */
export function parseModelConfigJson(modelConfig) {
  let raw = {}
  if (!modelConfig) return { providerId: null, modelId: null, raw }
  try {
    raw = typeof modelConfig === 'string' ? JSON.parse(modelConfig) : { ...modelConfig }
  } catch {
    return { providerId: null, modelId: null, raw: {} }
  }
  const providerId = raw.providerId != null ? String(raw.providerId) : null
  const modelId = raw.modelId != null ? String(raw.modelId) : null
  return { providerId, modelId, raw }
}
