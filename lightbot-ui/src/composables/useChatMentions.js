import { ref, watch } from 'vue'
import { getMentionOptions } from '../api/mention'

/**
 * Chat @ mention 输入管理：
 * - mentions：当前已插入的 mention 数组（{type, resourceId, name, token}）
 * - mentionOptions：从后端拉取的候选资源（按类型分组）
 * - pickerState：浮层状态（open, query, activeIndex）
 * - detectMentionQuery：从光标前最近 @ 之后提取 query
 * - insertMention / removeMention：维护 mentions 数组
 * - serializeForRequest：组装发给后端的 mentions 字段
 *
 * @param {import('vue').Ref<string|number|null>} agentIdRef 当前 Agent ID
 * @param {import('vue').Ref<string|number|null>} agentVersionIdRef 当前 Agent 版本快照 ID
 */
export function useChatMentions(agentIdRef, agentVersionIdRef) {
  const mentions = ref([])
  const mentionOptions = ref([])
  const mentionLoading = ref(false)

  const pickerState = ref({
    open: false,
    query: '',
    activeIndex: 0,
    caretRect: null,
    range: null,
  })

  async function loadMentionOptions() {
    const agentId = agentIdRef?.()
    if (!agentId) {
      mentionOptions.value = []
      return
    }
    mentionLoading.value = true
    try {
      const res = await getMentionOptions(agentId, agentVersionIdRef?.())
      mentionOptions.value = res?.data?.groups || []
    } catch (e) {
      mentionOptions.value = []
    } finally {
      mentionLoading.value = false
    }
  }

  // agentId / agentVersionId 变化时重新拉取候选
  watch(
    [agentIdRef, agentVersionIdRef],
    () => { loadMentionOptions() },
    { immediate: true },
  )

  /**
   * 从光标位置往前找最近一个 @，返回 @ 后的 query 字符串。
   * 触发浮层的条件：@ 在行首或前面是空白字符。
   *
   * @param {string} text 输入框全文
   * @param {number} caretIndex 光标位置
   * @returns {{query: string, atOffset: number} | null}
   */
  function detectMentionQuery(text, caretIndex) {
    if (!text) return null
    const before = text.slice(0, caretIndex)
    // 从光标往前找最近的 @
    const atIdx = before.lastIndexOf('@')
    if (atIdx < 0) return null
    // @ 必须在行首或前面是空白
    if (atIdx > 0 && !/\s/.test(before[atIdx - 1])) return null
    // @ 之后到光标之间不能有空白
    const query = before.slice(atIdx + 1)
    if (/\s/.test(query)) return null
    // 长度限制：query 最多 30 字符，避免误触发
    if (query.length > 30) return null
    return { query, atOffset: atIdx }
  }

  /**
   * 插入 mention：替换输入框中 [atOffset, caretIndex) 的 @query 文本为 chip，
   * 同时把 mention 加入 mentions 数组。
   *
   * @param {object} m 候选项 {type, resourceId, name, token}
   * @param {function} replaceText (start, end, replacement) => void 由输入组件提供
   * @param {function} focusEnd () => void 让输入组件聚焦并把光标移到末尾
   */
  function insertMention(m, replaceText, focusEnd) {
    const start = pickerState.value.range?.atOffset ?? 0
    const end = start + (pickerState.value.range?.query?.length || 0) + 1 // +1 for @
    replaceText(start, end, m.token + ' ')
    mentions.value.push({
      type: m.type,
      resourceId: String(m.resourceId),
      name: m.name,
      token: m.token,
    })
    pickerState.value.open = false
    pickerState.value.query = ''
    pickerState.value.activeIndex = 0
    pickerState.value.range = null
    if (focusEnd) focusEnd()
  }

  /**
   * 删除 mention：从 mentions 数组移除指定 token
   */
  function removeMention(token) {
    mentions.value = mentions.value.filter(m => m.token !== token)
  }

  /**
   * 清空所有 mention（发送或重置时调用）
   */
  function clearMentions() {
    mentions.value = []
    pickerState.value.open = false
    pickerState.value.query = ''
    pickerState.value.activeIndex = 0
    pickerState.value.range = null
  }

  /**
   * 组装发送给后端的 mentions 字段（只保留必要字段）
   */
  function serializeForRequest() {
    if (!mentions.value.length) return undefined
    return mentions.value.map(m => ({
      type: m.type,
      resourceId: String(m.resourceId),
      name: m.name,
      token: m.token,
    }))
  }

  return {
    mentions,
    mentionOptions,
    mentionLoading,
    picker: pickerState,
    loadMentionOptions,
    detectMentionQuery,
    insertMention,
    removeMention,
    clearMentions,
    serializeForRequest,
  }
}
