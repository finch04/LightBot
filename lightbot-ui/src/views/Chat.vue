<template>
  <div class="chat-container">
    <ChatTopBar
      :session-title="sessionTitle"
      :title-editing="titleEditing"
      :title-edit-value="titleEditValue"
      :show-file-drawer="!!sessionId && !enterpriseReadonly"
      :show-runtime-panel="!!sessionId"
      :runtime-panel-open="sessionRuntimePanelOpen"
      :show-subagent-drawer="!!sessionId"
      :subagent-running-count="runningSubagentCount"
      :title-editable="!!sessionId && !enterpriseReadonly"
      :show-sim-role="!enterpriseReadonly"
      :sim-role-user-id="userStore.user?.id"
      :session-has-messages="messages.length > 0"
      :sim-role-disabled="enterpriseReadonly"
      @start-title-edit="startTitleEdit"
      @confirm-title-edit="confirmTitleEdit"
      @cancel-title-edit="cancelTitleEdit"
      @update:title-edit-value="titleEditValue = $event"
      @open-file-drawer="openFileDrawer"
      @toggle-runtime-panel="sessionRuntimePanelOpen = !sessionRuntimePanelOpen"
      @open-subagent-drawer="subagentRuntimeOpen = true"
      @sim-role-change="onSimRoleChange"
      @sim-role-new-session="onSimRoleNewSession"
    />

    <div class="chat-workspace">
    <div class="chat-primary">
    <div ref="messagesRef" class="chat-messages">
      <ChatWelcomeState
        v-if="!sessionId && messages.length === 0 && !loadingHistory"
        :welcome-message="currentWelcomeMessage"
        :recommended-questions="currentRecommendedQuestions"
        :selected-agent-id="selectedAgentId"
        :agents-length="agents.length"
        :loading="currentAgentLoading || agentsLoading"
        @select-question="applyRecommendedQuestion"
      />

      <div v-if="loadingHistory" class="history-loading">
        <LoadingOutlined spin class="history-loading-icon" />
      </div>
      <div
        v-else
        class="virtual-list-container"
        :style="{ height: virtualizer.getTotalSize() + 'px', position: 'relative' }"
      >
        <div
          v-for="virtualRow in virtualizer.getVirtualItems()"
          :key="virtualRow.key"
          :data-index="virtualRow.index"
          :ref="el => { if (el) virtualizer.measureElement(el) }"
          :style="{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            transform: `translateY(${virtualRow.start}px)`,
          }"
        >
          <ChatMessageRow
            :ref="el => setMessageRowComponentRef(virtualRow.index, el)"
            :msg="messages[virtualRow.index]"
            :index="virtualRow.index"
            :loading="loading"
            :streaming="streaming"
            :editing-message-id="editingMessageId"
            :edit-content="editContent"
            :highlight-message-id="highlightMessageId"
            :feedback-type="getMessageFeedbackType(messages[virtualRow.index])"
            :can-regenerate="canRegenerate(virtualRow.index)"
            :is-last-user-message="isLastUserMessage(virtualRow.index)"
            :speaking-msg-key="speakingMsgKey"
            :show-tts-btn="showTtsBtn"
            :last-reply-elapsed="lastReplyElapsed"
            :selected-agent-id="selectedAgentId"
            :selected-agent-version-id="selectedAgentVersionId"
            :get-att-thumb-url="getAttThumbUrl"
            :messages="messages"
            :messages-length="messages.length"
            :refs-section-expanded="isRefsSectionExpanded(messages[virtualRow.index])"
            :is-ref-expanded="(refIndex) => isReferenceExpanded(messages[virtualRow.index], refIndex)"
            @update:edit-content="editContent = $event"
            @cancel-edit="cancelEdit"
            @submit-edit="submitEdit"
            @scroll-to-reply="scrollToMessage"
            @preview-attachment="onAttachmentPreview"
            @height-change="onCapabilityHeightChange"
            @copy="copyMessage(messages[virtualRow.index])"
            @regenerate="regenerateReply(virtualRow.index)"
            @edit="startEdit(virtualRow.index)"
            @reply="startReply(virtualRow.index)"
            @delete="handleDeleteMessage(virtualRow.index)"
            @star="toggleStarMessage(virtualRow.index)"
            @feedback-like="handleMessageFeedback(messages[virtualRow.index], 'like')"
            @show-dislike="showDislikeModal(messages[virtualRow.index])"
            @speak="speakMessage(messages[virtualRow.index], virtualRow.index)"
            @view-raw="openRawModal(virtualRow.index)"
            @copy-request-id="copyRequestId(messages[virtualRow.index])"
            @copy-message-id="copyMessageId(messages[virtualRow.index])"
            @workflow-confirm-submit="formData => submitWorkflowConfirm(messages[virtualRow.index], formData)"
            @workflow-confirm-abandon="() => abandonWorkflowConfirm(messages[virtualRow.index])"
            @rag-toggle="onRagToggle(virtualRow.index, $event)"
            @reasoning-toggle="toggleReasoningExpand(virtualRow.index)"
            @go-knowledge="({ knowledgeId, documentId }) => goToKnowledge(knowledgeId, documentId)"
          />
        </div>
      </div>

      <!-- 回复未终止（loading 为 true）时持续展示回复动画，位于 AI 回复内容下方 -->
      <ChatStreamingPlaceholder
        v-if="loading"
        :status-text="streamingIndicatorText"
        :status-badges="streamingSubagentBadges"
      />
    </div>

    <ChatInputArea
      ref="chatInputAreaRef"
      v-model:input="input"
      :loading="loading"
      :can-send="canSend && !enterpriseReadonly"
      :workflow-confirm-blocked="workflowConfirmBlocked"
      :ask-user-blocked="askUserModal.visible"
      :read-only="enterpriseReadonly"
      :switching-session="inputMaskLoading"
      :agents="agents"
      :selected-agent-id="selectedAgentId"
      :selected-agent-version-id="selectedAgentVersionId"
      :current-agent="currentAgent"
      :config-version-options="configVersionOptions"
      :selected-config-version="selectedConfigVersion"
      :session-token-count="sessionTokenCount"
      :show-file-upload-btn="showFileUploadBtn"
      :file-upload-hint="fileUploadHint"
      :image-upload-hint="imageUploadHint"
      :file-accept-types="fileAcceptTypes"
      :image-accept-types="imageAcceptTypes"
      :document-accept-types="documentAcceptTypes"
      :uploading="uploading"
      :show-voice-input-btn="showVoiceInputBtn"
      :voice-listening="voiceListening"
      :reply-to="replyTo"
      :pending-attachments="pendingAttachments"
      :get-att-thumb-url="getAttThumbUrl"
      :show-input-disclaimer="showInputDisclaimer"
      :input-hint-questions="inputHintQuestions"
      :question-rotate-index="questionRotateIndex"
      @send="sendMessage"
      @stop="stopGenerating"
      @agent-select="handleAgentSelect"
      @config-version-change="onConfigVersionChange"
      @file-selected="onFileSelected"
      @remove-attachment="removeAttachment"
      @attachment-preview="onAttachmentPreview"
      @toggle-voice="toggleVoiceInput"
      @cancel-reply="cancelReply"
      @apply-question="applyRecommendedQuestion"
    />
    </div>

    <ChatSessionRuntimePanel
      v-if="sessionId && sessionRuntimePanelOpen"
      :session-id="sessionId"
      :messages="currentRequestMessages"
      :live-events="liveSubagentEvents"
      :task-ids="currentRequestSubagentTaskIds"
      :parent-request-id="currentRequestId"
      :width="sessionRuntimePanelWidth"
      @open-files="openFileDrawer"
      @preview-attachment="onAttachmentPreview"
      @resize-start="startRuntimePanelResize"
    />
    </div>

    <ChatAttachmentPreview
      v-model:open="attachmentPreviewOpen"
      :attachment="attachmentPreviewAtt"
    />

    <ChatRawContentModal
      v-model:open="rawModal.visible"
      :title="rawModal.title"
      :content="rawModal.content"
      :copied="rawModal.copied"
      :metadata="rawModal.metadata"
      v-model:metadata-open="metadataModal.visible"
      :metadata-json="metadataModal.json"
      :metadata-copied="metadataModal.copied"
      @copy-raw="copyRawContent"
      @copy-metadata="copyMetadata"
      @open-metadata="openMetadataModal"
    />

    <ChatAskUserModal
      v-model:open="askUserModal.visible"
      :questions="askUserModal.questions"
      @submit="submitAskUserResponse"
    />

    <ChatFeedbackDislikeModal
      v-model:open="dislikeModalVisible"
      v-model:reason="dislikeReason"
      @submit="submitDislikeReason"
      @skip="skipDislikeReason"
      @close="closeDislikeModal"
    />

    <ChatSessionFilesDrawer
      v-model:open="fileDrawerOpen"
      :session-id="sessionId"
      :file-stats="fileStats"
      :file-drawer-loading="fileDrawerLoading"
      :file-tree-refresh-tick="fileTreeRefreshTick"
      :tooltip-popup-container="tooltipPopupContainer"
      @after-open-change="onFileDrawerOpened"
      @refresh="refreshSessionFiles"
      @preview="openSessionFilePreviewModal"
      @refreshed="onFileTreeRefreshed"
    />

    <ChatSubAgentRuntimeDrawer
      v-model:open="subagentRuntimeOpen"
      :session-id="sessionId"
      :live-events="liveSubagentEvents"
    />

    <SessionFilePreviewModal
      v-model:open="sessionFilePreviewOpen"
      :session-id="sessionId"
      :file="sessionFilePreviewTarget"
    />


  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick, provide } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LoadingOutlined } from '@ant-design/icons-vue'
import ChatAttachmentPreview from '../components/ChatAttachmentPreview.vue'
import SessionFilePreviewModal from '../components/SessionFilePreviewModal.vue'
import ChatTopBar from '../components/chat/ChatTopBar.vue'
import ChatWelcomeState from '../components/chat/ChatWelcomeState.vue'
import ChatMessageRow from '../components/chat/message/ChatMessageRow.vue'
import ChatInputArea from '../components/chat/input/ChatInputArea.vue'
import ChatRawContentModal from '../components/chat/modals/ChatRawContentModal.vue'
import ChatAskUserModal from '../components/chat/modals/ChatAskUserModal.vue'
import ChatFeedbackDislikeModal from '../components/chat/modals/ChatFeedbackDislikeModal.vue'
import ChatSessionFilesDrawer from '../components/chat/session/ChatSessionFilesDrawer.vue'
import ChatSubAgentRuntimeDrawer from '../components/chat/session/ChatSubAgentRuntimeDrawer.vue'
import ChatSessionRuntimePanel from '../components/chat/session/ChatSessionRuntimePanel.vue'
import ChatStreamingPlaceholder from '../components/chat/session/ChatStreamingPlaceholder.vue'
import { getSession } from '../api/chatSession'
import { useUserStore } from '../stores/user'
import { buildSubagentLiveStatusBadges } from '../components/capabilities/subagentEventUtils.js'
import { useChatAgents } from '../composables/useChatAgents'
import { useChatAttachments } from '../composables/useChatAttachments'
import { useVoiceIO } from '../composables/useVoiceIO'
import { useAskUser } from '../composables/useAskUser'
import { useStreamSmoother } from '../composables/useStreamSmoother'
import {
  useChatSendGate,
  createChatWorkflowStreamHandlers,
  createChatCapabilityStreamHandlers,
  useChatMessageModel,
  useChatFeedback,
  useChatRagRefs,
  useChatInputHint,
  useChatScroll,
  useChatHistory,
  useChatStream,
  useChatMessageActions,
  useChatSessionChrome,
  getMsgRagRefs,
  clearErrorRetry,
} from '../composables/chat'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const sessionId = computed(() => route.params.sessionId || null)
/** API 集成 / 自动化会话：只读排障，禁止控制台续聊 */
const enterpriseReadonly = ref(false)
/** 当前选中的模拟角色 → 请求 callerContext */
const simCallerContext = ref(null)

async function refreshEnterpriseReadonly() {
  const sid = sessionId.value
  if (!sid) {
    enterpriseReadonly.value = false
    return
  }
  try {
    const res = await getSession(sid)
    const source = res.data?.source
    enterpriseReadonly.value = source === 'api' || source === 'automation'
  } catch {
    enterpriseReadonly.value = false
  }
}

const input = ref('')
const inputHistory = ref([])
const historyIndex = ref(-1)
const loading = ref(false)
const streaming = ref(false)
const messages = ref([])
const sessionTokenCount = ref(0)
const messagesRef = ref(null)
const inputRef = ref(null)
const chatInputAreaRef = ref(null)
const skipNextWatch = ref(false)
const loadingHistory = ref(false)
const currentStatus = ref('')
const reconnecting = ref(false)
const lastReplyElapsed = ref(null)
const hasStreamContent = ref(false)
const userStoppedStream = ref(false)
const abortController = ref(null)
const toolEvents = ref([])
const pendingAttachments = ref([])
const fileInputRef = ref(null)
const uploading = ref(false)
const attachmentPreviewOpen = ref(false)
const attachmentPreviewAtt = ref(null)
const voiceListening = ref(false)
const speakingMsgKey = ref(null)
const subagentRuntimeOpen = ref(false)
const liveSubagentEvents = ref([])
const sessionRuntimePanelOpen = ref(false)
const sessionRuntimePanelWidth = ref(336)
const currentRequestStartIndex = ref(null)
let stopRuntimePanelResize = null

function onSimRoleChange(payload) {
  simCallerContext.value = payload?.callerContext || null
}

function onSimRoleNewSession() {
  skipNextWatch.value = true
  messages.value = []
  sessionTokenCount.value = 0
  liveSubagentEvents.value = []
  router.push('/app/chat')
}

/** 协作状态的待办/产物属于一轮用户任务，历史轮次只由各自消息与会话级抽屉承载。 */
const currentRequestMessages = computed(() => {
  if (currentRequestStartIndex.value !== null) {
    return messages.value.slice(currentRequestStartIndex.value)
  }
  const latestUserIndex = messages.value.map(item => item?.role).lastIndexOf('user')
  return latestUserIndex >= 0 ? messages.value.slice(latestUserIndex + 1) : []
})

/** 已落库的 assistant metadata 保存 requestId，用于刷新/重进时恢复该轮协作任务。 */
const currentRequestId = computed(() => {
  for (let index = currentRequestMessages.value.length - 1; index >= 0; index--) {
    const requestId = currentRequestMessages.value[index]?._requestId
    if (requestId) return String(requestId)
  }
  return null
})

/** 当前请求由 SSE 产生的任务 ID 是协作状态栏唯一允许展示的子智能体范围。 */
const currentRequestSubagentTaskIds = computed(() => {
  const taskIds = new Set()
  for (const event of liveSubagentEvents.value) {
    if (event?.task_id) taskIds.add(String(event.task_id))
    if (event?.type === 'subagent_batch_start') {
      for (const task of event.tasks || []) {
        if (task?.task_id) taskIds.add(String(task.task_id))
      }
    }
  }
  return [...taskIds]
})

function startRuntimePanelResize(event) {
  if (event.button !== undefined && event.button !== 0) return
  event.preventDefault()
  stopRuntimePanelResize?.()

  const startX = event.clientX
  const startWidth = sessionRuntimePanelWidth.value
  const minWidth = 280
  const maxWidth = Math.max(minWidth, Math.min(560, window.innerWidth - 440))
  const onPointerMove = (moveEvent) => {
    const nextWidth = startWidth + startX - moveEvent.clientX
    sessionRuntimePanelWidth.value = Math.min(maxWidth, Math.max(minWidth, nextWidth))
  }
  const stop = () => {
    window.removeEventListener('pointermove', onPointerMove)
    window.removeEventListener('pointerup', stop)
    document.body.style.userSelect = ''
    document.body.style.cursor = ''
    stopRuntimePanelResize = null
  }

  document.body.style.userSelect = 'none'
  document.body.style.cursor = 'col-resize'
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', stop, { once: true })
  stopRuntimePanelResize = stop
}

const runningSubagentCount = computed(() => {
  const taskStates = new Map()
  for (const event of liveSubagentEvents.value) {
    if (!event?.task_id) continue
    if (event.type === 'subagent_task_start') taskStates.set(event.task_id, 'running')
    if (event.type === 'subagent_task_done') taskStates.set(event.task_id, event.status || 'completed')
    if (event.type === 'subagent_error') taskStates.set(event.task_id, 'failed')
  }
  return [...taskStates.values()].filter(status => status === 'running').length
})

const streamingSubagentBadges = computed(() => {
  if (!loading.value && !streaming.value) return []
  return buildSubagentLiveStatusBadges(liveSubagentEvents.value)
})

function autoResize() {}

const {
  agents, agentsLoading, selectedAgentId, currentAgent, chatCapabilities,
  selectedConfigVersion, selectedAgentVersionId, configVersionOptions,
  showFileUploadBtn, showVoiceInputBtn, showTtsBtn,
  fileAcceptTypes, imageAcceptTypes, documentAcceptTypes, fileUploadHint, imageUploadHint,
  currentWelcomeMessage, currentRecommendedQuestions, currentAgentLoading,
  handleAgentSelect, onConfigVersionChange,
  loadAgentConfigVersions, loadCurrentAgent, loadAgents,
} = useChatAgents({
  sessionId, loading, pendingAttachments, voiceListening,
  stopVoiceInput: () => { voiceListening.value = false },
})

watch(chatInputAreaRef, (area) => {
  if (area?.inputRef) inputRef.value = area.inputRef
}, { flush: 'post' })

const {
  getAttThumbUrl, openAttachmentPreview, onFileSelected, removeAttachment,
} = useChatAttachments({
  selectedAgentId, sessionId, chatCapabilities, pendingAttachments,
  fileInputRef, uploading, attachmentPreviewOpen, attachmentPreviewAtt,
})

const { toggleVoiceInput, stopVoiceInput, speakMessage, cleanup: voiceCleanup } = useVoiceIO({
  input, inputRef, chatCapabilities, autoResize, voiceListening, speakingMsgKey,
})

const {
  sessionTitle, titleEditing, titleEditValue,
  fileDrawerOpen, fileDrawerLoading, fileTreeRefreshTick, fileStats,
  fileDrawerLoadedOnce,
  sessionFilePreviewOpen, sessionFilePreviewTarget,
  startTitleEdit, confirmTitleEdit, cancelTitleEdit, tooltipPopupContainer,
  openFileDrawer, onFileDrawerOpened, refreshSessionFiles, onFileTreeRefreshed,
  onAttachmentPreview, openSessionFilePreviewModal, pollSessionTitle, cleanupPollTitleTimer,
} = useChatSessionChrome({
  sessionId,
  openAttachmentPreview,
  attachmentPreviewOpen,
  attachmentPreviewAtt,
})

const {
  getMessageFeedbackType, handleMessageFeedback, showDislikeModal,
  submitDislikeReason, skipDislikeReason, closeDislikeModal, loadBatchFeedbacks,
  dislikeModalVisible, dislikeReason,
} = useChatFeedback()

const editingMessageId = ref(null)
const editContent = ref('')

const {
  enrichMessagesAttachments, parseMessage, applyStreamDoneMetadata, getMsgMentions,
} = useChatMessageModel({ editingMessageId, messages, sessionTokenCount })

function scrollAfterExpandBridge(msgIndex, expandEl) {
  scrollAfterExpandBridge.impl?.(msgIndex, expandEl)
}

const {
  expandedRefsMap, refsSectionExpandedMap,
  isReferenceExpanded, toggleReference,
  isRefsSectionExpanded, toggleRefsSection, toggleReasoningExpand,
} = useChatRagRefs({ messages, scrollAfterExpand: scrollAfterExpandBridge })

const {
  isNearBottom, userScrolledUp, virtualizer,
  onCapabilityHeightChange, scrollToBottom, scrollReasoningToBottom,
  forceScrollToBottom, scrollAfterExpand, createScrollHandler,
} = useChatScroll({
  messages, messagesRef, streaming, getMsgRagRefs, refsSectionExpandedMap,
})

scrollAfterExpandBridge.impl = scrollAfterExpand

const { askUserModal, isAskUserUnanswered, showAskUserModal, recordAskUserAnswers } = useAskUser({ messages })

let currentStreamingMsg = null
const streamSmoother = useStreamSmoother({
  onFlush: (text) => {
    if (currentStreamingMsg) {
      clearErrorRetry(currentStreamingMsg)
      currentStreamingMsg.content += text
      scrollToBottom()
    }
  },
})

const {
  handleChatWorkflowStreamEvent,
  submitWorkflowConfirm,
  abandonWorkflowConfirm,
  applyToolMetadata,
} = createChatWorkflowStreamHandlers({
  loading, streaming, currentStatus, hasStreamContent, streamSmoother,
  scrollToBottom, clearErrorRetry,
  getCurrentStreamingMsg: () => currentStreamingMsg,
  setCurrentStreamingMsg: (msg) => { currentStreamingMsg = msg },
  getAbortController: () => abortController.value,
  setAbortController: (ctrl) => { abortController.value = ctrl },
  getSelectedAgentId: () => selectedAgentId.value,
})

const { handleChatCapabilityStreamEvent } = createChatCapabilityStreamHandlers({
  currentStatus, hasStreamContent, scrollToBottom, messages, messagesRef,
  onSubagentEvent: (event) => {
    liveSubagentEvents.value.push({ ...event })
    if (liveSubagentEvents.value.length > 600) {
      liveSubagentEvents.value.splice(0, liveSubagentEvents.value.length - 600)
    }
  },
})

const { canSend, workflowConfirmBlocked } = useChatSendGate({
  loading, input, pendingAttachments, messages,
})

const {
  inputHintQuestions, showInputDisclaimer, questionRotateIndex, applyRecommendedQuestion,
} = useChatInputHint({ messages, currentRecommendedQuestions, selectedAgentId, input, inputRef })

const messageRowComponentRefs = new Map()

function setMessageRowComponentRef(index, el) {
  if (el) messageRowComponentRefs.set(index, el)
  else messageRowComponentRefs.delete(index)
}

function resolveEditInputRefFn() {
  const eid = editingMessageId.value
  if (!eid) return null
  let idx = messages.value.findIndex(m => m._id === eid)
  if (idx < 0 && String(eid).startsWith('local-')) {
    const parsed = parseInt(String(eid).replace('local-', ''), 10)
    if (!Number.isNaN(parsed) && parsed < messages.value.length) idx = parsed
  }
  if (idx < 0) return null
  return messageRowComponentRefs.get(idx)?.getEditMentionInput?.() ?? null
}

const streamHolder = {}

const {
  replyTo, highlightMessageId, rawModal, metadataModal,
  canRegenerate, isLastUserMessage, startEdit, cancelEdit, startReply, cancelReply,
  toggleStarMessage, scrollToMessage, submitEdit,
  copyMessage, copyRequestId, copyMessageId,
  openRawModal, openMetadataModal, copyRawContent, copyMetadata,
  handleDeleteMessage, cleanupEditClickOutside,
} = useChatMessageActions({
  sessionId, messages, loading, streaming, virtualizer,
  isNearBottom, userScrolledUp, scrollToBottom,
  runChatStream: (opts) => streamHolder.runChatStream(opts),
  inputRef, editingMessageId, editContent, resolveEditInputRefFn,
})

const {
  submitAskUserResponse, submitBusinessPageResult, runChatStream,
  sendMessage, regenerateReply, stopGenerating,
} = useChatStream({
  sessionId, messages, messagesRef, loading, streaming, hasStreamContent,
  currentStatus, reconnecting, lastReplyElapsed, abortController, userStoppedStream,
  toolEvents, isNearBottom, userScrolledUp, skipNextWatch, input, inputHistory,
  historyIndex, pendingAttachments, inputRef, replyTo, canSend, workflowConfirmBlocked,
  selectedAgentId, selectedConfigVersion, selectedAgentVersionId, sessionTokenCount,
  simCallerContext,
  streamSmoother,
  getCurrentStreamingMsg: () => currentStreamingMsg,
  setCurrentStreamingMsg: (msg) => { currentStreamingMsg = msg },
  scrollToBottom, scrollReasoningToBottom, applyStreamDoneMetadata, loadBatchFeedbacks,
  pollSessionTitle, isAskUserUnanswered, showAskUserModal, askUserModal, recordAskUserAnswers, cancelReply,
  autoResize, canRegenerate, getMsgMentions,
  handleChatWorkflowStreamEvent, handleChatCapabilityStreamEvent, applyToolMetadata,
})

streamHolder.runChatStream = runChatStream
provide('submitBusinessPageResult', submitBusinessPageResult)
provide('chatMessages', messages)

const {
  hasMoreMessages, loadingOlder, switchingSession,
  loadHistory, loadOlderMessages, onStreamingEnded,
} = useChatHistory({
  sessionId, messages, streaming, virtualizer, messagesRef,
  isNearBottom, userScrolledUp, forceScrollToBottom,
  selectedAgentId, currentAgent, loadAgentConfigVersions, loadCurrentAgent,
  loadBatchFeedbacks, parseMessage, enrichMessagesAttachments,
  isAskUserUnanswered, showAskUserModal, cancelReply,
  loadingHistory, lastReplyElapsed, sessionTokenCount, sessionTitle,
  input, inputHistory, historyIndex, pendingAttachments,
  fileDrawerLoadedOnce,
  sessionFilePreviewTarget, sessionFilePreviewOpen, fileStats,
  onSessionMissing: () => router.replace({ path: '/app/chat' }),
})

/** 回复动画文案：优先展示具体状态（思考/工具/工作流等），无状态时回退为通用生成提示 */
const streamingIndicatorText = computed(() => currentStatus.value || '正在生成回复...')

/** 输入框加载遮罩：首次加载 Agent 列表 或 切换会话期间 */
const inputMaskLoading = computed(() => agentsLoading.value || switchingSession.value)

function onRagToggle(index, payload) {
  const msg = messages.value[index]
  if (!msg) return
  if (payload?.kind === 'section') toggleRefsSection(msg)
  else if (payload?.kind === 'item') toggleReference(msg, payload.refIndex)
}

function goToKnowledge(knowledgeId, documentId) {
  const query = documentId ? { docId: String(documentId) } : {}
  router.push({ path: `/app/knowledge/${knowledgeId}`, query })
}

function handleChatKeydown(e) {
  if (e.ctrlKey && e.code === 'Slash') {
    e.preventDefault()
    chatInputAreaRef.value?.focusInput?.()
    return
  }
  if (e.key === 'Escape' && streaming.value) {
    e.preventDefault()
    stopGenerating()
  }
}

function abortActiveStreamOnUnload() {
  if (streaming.value && abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
}

const scrollHandler = createScrollHandler({ loadOlderMessages, hasMoreMessages, loadingOlder })

onMounted(async () => {
  const queryAgentId = route.query.agentId
  refreshEnterpriseReadonly()
  loadHistory()
  await loadAgents(queryAgentId || undefined)
  if (queryAgentId) {
    await loadCurrentAgent(queryAgentId)
    await loadAgentConfigVersions(queryAgentId)
  } else if (selectedAgentId.value) {
    await loadAgentConfigVersions(selectedAgentId.value)
  }
  if (messagesRef.value) {
    messagesRef.value.addEventListener('scroll', scrollHandler)
  }
  document.addEventListener('keydown', handleChatKeydown)
  window.addEventListener('beforeunload', abortActiveStreamOnUnload)
})

onUnmounted(() => {
  stopRuntimePanelResize?.()
  abortActiveStreamOnUnload()
  voiceCleanup()
  cleanupPollTitleTimer()
  cleanupEditClickOutside()
  if (messagesRef.value) {
    messagesRef.value.removeEventListener('scroll', scrollHandler)
  }
  document.removeEventListener('keydown', handleChatKeydown)
  window.removeEventListener('beforeunload', abortActiveStreamOnUnload)
})

watch(() => route.params.sessionId, () => {
  if (skipNextWatch.value) {
    skipNextWatch.value = false
    return
  }
  if (streaming.value && abortController.value) {
    userStoppedStream.value = true
    abortController.value.abort()
    abortController.value = null
  }
  expandedRefsMap.value = new Map()
  refsSectionExpandedMap.value = new Map()
  liveSubagentEvents.value = []
  currentRequestStartIndex.value = null
  messageRowComponentRefs.clear()
  refreshEnterpriseReadonly()
  loadHistory()
})

watch(streaming, onStreamingEnded)

watch(loading, (isLoading, wasLoading) => {
  if (isLoading && !wasLoading) {
    // 每次发起新请求都丢弃本会话上一轮的任务事件，防止协作状态混入历史子智能体。
    currentRequestStartIndex.value = messages.value.length
    liveSubagentEvents.value = []
  }
}, { flush: 'sync' })

watch(selectedAgentId, (newId) => {
  if (newId && !sessionId.value) {
    loadCurrentAgent(newId)
  } else if (!newId) {
    currentAgent.value = null
  }
})

watch(sessionId, (newVal, oldVal) => {
  if (!newVal && oldVal) loadAgents()
})
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  /* 顶栏改造后对话区在 MainLayout 内，用 content-height，避免高出顶栏被裁切 */
  height: var(--app-content-height);
  max-height: var(--app-content-height);
  overflow: hidden;
  background: var(--color-canvas);
}

.chat-workspace {
  display: flex;
  min-height: 0;
  flex: 1;
}

.chat-primary {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
  /* 禁用滚动锚定：能力块展开时浏览器会为保持锚点不动而调整 scrollTop，
     导致块在视口上半时向上展开、下半时向下展开。关闭后统一从上往下展开 */
  overflow-anchor: none;
}

.history-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
}

.history-loading-icon {
  font-size: 24px;
  color: var(--color-mute);
}

.virtual-list-container {
  width: 100%;
  max-width: 800px;
  margin: 0 auto;
}
</style>

<style>
.chat-upload-tooltip .chat-upload-hint {
  display: block;
  white-space: pre-line;
  line-height: 1.5;
}
</style>
