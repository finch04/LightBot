/**
 * 工具结果解析 composable
 * <p>统一处理 JSON 解析、错误检测、纯文本降级</p>
 */
import { computed } from 'vue'

/**
 * @param {import('vue').Ref<string>} rawResult - 原始 result 字符串
 * @returns {{ parsed, isPlainText, isError, errorMessage, displayText }}
 */
export function useToolResult(rawResult) {
  const parsed = computed(() => {
    try {
      const p = JSON.parse(rawResult.value)
      return typeof p === 'object' && p !== null ? p : null
    } catch {
      return null
    }
  })

  const isPlainText = computed(() => !parsed.value)

  const isError = computed(() => {
    return parsed.value?._error === true
  })

  const errorMessage = computed(() => {
    if (!isError.value) return ''
    return parsed.value?.message || '未知错误'
  })

  return { parsed, isPlainText, isError, errorMessage }
}
