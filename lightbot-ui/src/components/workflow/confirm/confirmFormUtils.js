/** 人工确认表单字段工具（节点配置 / 测试运行 / 对话共用） */

export const CONFIRM_FIELD_TYPES = [
  { value: 'info', label: '展示信息' },
  { value: 'text', label: '文本' },
  { value: 'textarea', label: '多行文本' },
  { value: 'number', label: '数字' },
  { value: 'radio', label: '单选' },
  { value: 'select', label: '下拉' },
]

export function normalizeConfirmOptions(options) {
  if (!Array.isArray(options)) return []
  return options
    .map(opt => {
      if (typeof opt === 'string') return { label: opt, value: opt }
      return { label: opt.label ?? opt.value, value: opt.value ?? opt.label }
    })
    .filter(opt => opt.value != null && String(opt.value).trim() !== '')
}

/** 确保 field.options 为数组（单选/下拉编辑用） */
export function ensureConfirmOptionsArray(field) {
  if (!field) return []
  if (!Array.isArray(field.options)) {
    field.options = []
  }
  return field.options
}

export function getConfirmOptionText(option) {
  if (typeof option === 'string') return option
  if (option && typeof option === 'object') {
    return option.label ?? option.value ?? ''
  }
  return ''
}

export function setConfirmOptionAt(field, index, text) {
  const list = ensureConfirmOptionsArray(field)
  list[index] = text ?? ''
}

export function addConfirmOption(field) {
  ensureConfirmOptionsArray(field).push('')
}

export function removeConfirmOptionAt(field, index) {
  ensureConfirmOptionsArray(field).splice(index, 1)
}

/** 保存前过滤空选项 */
export function sanitizeConfirmOptions(field) {
  if (!field || !Array.isArray(field.options)) return
  field.options = field.options
    .map(opt => getConfirmOptionText(opt).trim())
    .filter(Boolean)
}

/** info 类型展示文案：优先 label，其次 defaultValue */
export function getConfirmInfoText(field) {
  if (!field) return ''
  return field.label || field.defaultValue || field.key || ''
}

export function buildConfirmSubmittedEntries(formFields, submittedData) {
  const fields = Array.isArray(formFields) ? formFields : []
  const data = submittedData || {}
  return fields
    .filter(f => f?.type !== 'info' && f?.key)
    .map(f => ({
      key: f.key,
      label: f.label || f.key,
      value: data[f.key] != null ? String(data[f.key]) : '—',
    }))
}

/** 是否为 ask_user 工具触发的 HITL 挂起（与 confirm 节点共用 confirmForm 协议） */
export function isAskUserHitlForm(confirmForm) {
  return confirmForm?.hitlType === 'ask_user' || confirmForm?.toolName === 'ask_user'
}

/** 业务办理页 HITL */
export function isBusinessPageHitlForm(confirmForm) {
  return confirmForm?.hitlType === 'business_page' || confirmForm?.toolName === 'present_business_page'
}

export function getHitlPendingTitle(confirmForm) {
  if (isBusinessPageHitlForm(confirmForm)) return '等待业务办理'
  return isAskUserHitlForm(confirmForm) ? '等待您的回答' : '等待人工确认'
}

export function getHitlResolvedTitle(confirmForm) {
  if (isBusinessPageHitlForm(confirmForm)) return '业务办理已提交'
  return isAskUserHitlForm(confirmForm) ? '已提交回答' : '人工确认已提交'
}

export function getHitlSubmitLabel(confirmForm) {
  if (isBusinessPageHitlForm(confirmForm)) return '提交办理结果'
  return isAskUserHitlForm(confirmForm) ? '提交回答' : '确认并继续'
}

export function getHitlAbandonLabel(confirmForm) {
  if (isBusinessPageHitlForm(confirmForm)) return '放弃办理'
  return isAskUserHitlForm(confirmForm) ? '放弃回答' : '放弃本次确认'
}

export const BUSINESS_RESULT_KEY = 'businessResult'

export const ASK_USER_ANSWER_KEY = 'answer'
export const ASK_USER_SELECTED_OPTION_KEY = 'selectedOption'

/** ask_user 有选项时：单选 + 自定义文本至少填一项 */
export function validateAskUserFormFields(formFields, formValues) {
  const fields = Array.isArray(formFields) ? formFields : []
  const hasChoice = fields.some(
    f => f?.key === ASK_USER_SELECTED_OPTION_KEY && f?.type === 'radio'
  )
  if (!hasChoice) return null

  const custom = String(formValues?.[ASK_USER_ANSWER_KEY] ?? '').trim()
  const selected = formValues?.[ASK_USER_SELECTED_OPTION_KEY]
  const selectedStr = selected != null ? String(selected).trim() : ''
  if (custom || selectedStr) return null
  return '请选择一项或填写自定义回答'
}

/** 提交前合并为单一 answer（自定义文本优先） */
export function resolveAskUserSubmitPayload(formValues) {
  const custom = String(formValues?.[ASK_USER_ANSWER_KEY] ?? '').trim()
  const selected = formValues?.[ASK_USER_SELECTED_OPTION_KEY]
  const selectedStr = selected != null ? String(selected).trim() : ''
  return { [ASK_USER_ANSWER_KEY]: custom || selectedStr }
}

/** 只读回显：合并展示最终 answer */
export function resolveAskUserDisplayAnswer(submittedData) {
  if (!submittedData || typeof submittedData !== 'object') return '—'
  const custom = String(submittedData[ASK_USER_ANSWER_KEY] ?? '').trim()
  if (custom) return custom
  const selected = submittedData[ASK_USER_SELECTED_OPTION_KEY]
  if (selected != null && String(selected).trim()) return String(selected).trim()
  return '—'
}
