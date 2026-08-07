import { ref, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { chatStream, stopChatStream } from '../../api/chat'
import { createSession, getSessionMessages } from '../../api/chatSession'
import { validatePendingAttachmentMix } from '../../utils/chatAttachment'
import { enrichVideoThumbnails } from '../../utils/videoThumbnail'
import { safeJsonParse } from '../../utils/request'
import { getToolBlockOffsets, markToolBlockDone } from './useChatEventPartition.js'
import { registerToolBlockOffset } from './useChatCapabilityStream.js'
import { normalizeAssistantMessageErrors, resolveDeleteAssistantMessageId } from '../../utils/chat/messageErrorState.js'

export function clearErrorRetry(msg) {
  if (msg?._errorRetry) {
    msg._errorRetry = null
  }
}

/**
 * SSE 流式对话总线：发送、重生成、中止、Ask User 回复
 * @param {object} deps
 */
export function useChatStream(deps) {
  const {
    sessionId,
    messages,
    messagesRef,
    loading,
    streaming,
    hasStreamContent,
    currentStatus,
    reconnecting,
    lastReplyElapsed,
    abortController,
    userStoppedStream,
    toolEvents,
    isNearBottom,
    userScrolledUp,
    skipNextWatch,
    input,
    inputHistory,
    historyIndex,
    pendingAttachments,
    inputRef,
    replyTo,
    canSend,
    workflowConfirmBlocked,
    selectedAgentId,
    selectedConfigVersion,
    selectedAgentVersionId,
    simCallerContext,
    sessionTokenCount,
    streamSmoother,
    getCurrentStreamingMsg,
    setCurrentStreamingMsg,
    scrollToBottom,
    scrollReasoningToBottom,
    applyStreamDoneMetadata,
    loadBatchFeedbacks,
    pollSessionTitle,
    isAskUserUnanswered,
    showAskUserModal,
    askUserModal,
    recordAskUserAnswers,
    cancelReply,
    autoResize,
    canRegenerate,
    getMsgMentions,
    handleChatWorkflowStreamEvent,
    handleChatCapabilityStreamEvent,
    applyToolMetadata,
  } = deps

  let sendStartTime = 0
  // 用户点终止后，等待后端通过 SSE 推 [DONE]+assistantMessageId 的兜底 abort 定时器
  let stopFallbackTimer = null
  const clearStopFallbackTimer = () => {
    if (stopFallbackTimer) {
      clearTimeout(stopFallbackTimer)
      stopFallbackTimer = null
    }
  }

  async function submitAskUserResponse(payload) {
    if (!payload?.answers || !payload?.text || askUserModal.messageIndex < 0) {
      message.warning('提问状态已失效，请重新打开提问组件')
      return
    }
    recordAskUserAnswers?.(askUserModal.messageIndex, payload.answers)
    askUserModal.visible = false
    const text = payload.text.trim()
    messages.value.push({ role: 'user', content: text, _attachments: [] })
    isNearBottom.value = true
    userScrolledUp.value = false
    scrollToBottom()
    await runChatStream({
      message: text,
      attachments: [],
      regenerate: false,
    })
  }

  /** 业务页回灌字段格式化：嵌套对象展开；展示名优先用页面采集的 fieldLabels */
  function formatBusinessPageResultLines(result) {
    const lines = []
    const fieldLabels = (result?.fieldLabels && typeof result.fieldLabels === 'object')
      ? result.fieldLabels
      : {}
    const displayKey = (key) => {
      const label = fieldLabels[key]
      return (typeof label === 'string' && label.trim()) ? label.trim() : key
    }
    const walk = (key, value, indent = '') => {
      const name = displayKey(key)
      if (value == null || value === '') {
        lines.push(`${indent}- ${name}：`)
        return
      }
      if (Array.isArray(value)) {
        if (value.every((x) => x == null || typeof x !== 'object')) {
          lines.push(`${indent}- ${name}：${value.join('、')}`)
          return
        }
        lines.push(`${indent}- ${name}：`)
        value.forEach((item, i) => walk(`[${i}]`, item, `${indent}  `))
        return
      }
      if (typeof value === 'object') {
        // values 容器本身不输出空父级行，直接展开字段
        if (key === 'values') {
          Object.entries(value).forEach(([k, v]) => walk(k, v, indent))
          return
        }
        lines.push(`${indent}- ${name}：`)
        Object.entries(value).forEach(([k, v]) => walk(k, v, `${indent}  `))
        return
      }
      lines.push(`${indent}- ${name}：${String(value)}`)
    }
    Object.entries(result || {})
      .filter(([k]) => k !== 'pageType' && k !== 'action' && k !== 'fieldLabels' && k !== 'extra')
      .forEach(([k, v]) => walk(k, v))
    return lines.length ? lines : ['- （无字段）']
  }

  /**
   * 业务办理页提交/取消回灌对话（结构化文本，供 Agent 继续）
   * @param {{ messageIndex: number, result: object, status: 'submitted'|'cancelled' }} payload
   */
  async function submitBusinessPageResult(payload) {
    if (payload?.messageIndex == null || payload.messageIndex < 0 || !payload?.result) {
      message.warning('业务页状态已失效')
      return
    }
    const status = payload.status === 'cancelled' ? 'cancelled' : 'submitted'
    const result = payload.result
    const pageType = result.pageType || 'unknown'
    const values = (result.values && typeof result.values === 'object' && !Array.isArray(result.values))
      ? result.values
      : {}
    const fieldLabels = (result.fieldLabels && typeof result.fieldLabels === 'object')
      ? result.fieldLabels
      : {}
    const lines = formatBusinessPageResultLines(
      Object.keys(values).length ? { values, fieldLabels } : result,
    )
    // 机器可读标记：刷新历史时可还原办理字段与页面标签（禁止平台硬编码字段词典）
    let dataMarker = ''
    try {
      dataMarker = `\n<!--lightbot-bp-data:${JSON.stringify({ values, fieldLabels })}-->`
    } catch {
      dataMarker = ''
    }
    // 发给模型的上下文：要求自动简短确认，无需用户再问
    const text = status === 'cancelled'
      ? `系统通知：用户取消了业务办理页（${pageType}）。请用一两句话确认已取消，不要追问、不要要求用户再次输入。${dataMarker}`
      : `系统通知：用户已在业务办理页完成提交（${pageType}）。办理数据如下：\n${lines.join('\n')}\n请直接根据上述结果给用户一句简短确认提示（到账/受理情况即可），不要追问、不要要求用户再次输入。${dataMarker}`
    // 本地标记已处理，避免重复提交
    const msg = messages.value[payload.messageIndex]
    if (msg?._toolEvents?.length) {
      for (let i = msg._toolEvents.length - 1; i >= 0; i--) {
        const evt = msg._toolEvents[i]
        if (evt.type === 'tool_result' && evt.toolName === 'present_business_page') {
          evt.businessPageResult = { status, ...result }
          break
        }
      }
    }
    // 回灌消息对用户隐藏为「系统通知」条，不表现为用户再次提问
    messages.value.push({
      role: 'user',
      content: text,
      _attachments: [],
      _businessPageCallback: true,
      _businessPageCallbackStatus: status,
      _businessPageCallbackPageType: pageType,
      _businessPageCallbackValues: values,
      _businessPageCallbackFieldLabels: fieldLabels,
    })
    isNearBottom.value = true
    userScrolledUp.value = false
    scrollToBottom()
    await runChatStream({
      message: text,
      attachments: [],
      regenerate: false,
      // 业务办理提交回灌：即使 Agent 开启深度思考，本轮也不走思维链
      enableReasoning: false,
    })
  }

  /** 流被用户终止后，仅在本次请求确实落库时同步消息 ID（禁止借用历史助手消息 ID） */
  async function syncAbortedAssistantMessageId(sid, assistantMsg, sendStartMs) {
    if (!sid || !assistantMsg || assistantMsg._persisted) return
    // 确知未落库的错误态（LLM 异常且 DONE 未带 assistantMessageId、敏感拦截）不轮询
    // _terminated 不早退：终止消息后端一定落库（metadata.aborted=true），此处为 [DONE] 兜底补 ID
    if (assistantMsg._error || assistantMsg._sensitiveBlock) {
      return
    }
    for (let attempt = 0; attempt < 4; attempt++) {
      await new Promise(r => setTimeout(r, 250 * (attempt + 1)))
      if (assistantMsg._persisted) return
      try {
        const res = await getSessionMessages(sid, { pageNum: 1, pageSize: 5 })
        const records = res.data?.records || []
        const latestAssistant = records.find(m => {
          const role = String(m.role?.code || m.role || '').toLowerCase()
          return role === 'assistant'
        })
        if (!latestAssistant?.id) continue
        const createdAt = latestAssistant.createTime ? new Date(latestAssistant.createTime).getTime() : 0
        if (sendStartMs && createdAt > 0 && createdAt < sendStartMs - 3000) {
          continue
        }
        assistantMsg._id = String(latestAssistant.id)
        assistantMsg._persisted = true
        assistantMsg._createTime = latestAssistant.createTime || assistantMsg._createTime
        await loadBatchFeedbacks([assistantMsg])
        return
      } catch {
        // 忽略，继续重试
      }
    }
  }

  function finalizeAbortedStream(assistantMsg, pushed) {
    if (assistantMsg?._terminated) {
      streamSmoother.stop()
      setCurrentStreamingMsg(null)
      reconnecting.value = false
      loading.value = false
      streaming.value = false
      hasStreamContent.value = false
      currentStatus.value = ''
      abortController.value = null
      userStoppedStream.value = false
      return
    }

    streamSmoother.stop()
    setCurrentStreamingMsg(null)
    reconnecting.value = false

    let targetMsg = assistantMsg
    if (!pushed) {
      targetMsg = {
        role: 'assistant',
        content: '*AI 输出已终止*',
        _streaming: false,
        _toolsDone: true,
        _toolEvents: [],
        _workflowEvents: [],
        _toolBlockOffsets: [],
        _toolBlocksDone: [],
        _toolExpanded: false,
        _reasoningContent: '',
        _reasoningExpanded: true,
        _reasoningDone: true,
        _terminated: true,
        _persisted: false,
      }
      messages.value.push(targetMsg)
    } else if (assistantMsg) {
      if (!assistantMsg._toolBlockOffsets?.length) {
        assistantMsg._toolBlockOffsets = getToolBlockOffsets(assistantMsg)
      }
      if (!assistantMsg.content?.includes('AI 输出已终止')) {
        assistantMsg.content = (assistantMsg.content || '') + (assistantMsg.content ? '\n\n' : '') + '*AI 输出已终止*'
      }
      assistantMsg._streaming = false
      assistantMsg._toolsDone = true
      assistantMsg._toolExpanded = false
      assistantMsg._terminated = true
      targetMsg = assistantMsg
    }

    if (!targetMsg._persisted) {
      targetMsg._id = null
      targetMsg._persisted = false
    }

    normalizeAssistantMessageErrors(targetMsg)

    loading.value = false
    streaming.value = false
    hasStreamContent.value = false
    currentStatus.value = ''
    lastReplyElapsed.value = Date.now() - sendStartTime
    abortController.value = null
    userStoppedStream.value = false
    // 后端可能在流中断后仍落库并生成标题，继续轮询
    pollSessionTitle(sessionId.value)
    syncAbortedAssistantMessageId(sessionId.value, targetMsg, sendStartTime)

    if (isNearBottom.value) {
      nextTick(() => {
        const el = messagesRef.value
        if (el) el.scrollTop = el.scrollHeight
      })
    }
  }

  const router = useRouter()

  async function runChatStream({ message: msgText, attachments, mentions, regenerate, editMessageId: editMsgId, replyToMessageId: replyMsgId, deleteAssistantMessageId, enableReasoning }) {
    loading.value = true
    streaming.value = true
    hasStreamContent.value = false
    lastReplyElapsed.value = null
    currentStatus.value = '正在思考...'
    toolEvents.value = []
    sendStartTime = Date.now()

    let assistantMsg = null
    let pushed = false
    let pendingRequestId = null
    abortController.value = new AbortController()

    const attachRequestId = (msg) => {
      if (msg && pendingRequestId) {
        msg._requestId = pendingRequestId
      }
    }

    const ensureAssistantMsg = (toolExpanded = false) => {
      if (pushed) return assistantMsg
      messages.value.push({
        role: 'assistant',
        content: '',
        _streaming: true,
        _toolsDone: false,
        _toolEvents: [],
        _workflowEvents: [],
        _toolBlockOffsets: [],
        _toolBlocksDone: [],
        _toolExpanded: toolExpanded,
        _reasoningContent: '',
        _reasoningExpanded: true,
        _reasoningDone: false,
        _persisted: false,
      })
      assistantMsg = messages.value[messages.value.length - 1]
      setCurrentStreamingMsg(assistantMsg)
      attachRequestId(assistantMsg)
      pushed = true
      hasStreamContent.value = true
      streamSmoother.start()
      return assistantMsg
    }

    // 工具事件 RAF 批处理：高频 SSE（subagent_token/tool_status 等）合并到单帧刷新，
    // 把 30+ 次/秒的响应式触发压到约 60 帧/秒（每帧一次 v-for 重渲染）
    let pendingToolEvents = []
    let toolEventRafHandle = 0
    const flushToolEventBatch = () => {
      toolEventRafHandle = 0
      if (pendingToolEvents.length === 0) return
      const events = pendingToolEvents
      pendingToolEvents = []
      // 同步连续 push：Vue 微任务会合并为单次重渲染
      for (const ev of events) {
        assistantMsg?._toolEvents?.push(ev)
        toolEvents.value.push(ev)
      }
      scrollToBottom()
    }
    const batchToolEvent = (event) => {
      pendingToolEvents.push(event)
      if (toolEventRafHandle === 0) {
        toolEventRafHandle = requestAnimationFrame(flushToolEventBatch)
      }
    }

    try {
      let sid = sessionId.value
      const currentAgentId = selectedAgentId.value

      if (!sid) {
        const res = await createSession(currentAgentId || undefined)
        sid = res.data.id
        skipNextWatch.value = true
        router.replace(`/app/chat/${sid}`)
      }

      // 首轮携带模拟角色；续聊省略，由服务端从会话 caller_context 回填（避免与已绑定身份冲突）
      const userTurns = messages.value.filter((m) => m?.role === 'user').length
      const sendCallerContext = !regenerate && !editMsgId && userTurns <= 1
        ? (simCallerContext?.value || undefined)
        : undefined

      const chatPayload = {
        message: msgText || undefined,
        sessionId: sid,
        agentId: currentAgentId || undefined,
        configVersion: selectedConfigVersion.value ?? 0,
        agentVersionId: selectedAgentVersionId.value || undefined,
        regenerate: regenerate || undefined,
        deleteAssistantMessageId: deleteAssistantMessageId || undefined,
        editMessageId: editMsgId || undefined,
        replyToMessageId: replyMsgId || undefined,
        // 仅业务页回灌等场景显式传 false；undefined 表示沿用 Agent 深度思考配置
        enableReasoning: enableReasoning === false ? false : undefined,
        callerContext: sendCallerContext,
        mentions: mentions?.length ? mentions.map(m => ({
          type: m.type,
          resourceId: String(m.resourceId),
          name: m.name,
          token: m.token,
        })) : undefined,
        attachments: attachments?.length ? attachments.map(a => ({
          id: a.id,
          type: a.type,
          mimeType: a.mimeType,
          objectKey: a.objectKey,
          previewUrl: a.previewUrl,
          fileName: a.fileName,
        })) : undefined,
      }
      await chatStream(
        chatPayload,
        {
          onRequestId: (requestId) => {
            if (requestId) {
              pendingRequestId = requestId
              attachRequestId(assistantMsg)
            }
          },
          // onChunk: 正文 chunk（深度思考由后端 reasoning_content 事件推送）
          onChunk: (chunk) => {
            if (reconnecting.value) reconnecting.value = false
            if (!pushed) {
              messages.value.push({ role: 'assistant', content: '', _streaming: true, _toolsDone: false, _toolEvents: [], _workflowEvents: [], _toolBlockOffsets: [], _toolBlocksDone: [], _toolExpanded: false, _reasoningContent: '', _reasoningExpanded: true, _reasoningDone: false, _persisted: false })
              assistantMsg = messages.value[messages.value.length - 1]
              setCurrentStreamingMsg(assistantMsg)
              attachRequestId(assistantMsg)
              pushed = true
              hasStreamContent.value = true
              streamSmoother.start()
            }
            streamSmoother.push(chunk)
          },
          // onStatus: 状态消息
          onStatus: (status) => {
            currentStatus.value = status
            scrollToBottom()
          },
          // onToolEvent: 工具调用/结果/状态事件
          onToolEvent: (event) => {
            ensureAssistantMsg(event.type === 'tool_call' || event.type === 'tool_result')
            if (event.type === 'tool_complete') {
              const offset = event.contentOffset ?? assistantMsg._currentToolOffset
              markToolBlockDone(assistantMsg, offset)
              return
            }
            if (event.type === 'reasoning_content') {
              if (!pushed) {
                messages.value.push({ role: 'assistant', content: '', _streaming: true, _toolsDone: false, _toolEvents: [], _workflowEvents: [], _toolBlockOffsets: [], _toolBlocksDone: [], _toolExpanded: false, _reasoningContent: '', _reasoningExpanded: true, _reasoningDone: false, _persisted: false })
                assistantMsg = messages.value[messages.value.length - 1]
                setCurrentStreamingMsg(assistantMsg)
                attachRequestId(assistantMsg)
                pushed = true
                hasStreamContent.value = true
                streamSmoother.start()
              }
              clearErrorRetry(assistantMsg)
              assistantMsg._reasoningContent = (assistantMsg._reasoningContent || '') + event.content
              assistantMsg._reasoningDone = false
              scrollToBottom()
              scrollReasoningToBottom()
              return
            }
            // 敏感词拦截事件：标记消息为拦截状态
            if (event.type === 'sensitive_block') {
              // 兜底：SENSITIVE_USER 场景无任何前置 chunk/reasoning/tool 事件，
              // assistantMsg 可能尚未初始化，此处先 ensure 避免后续读取 undefined 抛错
              ensureAssistantMsg()
              assistantMsg._sensitiveBlock = event.scope || 'ai_output'
              assistantMsg.content = event.message || assistantMsg.content
              assistantMsg._streaming = false
              assistantMsg._toolsDone = true
              loading.value = false
              streaming.value = false
              hasStreamContent.value = false
              currentStatus.value = ''
              lastReplyElapsed.value = Date.now() - sendStartTime
              abortController.value = null
              return
            }
            // 上下文压缩实时状态：started 时切换占位文案，completed/failed 后恢复"正在思考…"
            // 让用户在长会话首次响应延迟期间看到"正在压缩 N 条"提示，避免误判卡死
            if (event.type === 'context_compression') {
              if (event.status === 'started') {
                currentStatus.value = event.message || '正在压缩上下文…'
                scrollToBottom()
              } else {
                // completed/failed：恢复默认思考态，等首个 LLM chunk 到达后再被覆盖
                currentStatus.value = ''
              }
              return
            }
            // 1.3 模型调用重试事件：保留流式状态，只更新专门提示块
            if (event.type === 'error_retry') {
              assistantMsg._errorRetry = {
                message: event.message || 'AI连接异常，正在重试中',
                code: event.code || 'LLM_ERROR',
                attempt: event.attempt || 1,
                maxRetries: event.maxRetries || event.attempt || 1,
              }
              currentStatus.value = assistantMsg._errorRetry.message
              scrollToBottom()
              return
            }
            // 1.3 结构化错误事件：LLM 调用中断、工具异常等
            if (event.type === 'error') {
              assistantMsg._error = {
                message: event.message || '未知错误',
                code: event.code || 'UNKNOWN',
              }
              assistantMsg._errorRetry = null
              assistantMsg._persisted = false
              assistantMsg._id = null
              normalizeAssistantMessageErrors(assistantMsg)
              assistantMsg._streaming = false
              assistantMsg._toolsDone = true
              loading.value = false
              streaming.value = false
              hasStreamContent.value = false
              currentStatus.value = ''
              lastReplyElapsed.value = Date.now() - sendStartTime
              abortController.value = null
              scrollToBottom()
              return
            }
            const isWorkflowStreamEvent = event.type === 'workflow_llm_chunk'
              || event.type === 'workflow_node_start'
              || event.type === 'workflow_node_complete'
              || event.type === 'workflow_node_retry'
              || event.type === 'workflow_node_failure'
              || event.type === 'workflow_complete'
              || event.type === 'workflow_confirm_required'
              || event.type === 'workflow_suspended'
            if (isWorkflowStreamEvent) {
              ensureAssistantMsg()
              handleChatWorkflowStreamEvent(assistantMsg, event)
              return
            }

            streamSmoother.flush()

            ensureAssistantMsg()
            if (handleChatCapabilityStreamEvent(assistantMsg, event)) {
              return
            }

            // 关键低频事件（tool_call/tool_result/subagent_task_*）立即更新 UI 状态：
            // _toolExpanded / _currentToolOffset / toolBlockOffsets 必须在事件到达时就位，
            // 否则后续 subagent_token 事件的 contentOffset 会落到错误位置。
            const offset = event.contentOffset ?? assistantMsg.content.length
            if (event.contentOffset == null) {
              event.contentOffset = offset
            }
            if (event.type === 'tool_call') {
              assistantMsg._toolExpanded = true
              assistantMsg._currentToolOffset = offset
              registerToolBlockOffset(assistantMsg, offset)
            } else if (assistantMsg._currentToolOffset == null || assistantMsg._currentToolOffset < 0) {
              assistantMsg._currentToolOffset = offset
              registerToolBlockOffset(assistantMsg, offset)
            }

            // tool_status 提示文案：低频，立即生效
            if (event.type === 'tool_status' && event.message) {
              currentStatus.value = event.message
            }

            // 高频事件批量入队：一帧内的所有事件合并到单次响应式更新（RAF 批处理）
            batchToolEvent(event)
          },
          // onMetadata: metadata消息（含工具事件与 offset，每轮工具调用后更新）
          onMetadata: (metadataStr) => {
            if (!assistantMsg) return
            applyToolMetadata(assistantMsg, safeJsonParse(metadataStr))
          },
          // onDone: 完成
          onDone: (meta) => {
            // 流结束前冲刷 RAF 缓冲区，确保最后一批 toolEvents 落入响应式数组
            if (toolEventRafHandle !== 0) {
              cancelAnimationFrame(toolEventRafHandle)
              toolEventRafHandle = 0
              flushToolEventBatch()
            }
            // 用户主动停止时仍合并 [DONE] 中的消息 ID（后端 buildDoneEvent 会落库并回传 assistantMessageId+aborted:true）
            if (userStoppedStream.value) {
              // 收到 [DONE]，取消兜底 abort 定时器
              clearStopFallbackTimer()
              if (assistantMsg) {
                // 合并 [DONE] 元数据：设置 _id、_persisted=true、metadata.aborted 等
                applyStreamDoneMetadata(assistantMsg, meta)
                if (assistantMsg._id) loadBatchFeedbacks([assistantMsg])
              }
              // finalizeAbortedStream 会把 _terminated=true 并清流式态；此时 _persisted 已由上一步置 true，
              // 不会走 finalize 里的 `_id = null` 兜底分支，ID 得以保留供后续 regenerate/edit 删库
              finalizeAbortedStream(assistantMsg, pushed)
              return
            }
            streamSmoother.stop()
            setCurrentStreamingMsg(null)
            reconnecting.value = false
            if (assistantMsg) {
              assistantMsg._reasoningDone = true
              assistantMsg._streaming = false
              assistantMsg._toolsDone = true
              assistantMsg._toolExpanded = false
              applyStreamDoneMetadata(assistantMsg, meta)
              applyToolMetadata(assistantMsg, typeof assistantMsg.metadata === 'object' ? assistantMsg.metadata : null)
              assistantMsg._toolBlockOffsets = getToolBlockOffsets(assistantMsg)
              if (assistantMsg._workflowConfirmPending) {
                loading.value = false
                streaming.value = false
              }
              if (assistantMsg._id) {
                loadBatchFeedbacks([assistantMsg])
              }
            }
            loading.value = false
            streaming.value = false
            hasStreamContent.value = false
            if (!assistantMsg?._workflowConfirmPending) {
              currentStatus.value = ''
            }
            lastReplyElapsed.value = Date.now() - sendStartTime
            abortController.value = null
            // 轮询等待标题生成完成
            pollSessionTitle(sid)
            // 流式结束后滚动到底部（延迟等待渲染完成）
            if (isNearBottom.value) {
              setTimeout(() => {
                const el = messagesRef.value
                if (el) el.scrollTop = el.scrollHeight
              }, 300)
            }
            // 流式结束后自动弹出 ask_user 弹窗
            nextTick(() => {
              const lastIdx = messages.value.length - 1
              if (lastIdx >= 0 && messages.value[lastIdx].role === 'assistant' && isAskUserUnanswered(lastIdx)) {
                showAskUserModal(lastIdx)
              }
            })
          },
        },
        abortController.value?.signal,
        { maxRetries: 3, retryDelay: 2000, onReconnecting: () => {
          reconnecting.value = true
          currentStatus.value = '正在重连...'
        }}
      )
      if (userStoppedStream.value) {
        finalizeAbortedStream(assistantMsg, pushed)
        return
      }
    } catch (e) {
      reconnecting.value = false
      if (e.name === 'AbortError') {
        finalizeAbortedStream(assistantMsg, pushed)
        return
      }
      streamSmoother.stop()
      setCurrentStreamingMsg(null)
      if (!assistantMsg) {
        messages.value.push({ role: 'assistant', content: '', _streaming: false, _toolsDone: true, _toolEvents: [], _workflowEvents: [], _toolBlockOffsets: [], _toolBlocksDone: [], _toolExpanded: false, _reasoningContent: '', _reasoningExpanded: true, _reasoningDone: true, _persisted: false })
        assistantMsg = messages.value[messages.value.length - 1]
      }
      assistantMsg._error = {
        message: 'AI 大模型调用失败，请检查模型配置是否正确。\n\n错误详情：' + (e.message || '未知错误'),
        code: 'REQUEST_ERROR',
      }
      assistantMsg._persisted = false
      assistantMsg._id = null
      normalizeAssistantMessageErrors(assistantMsg)
      assistantMsg._streaming = false
      assistantMsg._toolsDone = true
      loading.value = false
      streaming.value = false
      hasStreamContent.value = false
      currentStatus.value = ''
      abortController.value = null
      pollSessionTitle(sessionId.value)
    }
  }

  async function sendMessage() {
    const text = input.value.trim()
    const attachments = [...pendingAttachments.value]
    if ((!text && attachments.length === 0) || !canSend.value) {
      if (workflowConfirmBlocked.value && (text || attachments.length)) {
        message.warning('请先完成工作流人工确认表单，再发送新消息')
      }
      return
    }
    const mixCheck = validatePendingAttachmentMix(attachments)
    if (!mixCheck.ok) {
      message.warning(mixCheck.message)
      return
    }

    // 从 ChatMentionInput 提取结构化 mentions（在重置 input 之前）
    const mentionInputComp = inputRef.value
    const sentMentions = mentionInputComp?.getMentions?.() || undefined

    const displayContent = text || (attachments.length ? '[附件]' : '')
    const sentAttachments = attachments.map(a => ({ ...a }))
    await enrichVideoThumbnails(sentAttachments)

    const userMsg = { role: 'user', content: displayContent, _attachments: sentAttachments }
    // 携带 mention 快照（用于前端 chip 渲染，后端 message.metadata 也会持久化一份）
    if (sentMentions?.length) {
      userMsg._mentions = sentMentions
    }
    // 携带引用回复信息（用于前端渲染引用摘要）
    const currentReplyToId = replyTo.active ? replyTo.messageId : null
    const currentReplyToContent = replyTo.active ? replyTo.content : ''
    const currentReplyToRole = replyTo.active ? replyTo.role : ''
    if (currentReplyToId) {
      userMsg._replyToMessageId = currentReplyToId
      userMsg._replyToContent = currentReplyToContent
      userMsg._replyToRole = currentReplyToRole
    }
    messages.value.push(userMsg)
    // 记录输入历史（去重：与上一条相同则不重复记录）
    if (text && (inputHistory.value.length === 0 || inputHistory.value[inputHistory.value.length - 1] !== text)) {
      inputHistory.value.push(text)
    }
    historyIndex.value = -1
    input.value = ''
    // 清空 ChatMentionInput 中的 chip（input 清空会触发 watch 重置 innerHTML，mentions 也需手动清）
    mentionInputComp?.clear?.()
    pendingAttachments.value = []
    cancelReply()
    autoResize()
    isNearBottom.value = true
    userScrolledUp.value = false
    scrollToBottom()

    await runChatStream({
      message: text,
      attachments: sentAttachments,
      mentions: sentMentions,
      regenerate: false,
      replyToMessageId: currentReplyToId,
    })
  }

  async function regenerateReply(assistantIndex) {
    if (loading.value || !canRegenerate(assistantIndex)) return
    let userIdx = assistantIndex - 1
    while (userIdx >= 0 && messages.value[userIdx].role !== 'user') {
      userIdx--
    }
    if (userIdx < 0) return
    const userMsg = messages.value[userIdx]
    const assistantMsg = messages.value[assistantIndex]
    const deleteId = resolveDeleteAssistantMessageId(assistantMsg)
    // 仅移除当前助手消息；未落库时不传 deleteAssistantMessageId，避免误删历史成功回复
    if (assistantIndex === messages.value.length - 1) {
      messages.value.pop()
    } else {
      messages.value.splice(assistantIndex, 1)
    }
    isNearBottom.value = true
    userScrolledUp.value = false
    scrollToBottom()
    await runChatStream({
      message: userMsg.content === '[附件]' ? '' : (userMsg.content || ''),
      attachments: userMsg._attachments || [],
      mentions: getMsgMentions(userMsg).length ? getMsgMentions(userMsg) : undefined,
      regenerate: true,
      deleteAssistantMessageId: deleteId,
    })
  }

  function stopGenerating() {
    if (!abortController.value) return

    // 通知后端停止：置中断标记使 in-flight LLM 立即停，并连带取消子任务（fire-and-forget）
    const requestId = getCurrentStreamingMsg()?._requestId
    if (requestId) {
      stopChatStream(requestId).catch(() => {})
    }

    // 标记为用户主动停止，让 onDone 走「合并 [DONE] 元数据但不覆盖 UI」分支
    userStoppedStream.value = true

    // 立即停止打字机平滑动画：把 smoother buffer 里残留内容一次性 flush 显示，
    // 避免用户点停止后到后端 [DONE]/5s 兜底期间还看到逐字吐字
    streamSmoother.stop()

    // 立即置 _streaming=false：消除 MarkdownPreview 末尾的闪烁光标（typing-cursor），
    // 让用户点停止后界面立即进入「终态」，不再有任何流式动画迹象
    // 后续 [DONE] 到达时 finalizeAbortedStream 会再次赋值（幂等），不会冲突
    const stoppingMsg = getCurrentStreamingMsg()
    if (stoppingMsg) {
      stoppingMsg._streaming = false
    }

    // 立即置流式相关 UI 为完结态，用户感知立即停止；但保留 SSE 连接等 [DONE] 补 ID
    // [DONE] 到达时 onDone 会合并 assistantMessageId，然后本方法末尾的 finalize 逻辑收尾
    currentStatus.value = '正在中断...'

    // 兜底：5s 内没收到 [DONE] 就 abort TCP 走 AbortError 分支收尾
    // 后端 buildDoneEvent 落库 + 发 [DONE] 通常在 100ms 内完成，5s 足够容错
    clearStopFallbackTimer()
    stopFallbackTimer = setTimeout(() => {
      stopFallbackTimer = null
      if (abortController.value) {
        abortController.value.abort()
        abortController.value = null
      }
      // 若 abort 也未触发 finalize，直接落地结束态
      if (!streaming.value) return
      const msg = getCurrentStreamingMsg()
        || [...messages.value].reverse().find(m => m.role === 'assistant' && m._streaming)
      if (msg) finalizeAbortedStream(msg, messages.value.includes(msg))
    }, 5000)
  }

  return {
    clearErrorRetry,
    submitAskUserResponse,
    submitBusinessPageResult,
    syncAbortedAssistantMessageId,
    finalizeAbortedStream,
    runChatStream,
    sendMessage,
    regenerateReply,
    stopGenerating,
  }
}
