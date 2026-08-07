/**
 * 数据池字段前端校验（与后端 DataPoolFieldValidator 对齐）
 */

function isEmpty(value) {
  if (value == null) return true
  if (typeof value === 'string') return value.trim() === ''
  if (Array.isArray(value)) return value.length === 0
  return false
}

function optionValues(field) {
  return new Set((field.props?.options || []).map((o) => String(o.value)))
}

function toArray(value) {
  if (value == null || value === '') return []
  if (Array.isArray(value)) return value.map(String)
  if (typeof value === 'string') {
    const t = value.trim()
    if (t.startsWith('[')) {
      try {
        const arr = JSON.parse(t)
        return Array.isArray(arr) ? arr.map(String) : [t]
      } catch {
        return [t]
      }
    }
    if (t.includes(',')) return t.split(',').map((s) => s.trim()).filter(Boolean)
    return [t]
  }
  return [String(value)]
}

/**
 * @param {object} field
 * @param {*} value
 * @returns {string|null} 错误信息，通过返回 null
 */
export function validatePoolField(field, value) {
  const label = field.label || field.key
  if (isEmpty(value)) {
    if (field.required) return `请填写${label}`
    return null
  }
  const type = field.type || 'input'
  if (type === 'number') {
    if (typeof value !== 'number' && Number.isNaN(Number(value))) {
      return `${label} 必须是数字`
    }
    return null
  }
  if (type === 'date') {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(String(value).slice(0, 10))) {
      return `${label} 格式应为 yyyy-MM-dd`
    }
    return null
  }
  if (type === 'datetime') {
    const s = String(value).trim().replace('T', ' ')
    if (!/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(s.slice(0, 19))) {
      return `${label} 格式应为 yyyy-MM-dd HH:mm:ss`
    }
    return null
  }
  if (type === 'select' || type === 'radio') {
    const allowed = optionValues(field)
    if (!allowed.size) return `${label} 未配置可选值`
    if (!allowed.has(String(value))) return `${label} 的值不在可选范围内`
    return null
  }
  if (type === 'checkbox') {
    const allowed = optionValues(field)
    if (!allowed.size) return `${label} 未配置可选值`
    for (const v of toArray(value)) {
      if (!allowed.has(v)) return `${label} 含有非法选项: ${v}`
    }
    return null
  }
  if (type === 'upload') {
    const list = Array.isArray(value) ? value : []
    for (const item of list) {
      if (!item?.url || !item?.name) return `${label} 附件信息不完整`
    }
  }
  return null
}

/**
 * @param {object[]} fields
 * @param {Record<string, any>} values
 * @returns {string|null}
 */
export function validatePoolRecord(fields, values) {
  for (const field of fields || []) {
    const err = validatePoolField(field, values?.[field.key])
    if (err) return err
  }
  return null
}
