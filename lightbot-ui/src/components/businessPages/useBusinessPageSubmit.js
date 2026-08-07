import { inject, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { parseBusinessPageSubmitDataFromContent } from './businessPageResultUtils'

/**
 * 业务页提交/取消：对话回灌 或 Workflow HITL
 */
export function useBusinessPageSubmit(props, emit) {
  const submitBusinessPageResult = inject('submitBusinessPageResult', null)
  const chatMessages = inject('chatMessages', null)
  const submitting = ref(false)
  const done = ref(false)
  /** 提交成功后保留的结果，供卡片回显 */
  const submittedResult = ref(null)

  /** 刷新历史后：若后续已有业务页回灌消息，恢复已提交/已取消态 */
  function restoreIfAlreadyHandled() {
    const msgs = chatMessages?.value
    if (!Array.isArray(msgs) || props.messageIndex == null || props.messageIndex < 0) return
    const pageType = props.payload?.pageType
    for (let i = props.messageIndex + 1; i < msgs.length; i++) {
      const m = msgs[i]
      if (m?.role !== 'user') continue
      const content = typeof m.content === 'string' ? m.content : ''
      const isCallback = m._businessPageCallback
        || content.startsWith('系统通知：用户')
        || content.startsWith('用户已完成业务办理页')
        || content.startsWith('用户取消了业务办理页')
      if (!isCallback) continue
      if (pageType && content && !content.includes(pageType)) continue
      const cancelled = m._businessPageCallbackStatus === 'cancelled' || content.includes('取消')
      const parsed = parseBusinessPageSubmitDataFromContent(content)
      const values = (m._businessPageCallbackValues && typeof m._businessPageCallbackValues === 'object')
        ? m._businessPageCallbackValues
        : parsed.values
      const fieldLabels = (m._businessPageCallbackFieldLabels && typeof m._businessPageCallbackFieldLabels === 'object')
        ? m._businessPageCallbackFieldLabels
        : parsed.fieldLabels
      done.value = true
      submittedResult.value = cancelled
        ? { action: 'cancel', pageType }
        : { action: 'submit', pageType, values: values || {}, fieldLabels: fieldLabels || {} }
      return
    }
  }

  onMounted(restoreIfAlreadyHandled)
  // 历史消息异步到位 / pageType 回填后再次尝试恢复
  watch(
    () => [props.messageIndex, props.payload?.pageType, chatMessages?.value?.length],
    () => {
      if (!done.value) restoreIfAlreadyHandled()
    },
  )

  async function submitResult(result) {
    submitting.value = true
    try {
      if (props.workflowMode) {
        emit('workflow-submit', result)
        done.value = true
        submittedResult.value = result
        return
      }
      if (typeof submitBusinessPageResult === 'function' && props.messageIndex >= 0) {
        await submitBusinessPageResult({
          messageIndex: props.messageIndex,
          result,
          status: 'submitted',
        })
        done.value = true
        submittedResult.value = result
        return
      }
      // 无回灌能力时本地演示
      await new Promise((r) => setTimeout(r, 400))
      done.value = true
      submittedResult.value = result
      message.success('演示提交成功')
    } finally {
      submitting.value = false
    }
  }

  async function cancelResult(result) {
    submitting.value = true
    try {
      const cancelled = { action: 'cancel', pageType: result?.pageType || props.payload?.pageType, ...(result || {}) }
      if (props.workflowMode) {
        emit('workflow-cancel', cancelled)
        done.value = true
        submittedResult.value = cancelled
        return
      }
      if (typeof submitBusinessPageResult === 'function' && props.messageIndex >= 0) {
        await submitBusinessPageResult({
          messageIndex: props.messageIndex,
          result: cancelled,
          status: 'cancelled',
        })
        done.value = true
        submittedResult.value = cancelled
        return
      }
      done.value = true
      submittedResult.value = cancelled
      message.info('已取消')
    } finally {
      submitting.value = false
    }
  }

  return { submitting, done, submittedResult, submitResult, cancelResult }
}
