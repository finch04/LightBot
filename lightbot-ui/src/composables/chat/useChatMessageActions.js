import { ref, reactive, watch, nextTick } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { deleteMessage as deleteMessageApi, toggleMessageStar } from '../../api/chatSession'
import { copyToClipboard } from '../../utils/clipboard'
import { getMsgMentions } from './useChatMessageModel.js'
import { resolveMessageFatalErrorState, resolveDeleteAssistantMessageId } from '../../utils/chat/messageErrorState.js'

/**
 * 消息操作：编辑/回复/删除/收藏/复制/原文弹窗/滚动定位
 * @param {object} deps
 */
export function useChatMessageActions(deps) {
  const {
    sessionId,
    messages,
    loading,
    streaming,
    virtualizer,
    isNearBottom,
    userScrolledUp,
    scrollToBottom,
    runChatStream,
    getReplyToInfo: getReplyToInfoBound,
    hasReplyTarget: hasReplyTargetBound,
    isMessageEditing: isMessageEditingBound,
    inputRef,
    resolveEditInputRefFn,
  } = deps

  const editingMessageId = deps.editingMessageId ?? ref(null)
  const editContent = deps.editContent ?? ref('')
  const editInputRef = deps.editInputRef ?? ref(null)
  const replyTo = deps.replyTo ?? reactive({ active: false, messageId: null, content: '', role: '' })
  const highlightMessageId = ref(null)
  /** 原始内容弹窗状态 */
  const rawModal = reactive({ visible: false, content: '', title: '', metadata: null, copied: false })
  /** Metadata 弹窗状态 */
  const metadataModal = reactive({ visible: false, json: '', copied: false })

  function resolveEditInputRef() {
    if (typeof resolveEditInputRefFn === 'function') {
      return resolveEditInputRefFn()
    }
    const raw = editInputRef.value
    if (Array.isArray(raw)) return raw.find(Boolean) || null
    return raw
  }

  function canRegenerate(index) {
    if (loading.value || streaming.value) return false
    const msg = messages.value[index]
    if (!msg || msg.role !== 'assistant' || msg._streaming || msg._sensitiveBlock) return false
    return index === messages.value.length - 1
  }

  function isLastUserMessage(index) {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      const m = messages.value[i]
      // 业务页回灌不算用户主动提问
      if (m.role === 'user' && !m._businessPageCallback) return i === index
    }
    return false
  }

  function startEdit(index) {
    const msg = messages.value[index]
    if (!msg) return
    if (loading.value) {
      console.warn('[startEdit] 无法编辑：对话正在加载中')
      return
    }
    editingMessageId.value = msg._id || `local-${index}`
    editContent.value = msg.content || ''
    nextTick(() => {
      const comp = resolveEditInputRef()
      comp?.setFromMessage?.(msg.content || '', getMsgMentions(msg))
      comp?.focus?.()
    })
  }

  function cancelEdit() {
    editingMessageId.value = null
    editContent.value = ''
  }

  /** 点击编辑区外（含 @ 候选浮层除外）时退出编辑 */
  function onEditClickOutside(e) {
    if (!editingMessageId.value) return
    const target = e.target
    if (!(target instanceof Element)) return
    if (target.closest('.edit-message-outer')) return
    if (target.closest('.mention-picker')) return
    cancelEdit()
  }

  watch(editingMessageId, (id) => {
    if (id) {
      document.addEventListener('mousedown', onEditClickOutside, true)
    } else {
      document.removeEventListener('mousedown', onEditClickOutside, true)
    }
  })

  function startReply(index) {
    const msg = messages.value[index]
    if (!msg || loading.value) return
    replyTo.active = true
    replyTo.messageId = msg._id
    replyTo.content = (msg.content || '').slice(0, 100)
    replyTo.role = msg.role
    nextTick(() => inputRef.value?.focus())
  }

  function cancelReply() {
    replyTo.active = false
    replyTo.messageId = null
    replyTo.content = ''
    replyTo.role = ''
  }

  async function toggleStarMessage(index) {
    const msg = messages.value[index]
    if (!msg?._id) {
      message.warning('消息正在保存，请稍后再试')
      return
    }
    try {
      await toggleMessageStar(msg._id)
      msg._starred = !msg._starred
      message.success(msg._starred ? '收藏成功' : '已取消收藏')
    } catch {
      message.error('操作失败')
    }
  }

  function getReplyToInfo(msg) {
    if (getReplyToInfoBound) return getReplyToInfoBound(msg)
    if (!msg._replyToMessageId) return null
    if (msg._replyToContent) {
      return { content: msg._replyToContent, role: msg._replyToRole }
    }
    const refMsg = messages.value.find(m => m._id === msg._replyToMessageId)
    if (refMsg) {
      return { content: (refMsg.content || '').slice(0, 100), role: refMsg.role }
    }
    return null
  }

  function hasReplyTarget(msg) {
    if (hasReplyTargetBound) return hasReplyTargetBound(msg)
    if (!msg?._replyToMessageId) return false
    return messages.value.some(m => m._id === msg._replyToMessageId)
  }

  function isMessageEditing(msg, index) {
    if (isMessageEditingBound) return isMessageEditingBound(msg, index)
    if (!editingMessageId.value || !msg) return false
    if (msg._id) return editingMessageId.value === msg._id
    return editingMessageId.value === `local-${index}`
  }

  /** 滚动到被引用的消息并高亮闪烁 */
  function scrollToMessage(messageId) {
    if (!messageId) return
    const idx = messages.value.findIndex(m => m._id === messageId)
    if (idx < 0) return
    virtualizer.value.scrollToIndex(idx, { align: 'center' })
    // 延迟高亮，等虚拟滚动渲染完成
    setTimeout(() => {
      highlightMessageId.value = messageId
      setTimeout(() => { highlightMessageId.value = null }, 2000)
    }, 100)
  }

  async function submitEdit() {
    const editComp = resolveEditInputRef()
    const newText = (editComp?.getText?.() || editContent.value || '').trim()
    if (!newText || loading.value) return

    // 尝试通过 ID 查找消息，如果找不到则尝试通过 local- 前缀解析索引
    let editIdx = messages.value.findIndex(m => m._id === editingMessageId.value)
    if (editIdx < 0 && editingMessageId.value?.startsWith('local-')) {
      const idxFromLocal = parseInt(editingMessageId.value.replace('local-', ''))
      if (!isNaN(idxFromLocal) && idxFromLocal < messages.value.length) {
        const msgAtIdx = messages.value[idxFromLocal]
        if (msgAtIdx?.role === 'user') {
          editIdx = idxFromLocal
        }
      }
    }
    if (editIdx < 0) {
      console.error('[submitEdit] 找不到要编辑的消息:', editingMessageId.value)
      editingMessageId.value = null
      editContent.value = ''
      return
    }

    const msg = messages.value[editIdx]
    const sentMentions = editComp?.getMentions?.()
    msg.content = newText
    if (sentMentions?.length) {
      msg._mentions = sentMentions
    }
    editingMessageId.value = null
    editContent.value = ''

    const lastIdx = messages.value.length - 1
    let deleteAssistantMessageId
    if (lastIdx > editIdx && messages.value[lastIdx].role === 'assistant') {
      deleteAssistantMessageId = resolveDeleteAssistantMessageId(messages.value[lastIdx])
      messages.value.pop()
    }

    isNearBottom.value = true
    userScrolledUp.value = false
    scrollToBottom()

    await runChatStream({
      message: newText,
      attachments: msg._attachments || [],
      mentions: sentMentions?.length ? sentMentions : undefined,
      regenerate: true,
      editMessageId: msg._id || null,
      deleteAssistantMessageId,
    })
  }

  async function copyMessage(msg) {
    const fatal = resolveMessageFatalErrorState(msg)
    const text = (msg.content && msg.content !== '[附件]')
      ? msg.content
      : (fatal?.message || msg.content || '')
    await copyToClipboard(text)
    msg._copied = true
    setTimeout(() => { msg._copied = false }, 2000)
  }

  async function copyRequestId(msg) {
    if (!msg._requestId) return
    await copyToClipboard(msg._requestId)
    msg._requestIdCopied = true
    message.success('Request ID 已复制')
    setTimeout(() => { msg._requestIdCopied = false }, 2000)
  }

  async function copyMessageId(msg) {
    if (!msg._id) return
    await copyToClipboard(String(msg._id))
    msg._msgIdCopied = true
    message.success('Message ID 已复制')
    setTimeout(() => { msg._msgIdCopied = false }, 2000)
  }

  function openRawModal(index) {
    const msg = messages.value[index]
    if (!msg) return
    rawModal.content = msg.content || ''
    rawModal.title = msg.role === 'assistant' ? '原文' : '消息原文'
    rawModal.metadata = msg.metadata || null
    rawModal.visible = true
  }

  function openMetadataModal() {
    if (!rawModal.metadata) return
    const raw = rawModal.metadata
    try {
      const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
      metadataModal.json = JSON.stringify(obj, null, 2)
    } catch {
      metadataModal.json = typeof raw === 'string' ? raw : JSON.stringify(raw)
    }
    metadataModal.copied = false
    metadataModal.visible = true
  }

  async function copyRawContent() {
    if (!rawModal.content) return
    await copyToClipboard(rawModal.content)
    rawModal.copied = true
    message.success('已复制')
    setTimeout(() => { rawModal.copied = false }, 2000)
  }

  async function copyMetadata() {
    if (!metadataModal.json) return
    await copyToClipboard(metadataModal.json)
    metadataModal.copied = true
    message.success('Metadata 已复制')
    setTimeout(() => { metadataModal.copied = false }, 2000)
  }

  function handleDeleteMessage(index) {
    const msg = messages.value[index]
    if (!msg) return
    const label = msg.role === 'assistant' ? 'AI 回复' : '用户消息'
    Modal.confirm({
      title: `删除${label}`,
      content: `确定要删除这条${label}吗？删除后不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      async onOk() {
        // 有 _id 说明是已持久化的消息，需要调后端删除
        if (msg._id && sessionId.value) {
          try {
            await deleteMessageApi(sessionId.value, msg._id)
          } catch {
            // 业务错误已由拦截器提示
            return
          }
        }
        messages.value.splice(index, 1)
        message.success('已删除')
      },
    })
  }

  function cleanupEditClickOutside() {
    document.removeEventListener('mousedown', onEditClickOutside, true)
  }

  return {
    editingMessageId,
    editContent,
    editInputRef,
    replyTo,
    highlightMessageId,
    rawModal,
    metadataModal,
    resolveEditInputRef,
    canRegenerate,
    isLastUserMessage,
    startEdit,
    cancelEdit,
    onEditClickOutside,
    startReply,
    cancelReply,
    toggleStarMessage,
    getReplyToInfo,
    hasReplyTarget,
    isMessageEditing,
    scrollToMessage,
    submitEdit,
    copyMessage,
    copyRequestId,
    copyMessageId,
    openRawModal,
    openMetadataModal,
    copyRawContent,
    copyMetadata,
    handleDeleteMessage,
    cleanupEditClickOutside,
  }
}
