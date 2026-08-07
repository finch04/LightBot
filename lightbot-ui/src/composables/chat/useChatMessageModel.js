import { refreshChatAttachmentPreviews } from '../../api/chat'
import { parseBusinessPageSubmitDataFromContent } from '../../components/businessPages/businessPageResultUtils'
import { resolveWorkflowConfirmPending, resolveWorkflowFailureFromEvents, stripWorkflowErrorContent } from '../../components/workflow/workflowStepUtils.js'
import { safeJsonParse } from '../../utils/request'
import { contentHasMentionTokens, parseMentionsFromMetadata, resolveMentionsForDisplay } from '../../utils/mention_utils'
import { enrichVideoThumbnails } from '../../utils/videoThumbnail'
import { normalizeAssistantMessageErrors, applyMessageErrorFromDoneMeta } from '../../utils/chat/messageErrorState.js'
import { getToolBlockOffsets } from './useChatEventPartition.js'

export function parseAttachmentsFromMetadata(metadata) {
  if (!metadata) return []
  try {
    const meta = typeof metadata === 'string' ? safeJsonParse(metadata) : metadata
    return Array.isArray(meta?.attachments) ? meta.attachments : []
  } catch {
    return []
  }
}

/** 从 msg.metadata 或 msg._mentions 提取 mention 快照（用于历史 chip 回显） */
export function getMsgMentions(msg) {
  if (!msg) return []
  const content = msg.content ?? ''
  const raw = (Array.isArray(msg._mentions) && msg._mentions.length)
    ? msg._mentions
    : parseMentionsFromMetadata(msg.metadata)
  return resolveMentionsForDisplay(content, raw).map(m => ({
    type: m.type,
    resourceId: m.resourceId != null ? String(m.resourceId) : '',
    name: m.name || `${m.type}:${m.resourceId}` || m.token || '',
    token: m.token || `@${m.type}:${m.resourceId}`,
  }))
}

/** 用户消息是否应渲染 mention 高亮 */
export function shouldRenderMentions(msg, content) {
  if (!msg || msg.role !== 'user') return false
  const text = content ?? msg.content ?? ''
  return getMsgMentions(msg).length > 0 || contentHasMentionTokens(text)
}

export function getMsgAttachments(msg) {
  return msg._attachments || []
}

export async function enrichMessagesAttachments(msgs) {
  const needRefresh = []
  for (const msg of msgs) {
    if (msg.role !== 'user') continue
    const atts = msg._attachments || []
    for (const a of atts) {
      if (a?.objectKey) needRefresh.push(a)
    }
  }
  if (!needRefresh.length) return
  try {
    const res = await refreshChatAttachmentPreviews(needRefresh)
    const refreshedByKey = new Map((res.data || []).map(a => [a.objectKey, a]))
    for (const msg of msgs) {
      if (!msg._attachments?.length) continue
      msg._attachments = msg._attachments.map(a => {
        const refreshed = refreshedByKey.get(a.objectKey)
        return refreshed ? { ...a, ...refreshed } : a
      })
      await enrichVideoThumbnails(msg._attachments)
    }
  } catch {
    // 预览 URL 刷新失败时仍展示文件名
  }
}

export function parseMessage(m) {
  let toolEvents = []
  let workflowEvents = []
  let toolBlockOffsets = []
  let reasoningContent = ''
  let sensitiveBlock = null
  let attachments = []
  let requestId = null
  let incompleteTodos = []

  // 解析metadata（处理JSON字符串嵌套）
  let metadata = m.metadata
  if (metadata && typeof metadata === 'string') {
    try {
      // 使用原生JSON.parse解析（metadata中没有Long ID精度问题）
      metadata = JSON.parse(metadata)

      // 如果解析后仍是字符串（双重转义），再解析一次
      if (typeof metadata === 'string') {
        metadata = JSON.parse(metadata)
      }
    } catch (e) {
      console.error('[parseMessage] metadata解析失败:', e, metadata?.substring?.(0, 200))
      metadata = null
    }
  }

  // toolEvents 顶层字段（与 metadata 解耦存储）；旧消息回退 metadata.toolEvents
  let topToolEvents = m.toolEvents
  if (topToolEvents && typeof topToolEvents === 'string') {
    try {
      topToolEvents = JSON.parse(topToolEvents)
      if (typeof topToolEvents === 'string') topToolEvents = JSON.parse(topToolEvents)
    } catch (e) {
      console.error('[parseMessage] toolEvents 解析失败:', e)
      topToolEvents = null
    }
  }

  // 提取字段
  if (metadata) {
    if (metadata.toolEvents) toolEvents = metadata.toolEvents
    if (metadata.workflowEvents) workflowEvents = metadata.workflowEvents
    if (metadata.toolBlockOffsets) {
      toolBlockOffsets = metadata.toolBlockOffsets.map(o => Number(o))
    }
    if (metadata.reasoningContent) reasoningContent = metadata.reasoningContent.replace(/^\s+/, '')
    if (metadata.sensitiveBlock) sensitiveBlock = metadata.sensitiveBlock
    if (metadata.requestId) requestId = metadata.requestId
    if (Array.isArray(metadata.incompleteTodos) && metadata.incompleteTodos.length > 0) {
      incompleteTodos = metadata.incompleteTodos
    }
    attachments = parseAttachmentsFromMetadata(metadata)
  }

  // 顶层 toolEvents 优先于 metadata.toolEvents（新存储路径）
  if (Array.isArray(topToolEvents) && topToolEvents.length > 0) {
    toolEvents = topToolEvents
  }

  // 规范化 toolEvents 中的 contentOffset 为数字类型
  if (toolEvents.length > 0) {
    toolEvents = toolEvents.map(e => ({
      ...e,
      contentOffset: e.contentOffset != null ? Number(e.contentOffset) : e.contentOffset
    }))
  }

  const roleRaw = m.role?.code || m.role
  const role = roleRaw != null ? String(roleRaw).toLowerCase() : ''
  const workflowConfirmPending = resolveWorkflowConfirmPending(workflowEvents, metadata)
  const workflowFailure = resolveWorkflowFailureFromEvents(workflowEvents)
    || (metadata?.workflowError ? {
      nodeLabel: '工作流',
      message: metadata.workflowError.message || '工作流执行失败',
      reason: metadata.workflowError.failureReason,
    } : null)
  let content = m.content
  if (workflowFailure) {
    content = stripWorkflowErrorContent(content)
  }

  const msg = {
    role,
    content,
    metadata: metadata ?? m.metadata,
    _id: m.id,
    _parentId: m.parentId || null,
    _messageType: m.messageType?.code || m.messageType || 'text',
    _attachments: attachments,
    _mentions: Array.isArray(metadata?.mentions) ? metadata.mentions : [],
    _toolEvents: toolEvents,
    _workflowEvents: workflowEvents,
    _workflowConfirmPending: workflowConfirmPending,
    _workflowError: workflowFailure,
    _toolBlockOffsets: toolBlockOffsets,
    _toolBlocksDone: [],
    _toolExpanded: false,
    _toolsDone: true,
    _reasoningContent: reasoningContent,
    _reasoningExpanded: true,
    _reasoningDone: true,
    _sensitiveBlock: sensitiveBlock,
    _incompleteTodos: incompleteTodos,
    _requestId: requestId,
    _replyToMessageId: m.replyToMessageId || null,
    _replyToContent: null,
    _replyToRole: null,
    _starred: !!m.starred,
    _createTime: m.createTime || null,
    _persisted: !!m.id,
  }

  if (metadata?.error) {
    msg._error = {
      message: metadata.error.message || '未知错误',
      code: metadata.error.code || 'UNKNOWN',
    }
  }

  // 业务页回灌：历史消息按系统通知展示，不表现为用户再次提问
  if (
    role === 'user'
    && typeof content === 'string'
    && (
      content.startsWith('系统通知：用户')
      || content.startsWith('用户已完成业务办理页')
      || content.startsWith('用户取消了业务办理页')
    )
  ) {
    msg._businessPageCallback = true
    msg._businessPageCallbackStatus = content.includes('取消') ? 'cancelled' : 'submitted'
    const typeMatch = content.match(/业务办理页(?:完成提交)?（([^）]+)）/)
      || content.match(/业务办理页提交（([^）]+)）/)
    if (typeMatch?.[1]) {
      msg._businessPageCallbackPageType = typeMatch[1]
    }
    // 从回灌正文恢复办理字段与页面标签（标签来自提交时 DOM 采集，非平台硬编码）
    const parsed = parseBusinessPageSubmitDataFromContent(content)
    msg._businessPageCallbackValues = parsed.values
    msg._businessPageCallbackFieldLabels = parsed.fieldLabels
  }

  normalizeAssistantMessageErrors(msg)
  msg._toolBlockOffsets = getToolBlockOffsets(msg)
  return msg
}

/** 后端结构化错误（流式 error 事件或 metadata.error） */
export function isBackendErrorMessage(msg) {
  return msg?.role === 'assistant' && !!msg._error
}

/** 将 [DONE] 携带的消息 ID、metadata 等合并到流式消息对象 */
export function applyStreamDoneMetadata(assistantMsg, meta, sessionTokenCount) {
  if (!assistantMsg || !meta) return
  if (meta.assistantMessageId) {
    assistantMsg._id = meta.assistantMessageId
    assistantMsg._persisted = true
  }
  if (meta.totalTokens) {
    sessionTokenCount.value += meta.totalTokens
  }
  const { assistantMessageId, userMessageId, totalTokens, ...restMeta } = meta
  if (Object.keys(restMeta).length > 0) {
    assistantMsg.metadata = { ...(assistantMsg.metadata || {}), ...restMeta }
  }
  if (restMeta.reasoningContent && !assistantMsg._reasoningContent) {
    assistantMsg._reasoningContent = String(restMeta.reasoningContent).replace(/^\s+/, '')
  }
  // 未完成待办告警：buildDoneEvent 检测到 pending/in_progress todos 时下发
  if (Array.isArray(restMeta.incompleteTodos) && restMeta.incompleteTodos.length > 0) {
    assistantMsg._incompleteTodos = restMeta.incompleteTodos
  }
  applyMessageErrorFromDoneMeta(assistantMsg, meta)
}

/**
 * 从消息metadata中解析RAG引用
 */
export function getMsgRagRefs(msg) {
  if (!msg.metadata) return []
  try {
    const metadata = typeof msg.metadata === 'string' ? safeJsonParse(msg.metadata) : msg.metadata
    return metadata?.ragReferences || []
  } catch {
    return []
  }
}

export function getRagQaQuestion(ref) {
  if (!ref || ref.sourceType !== 'qa_pair') return ''
  if (ref.question) return ref.question
  const content = ref.contentPreview || ''
  const match = content.match(/^问题：([^\n]*)/)
  return match?.[1] || ref.documentName || '问答对'
}

export function formatElapsed(ms) {
  if (ms < 1000) return `耗时 ${ms}ms`
  return `耗时 ${(ms / 1000).toFixed(1)}s`
}

export function isMessageEditing(msg, index, editingMessageId) {
  const eid = editingMessageId?.value ?? editingMessageId
  if (!eid || !msg) return false
  if (msg._id) return eid === msg._id
  return eid === `local-${index}`
}

export function getReplyToInfo(msg, messages) {
  if (!msg._replyToMessageId) return null
  const list = messages?.value ?? messages ?? []
  // 当前会话发送的消息直接携带引用内容
  if (msg._replyToContent) {
    return { content: msg._replyToContent, role: msg._replyToRole }
  }
  // 历史消息：从已加载的消息列表中查找被引用的消息
  const refMsg = list.find(m => m._id === msg._replyToMessageId)
  if (refMsg) {
    return { content: (refMsg.content || '').slice(0, 100), role: refMsg.role }
  }
  return null
}

/** 引用目标是否在已加载的消息列表中（控制是否可点击跳转） */
export function hasReplyTarget(msg, messages) {
  if (!msg?._replyToMessageId) return false
  const list = messages?.value ?? messages ?? []
  return list.some(m => m._id === msg._replyToMessageId)
}

/**
 * 绑定 messages / editingMessageId 等 Ref 后返回消息模型辅助函数
 * @param {object} options
 * @param {import('vue').Ref<string|null>} options.editingMessageId
 * @param {import('vue').Ref<Array>} options.messages
 * @param {import('vue').Ref<number>} options.sessionTokenCount
 */
export function useChatMessageModel({ editingMessageId, messages, sessionTokenCount }) {
  function isMessageEditingBound(msg, index) {
    return isMessageEditing(msg, index, editingMessageId)
  }

  function getReplyToInfoBound(msg) {
    return getReplyToInfo(msg, messages)
  }

  function hasReplyTargetBound(msg) {
    return hasReplyTarget(msg, messages)
  }

  function applyStreamDoneMetadataBound(assistantMsg, meta) {
    return applyStreamDoneMetadata(assistantMsg, meta, sessionTokenCount)
  }

  return {
    parseAttachmentsFromMetadata,
    getMsgMentions,
    shouldRenderMentions,
    getMsgAttachments,
    enrichMessagesAttachments,
    parseMessage,
    isBackendErrorMessage,
    applyStreamDoneMetadata: applyStreamDoneMetadataBound,
    getMsgRagRefs,
    getRagQaQuestion,
    getReplyToInfo: getReplyToInfoBound,
    hasReplyTarget: hasReplyTargetBound,
    isMessageEditing: isMessageEditingBound,
    formatElapsed,
  }
}
