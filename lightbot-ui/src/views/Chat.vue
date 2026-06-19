<template>
  <div class="chat-container">
    <!-- 消息列表 -->
    <div class="chat-messages" ref="messagesRef">
      <!-- 欢迎状态（新对话 + 无消息） -->
      <div v-if="!sessionId && messages.length === 0 && !loadingHistory" class="empty-state">
        <img src="/lightbot-logo-single.png" alt="LightBot" class="empty-logo" />
        <div class="welcome-content"><MarkdownPreview :content="currentWelcomeMessage" /></div>
        <!-- 推荐问题：全部展示 -->
        <div v-if="currentRecommendedQuestions.length > 0" class="recommended-questions">
          <button
            v-for="(q, qi) in currentRecommendedQuestions"
            :key="qi"
            class="btn-question"
            @click="input = q; $nextTick(() => $refs.inputRef?.focus())"
          >
            {{ q }}
          </button>
        </div>
        <!-- 无默认Agent提示 -->
        <div v-if="!selectedAgentId && agents.length > 0" class="no-default-hint">
          没有默认Agent，<router-link to="/app/agents">去创建</router-link>
        </div>
      </div>

      <!-- 加载更早的消息 -->
      <div v-if="hasMoreMessages && !streaming" class="load-more-area">
        <a-button size="small" :loading="loadingOlder" @click="loadOlderMessages">
          加载更早的消息
        </a-button>
      </div>

      <!-- 虚拟滚动消息列表 -->
      <div
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
          <div :class="['message', messages[virtualRow.index]?.role]">
            <div class="message-body">
              <div class="message-content-wrapper" :class="{ 'user-message-stack': messages[virtualRow.index]?.role === 'user' }">
                <!-- 用户附件 -->
                <div
                  v-if="messages[virtualRow.index]?.role === 'user' && getMsgAttachments(messages[virtualRow.index]).length && !messages[virtualRow.index]._sensitiveBlock"
                  class="user-message-attachments"
                >
                  <ChatAttachmentTile
                    v-for="(att, ai) in getMsgAttachments(messages[virtualRow.index])"
                    :key="att.id || ai"
                    :att="att"
                    :thumb-url="getAttThumbUrl(att)"
                    @preview="openAttachmentPreview"
                  />
                </div>
                <!-- 敏感词拦截提示 -->
                <div v-if="messages[virtualRow.index]?._sensitiveBlock" class="sensitive-block-alert" :class="messages[virtualRow.index]._sensitiveBlock">
                  <WarningOutlined class="sensitive-block-icon" />
                  <span class="sensitive-block-text">{{ messages[virtualRow.index].content }}</span>
                </div>
                <!-- 深度思考面板 -->
                <div v-if="messages[virtualRow.index]?._reasoningContent && !messages[virtualRow.index]._sensitiveBlock" class="reasoning-panel">
                  <div class="reasoning-header" @click="messages[virtualRow.index]._reasoningExpanded = !messages[virtualRow.index]._reasoningExpanded">
                    <BulbOutlined class="reasoning-icon" />
                    <span class="reasoning-title">深度思考</span>
                    <LoadingOutlined v-if="messages[virtualRow.index]._streaming && !messages[virtualRow.index]._reasoningDone" class="reasoning-spinner" />
                    <RightOutlined :class="{ expanded: messages[virtualRow.index]._reasoningExpanded }" class="tool-expand-icon" />
                  </div>
                  <div v-show="messages[virtualRow.index]._reasoningExpanded" class="reasoning-content">{{ messages[virtualRow.index]._reasoningContent }}</div>
                </div>
                <!-- Skill 启用 -->
                <div v-if="getTopCapabilityEvents(messages[virtualRow.index]).length > 0 && !messages[virtualRow.index]._sensitiveBlock" class="capability-block-inline">
                  <AgentCapabilityPanel
                    :events="getTopCapabilityEvents(messages[virtualRow.index])"
                    :is-done="!messages[virtualRow.index]._streaming || messages[virtualRow.index]._toolsDone"
                    :default-expanded="true"
                  />
                </div>
                <!-- 工作流节点执行 -->
                <div v-if="messages[virtualRow.index]?._workflowEvents?.length > 0 && !messages[virtualRow.index]._sensitiveBlock" class="workflow-block-inline">
                  <WorkflowNodesGroupComponent
                    :workflow-events="messages[virtualRow.index]._workflowEvents"
                    :is-done="!messages[virtualRow.index]._streaming"
                    :default-expanded="!!messages[virtualRow.index]._streaming"
                    :is-streaming="!!messages[virtualRow.index]._streaming"
                  />
                </div>
                <!-- 有工具事件：按 offset 位置插入工具块 -->
                <template v-if="!messages[virtualRow.index]._sensitiveBlock && messages[virtualRow.index]._toolEvents?.length > 0 && getToolBlockOffsets(messages[virtualRow.index]).length > 0">
                  <template v-for="(segment, si) in splitContentByOffsets(messages[virtualRow.index])" :key="si">
                    <div v-if="segment.type === 'text'" class="message-content"><MarkdownPreview :content="segment.text" :finalized="!messages[virtualRow.index]._streaming" /></div>
                    <div v-else-if="segment.type === 'tool'" class="tool-block-inline">
                      <AgentCapabilityPanel
                        v-if="getCapabilityEventsForOffset(messages[virtualRow.index], segment.offset).length > 0"
                        :events="getCapabilityEventsForOffset(messages[virtualRow.index], segment.offset)"
                        :is-done="isToolBlockDone(messages[virtualRow.index], segment.offset)"
                        :default-expanded="true"
                      />
                      <ToolCallsGroupComponent
                        v-if="getPureToolEvents(getToolEventsForOffset(messages[virtualRow.index], segment.offset)).length > 0"
                        :tool-events="getPureToolEvents(getToolEventsForOffset(messages[virtualRow.index], segment.offset))"
                        :is-done="isToolBlockDone(messages[virtualRow.index], segment.offset)"
                        :default-expanded="true"
                      />
                    </div>
                  </template>
                </template>
                <!-- 有工具事件但 offset 尚未到达 -->
                <template v-else-if="!messages[virtualRow.index]._sensitiveBlock && messages[virtualRow.index]._toolEvents?.length > 0">
                  <div v-if="messages[virtualRow.index].content" class="message-content"><MarkdownPreview :content="messages[virtualRow.index].content" :finalized="!messages[virtualRow.index]._streaming" /></div>
                  <div class="tool-block-inline">
                    <AgentCapabilityPanel
                      v-if="getInlineCapabilityEvents(messages[virtualRow.index]).length > 0"
                      :events="getInlineCapabilityEvents(messages[virtualRow.index])"
                      :is-done="messages[virtualRow.index]._toolsDone"
                      :default-expanded="true"
                    />
                    <ToolCallsGroupComponent
                      v-if="getPureToolEvents(messages[virtualRow.index]._toolEvents).length > 0"
                      :tool-events="getPureToolEvents(messages[virtualRow.index]._toolEvents)"
                      :is-done="messages[virtualRow.index]._toolsDone"
                      :default-expanded="true"
                    />
                  </div>
                </template>
                <!-- 无工具事件：正常渲染 -->
                <template v-else-if="!messages[virtualRow.index]._sensitiveBlock">
                  <div v-if="messages[virtualRow.index].content && messages[virtualRow.index].content !== '[附件]'" class="message-content">
                    <MarkdownPreview :content="messages[virtualRow.index].content" :finalized="!messages[virtualRow.index]._streaming" />
                  </div>
                </template>
                <!-- 操作按钮 -->
                <div
                  v-if="!messages[virtualRow.index]._streaming && messages[virtualRow.index].content && !messages[virtualRow.index]._sensitiveBlock"
                  class="message-actions"
                >
                  <a-tooltip
                    v-if="messages[virtualRow.index].role === 'assistant' && showTtsBtn"
                    :title="speakingMsgKey === virtualRow.index ? '停止朗读' : '朗读'"
                  >
                    <button
                      class="btn-copy"
                      :class="{ speaking: speakingMsgKey === virtualRow.index }"
                      @click="speakMessage(messages[virtualRow.index], virtualRow.index)"
                    >
                      <SoundOutlined />
                    </button>
                  </a-tooltip>
                  <a-tooltip :title="messages[virtualRow.index]._copied ? '已复制' : '复制'">
                    <button
                      class="btn-copy"
                      :class="{ copied: messages[virtualRow.index]._copied }"
                      @click="copyMessage(messages[virtualRow.index])"
                    >
                      <CheckOutlined v-if="messages[virtualRow.index]._copied" />
                      <CopyOutlined v-else />
                    </button>
                  </a-tooltip>
                  <a-tooltip v-if="messages[virtualRow.index].role === 'assistant' && canRegenerate(virtualRow.index)" title="重新生成">
                    <button class="btn-copy btn-action-text" :disabled="loading" @click="regenerateReply(virtualRow.index)">
                      <ReloadOutlined />
                    </button>
                  </a-tooltip>
                  <a-tooltip
                    v-if="messages[virtualRow.index].role === 'assistant' && messages[virtualRow.index]._requestId"
                    :title="messages[virtualRow.index]._requestIdCopied ? '已复制' : '复制 Request ID'"
                  >
                    <button
                      class="btn-copy btn-action-text"
                      :class="{ copied: messages[virtualRow.index]._requestIdCopied }"
                      @click="copyRequestId(messages[virtualRow.index])"
                    >
                      <CheckOutlined v-if="messages[virtualRow.index]._requestIdCopied" />
                      <NumberOutlined v-else />
                    </button>
                  </a-tooltip>
                </div>
              </div>
              <!-- RAG引用列表 -->
              <div v-if="messages[virtualRow.index]?.role === 'assistant' && getMsgRagRefs(messages[virtualRow.index]).length > 0 && !messages[virtualRow.index]._streaming" class="rag-references">
                <div class="rag-header">
                  <FileTextOutlined />
                  <span>参考文献 ({{ getMsgRagRefs(messages[virtualRow.index]).length }})</span>
                </div>
                <div class="rag-list">
                  <div v-for="(ref, ri) in getMsgRagRefs(messages[virtualRow.index])" :key="ri" class="rag-item">
                    <div class="rag-item-header" @click="toggleReference(messages[virtualRow.index], ri)">
                      <RightOutlined :class="{ expanded: isReferenceExpanded(messages[virtualRow.index], ri) }" />
                      <a-tag v-if="ref.sourceType === 'qa_pair'" color="success" class="rag-qa-tag">问答对</a-tag>
                      <span v-else class="rag-doc-name">{{ ref.documentName }}</span>
                      <span class="rag-score">{{ (ref.score * 100).toFixed(1) }}%</span>
                      <a-tooltip v-if="ref.knowledgeId" title="查看知识库">
                        <LinkOutlined class="rag-nav-btn" @click.stop="goToKnowledge(ref.knowledgeId, ref.documentId)" />
                      </a-tooltip>
                    </div>
                    <div v-if="isReferenceExpanded(messages[virtualRow.index], ri)" class="rag-item-content">
                      {{ ref.contentPreview }}
                    </div>
                  </div>
                </div>
              </div>
              <!-- 耗时显示 -->
              <div v-if="messages[virtualRow.index]?.role === 'assistant' && virtualRow.index === messages.length - 1 && !messages[virtualRow.index]._streaming && lastReplyElapsed !== null" class="reply-elapsed">
                {{ formatElapsed(lastReplyElapsed) }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 加载中（等待第一个 chunk） -->
      <div v-if="loading && !streaming" class="message assistant">
        <div class="message-body">
          <div class="message-meta">回复</div>
          <div class="message-content status-content">
            <div class="status-loading">
              <span class="status-spinner"></span>
              <span class="status-text">{{ currentStatus || '正在思考...' }}</span>
            </div>
          </div>
        </div>
      </div>
      <!-- 流式输出中但尚未创建助手消息时显示加载动画（避免与消息列表中的助手气泡重复） -->
      <div v-if="loading && streaming && !hasStreamContent && !hasStreamingAssistantMessage" class="message assistant">
        <div class="message-body">
          <div class="message-meta">回复</div>
          <div class="message-content status-content">
            <div class="status-loading">
              <span class="status-spinner"></span>
              <span class="status-text">{{ currentStatus || '正在思考...' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="chat-input-wrapper">
      <div class="chat-input-shell">
        <div class="chat-input-toolbar">
          <!-- Agent 列表为空时显示气泡引导 -->
          <a-popover v-if="agents.length === 0" trigger="click" placement="topLeft">
            <template #content>
              <div class="empty-agent-tip">
                系统里还没有智能体，<router-link to="/app/agents">点击创建智能体</router-link>
              </div>
            </template>
            <button type="button" class="btn-agent">
              <RobotOutlined />
            </button>
          </a-popover>
          <!-- Agent 列表不为空时正常下拉 -->
          <a-dropdown v-else :trigger="['click']" placement="topLeft">
            <a-tooltip :title="currentAgent?.name || '选择 Agent'">
              <button type="button" class="btn-agent">
                <RobotOutlined v-if="!currentAgent" />
                <img v-else-if="currentAgent.avatar" :src="currentAgent.avatar" alt="" class="btn-agent-avatar" />
                <span v-else class="btn-agent-initial">{{ currentAgent.name[0] }}</span>
              </button>
            </a-tooltip>
            <template #overlay>
              <a-menu @click="handleAgentSelect" :selectedKeys="selectedAgentId ? [String(selectedAgentId)] : []">
                <a-menu-item v-for="a in agents" :key="String(a.id)">
                  <div class="agent-menu-item">
                    <img v-if="a.avatar" :src="a.avatar" alt="" class="agent-menu-icon" />
                    <span v-else class="agent-menu-icon">{{ a.name[0] }}</span>
                    <span class="agent-menu-name">{{ a.name }}</span>
                    <span v-if="agentVersionLabel(a)" class="agent-version-tag">{{ agentVersionLabel(a) }}</span>
                    <span v-if="a.isDefault" class="agent-default-tag">默认</span>
                  </div>
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
          <span v-if="currentAgent?.name" class="chat-toolbar-agent-name">{{ currentAgent.name }}</span>
          <a-select
            v-if="selectedAgentId && configVersionOptions.length > 0"
            v-model:value="selectedConfigVersion"
            class="config-version-select"
            :disabled="loading"
            popup-class-name="config-version-select-dropdown"
            @change="onConfigVersionChange"
          >
            <a-select-option
              v-for="opt in configVersionOptions"
              :key="String(opt.value)"
              :value="opt.value"
              :label="opt.selectLabel"
            >
              <span class="version-option-row">
                <span class="version-option-num">{{ opt.versionLabel }}</span>
                <a-tag v-if="opt.badge === 'draft'" class="version-status-tag draft" :bordered="false">草稿</a-tag>
                <a-tag v-else-if="opt.badge === 'online'" class="version-status-tag online" color="success" :bordered="false">线上</a-tag>
              </span>
            </a-select-option>
          </a-select>
        </div>
        <div class="chat-input">
          <input
            ref="fileInputRef"
            type="file"
            class="hidden-file-input"
            :accept="fileAcceptTypes"
            @change="onFileSelected"
          />
          <a-tooltip
            v-if="showFileUploadBtn"
            overlay-class-name="no-flip-tooltip chat-upload-tooltip"
            :overlay-style="{ maxWidth: '360px' }"
          >
            <template #title>
              <span class="chat-upload-hint">{{ fileUploadHint || '上传附件' }}</span>
            </template>
            <button
              type="button"
              class="btn-attach"
              :class="{ 'btn-attach--uploading': uploading }"
              :disabled="loading || uploading"
              @click="triggerFileUpload"
            >
              <LoadingOutlined v-if="uploading" spin />
              <PaperClipOutlined v-else />
            </button>
          </a-tooltip>
          <textarea
            ref="inputRef"
            v-model="input"
            class="input-textarea"
            placeholder="输入消息... (Enter 发送, Shift+Enter 换行)"
            rows="1"
            spellcheck="false"
            @keydown="handleKeydown"
            @input="autoResize"
          />
          <div class="chat-input-actions">
            <div v-if="voiceListening" class="voice-listening-indicator">
              <VoiceMicVisualizer :active="voiceListening" />
              <span class="voice-listening-text">聆听中</span>
            </div>
            <a-tooltip v-if="showVoiceInputBtn" title="语音转文字">
              <button
                type="button"
                class="btn-voice"
                :class="{ listening: voiceListening }"
                :disabled="loading"
                @click="toggleVoiceInput"
              >
                <AudioOutlined />
              </button>
            </a-tooltip>
            <button
              v-if="loading"
              class="btn-stop"
              @click="stopGenerating"
              title="停止生成"
            >
              <PauseCircleOutlined />
            </button>
            <button
              v-else
              class="btn-send"
              :disabled="!canSend"
              @click="sendMessage"
            >
              <SendOutlined />
            </button>
          </div>
        </div>
      </div>
      <div v-if="uploading || pendingAttachments.length > 0" class="pending-attachments">
        <span class="pending-att-count">
          <template v-if="uploading">附件上传中…</template>
          <template v-else>已选 {{ pendingAttachments.length }} 个附件</template>
        </span>
        <div class="pending-att-thumbs">
          <ChatAttachmentTile
            v-if="uploading"
            :att="{ type: 'uploading', fileName: '上传中' }"
            uploading
          />
          <ChatAttachmentTile
            v-for="(att, i) in pendingAttachments"
            :key="att.id || i"
            :att="att"
            :thumb-url="getAttThumbUrl(att)"
            removable
            @preview="openAttachmentPreview"
            @remove="removeAttachment(i)"
          />
        </div>
      </div>
      <div class="input-hint">LightBot 可能会犯错，请核实重要信息。</div>
    </div>

    <ChatAttachmentPreview
      v-model:open="attachmentPreviewOpen"
      :attachment="attachmentPreviewAtt"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, watch, computed } from 'vue'
import { useVirtualizer } from '@tanstack/vue-virtual'
import { useRoute, useRouter } from 'vue-router'
import { SendOutlined, CopyOutlined, CheckOutlined, RobotOutlined, FileTextOutlined, RightOutlined, LinkOutlined, PauseCircleOutlined, LoadingOutlined, CheckCircleOutlined, BulbOutlined, WarningOutlined, PaperClipOutlined, AudioOutlined, CloseOutlined, PlayCircleOutlined, EyeOutlined, SoundOutlined, ReloadOutlined, NumberOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { chatStream, uploadChatAttachment, refreshChatAttachmentPreviews } from '../api/chat'
import {
  buildUploadHint,
  buildFileAcceptTypes,
  validateChatAttachmentFile,
  validateAttachmentCount,
  validateAttachmentMix,
  validatePendingAttachmentMix,
} from '../utils/chatAttachment'
import { captureVideoThumbnail, enrichVideoThumbnails } from '../utils/videoThumbnail'
import { getSessionMessages, getSession, createSession, getSessionTitle } from '../api/chatSession'
import { getAgents, getAgentDetail, getAgentChatCapabilities, listAgentVersions } from '../api/agent'
import { useUserStore } from '../stores/user'
import { safeJsonParse } from '../utils/request'
import MarkdownPreview from '../components/MarkdownPreview.vue'
import ToolCallsGroupComponent from '../components/ToolCallsGroupComponent.vue'
import WorkflowNodesGroupComponent from '../components/WorkflowNodesGroupComponent.vue'
import AgentCapabilityPanel from '../components/AgentCapabilityPanel.vue'
import ChatAttachmentPreview from '../components/ChatAttachmentPreview.vue'
import ChatAttachmentTile from '../components/ChatAttachmentTile.vue'
import VoiceMicVisualizer from '../components/VoiceMicVisualizer.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const input = ref('')
const loading = ref(false)
const streaming = ref(false)
const messages = ref([])
const messagesRef = ref(null)
const inputRef = ref(null)
const agents = ref([])
const selectedAgentId = ref(null)
const skipNextWatch = ref(false)
const loadingHistory = ref(false)
const messagePage = ref(1)
const hasMoreMessages = ref(false)
const loadingOlder = ref(false)
const currentAgent = ref(null)
const chatCapabilities = ref({})
const pendingAttachments = ref([])
const fileInputRef = ref(null)
const uploading = ref(false)
const voiceListening = ref(false)
const voiceInputBase = ref('')
let speechRecognition = null
const attachmentPreviewOpen = ref(false)
const attachmentPreviewAtt = ref(null)
const speakingMsgKey = ref(null)
const currentStatus = ref('')
const lastReplyElapsed = ref(null)
let sendStartTime = 0
const hasStreamContent = ref(false)

// ===== 虚拟滚动 =====
const isNearBottom = ref(true)

const virtualizer = useVirtualizer({
  count: messages.value.length,
  getScrollElement: () => messagesRef.value,
  estimateSize: (index) => {
    const msg = messages.value[index]
    if (!msg) return 80
    if (msg.role === 'user') return 60
    const len = msg.content?.length || 0
    return Math.max(80, Math.min(600, Math.ceil(len / 40) * 22 + 60))
  },
  overscan: 5,
})

watch(() => messages.value.length, (newLen, oldLen) => {
  virtualizer.value.setOptions({
    ...virtualizer.value.options,
    count: newLen,
  })
})

function handleScroll() {
  const el = messagesRef.value
  if (!el) return
  const threshold = 150
  isNearBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < threshold
}

function scrollToBottom() {
  if (!isNearBottom.value) return
  const el = messagesRef.value
  if (!el) return
  nextTick(() => {
    el.scrollTop = el.scrollHeight
  })
}

/** 消息列表中是否已有流式中的助手消息（与占位加载条互斥） */
const hasStreamingAssistantMessage = computed(() =>
  messages.value.some(m => m.role === 'assistant' && m._streaming)
)
const abortController = ref(null)
const toolEvents = ref([])
// 用于存储每条消息的展开状态，key为消息索引，value为Set<refIndex>
const expandedRefsMap = ref(new Map())
/** 对话配置版本：0=暂存草稿，>0=指定已发布版本号 */
const selectedConfigVersion = ref(0)
const configVersionOptions = ref([])

/** 未开多模态但开启文件读取时也应显示附件按钮（仅文档） */
const showFileUploadBtn = computed(() => {
  const c = chatCapabilities.value
  if (!c) return false
  return Boolean(c.allowFileUpload || c.allowDocumentUpload || c.allowMediaUpload)
})
const showVoiceInputBtn = computed(() =>
  Boolean(chatCapabilities.value?.multimodalEnabled && chatCapabilities.value?.enableAudioInput))
const showTtsBtn = computed(() => Boolean(chatCapabilities.value?.enableTts))
const fileAcceptTypes = computed(() => buildFileAcceptTypes(chatCapabilities.value))
const fileUploadHint = computed(() => buildUploadHint(chatCapabilities.value))
const canSend = computed(() =>
  !loading.value && (input.value.trim().length > 0 || pendingAttachments.value.length > 0))

const userInitial = computed(() => {
  const name = userStore.user?.username || userStore.user?.nickname || 'U'
  return name[0].toUpperCase()
})

const sessionId = computed(() => {
  return route.params.sessionId || null
})

const currentWelcomeMessage = computed(() => {
  if (currentAgent.value?.welcomeMessage) {
    return currentAgent.value.welcomeMessage
  }
  return '## 你好，我是 LightBot\n有什么可以帮你的？'
})

const currentRecommendedQuestions = computed(() => {
  if (currentAgent.value?.recommendedQuestions) {
    try {
      const questions = typeof currentAgent.value.recommendedQuestions === 'string'
        ? JSON.parse(currentAgent.value.recommendedQuestions)
        : currentAgent.value.recommendedQuestions
      return Array.isArray(questions) ? questions : []
    } catch { return [] }
  }
  return []
})

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

function autoResize() {
  const el = inputRef.value
  if (el) {
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 200) + 'px'
  }
}

function handleAgentSelect({ key }) {
  selectedAgentId.value = key
  pendingAttachments.value = []
  loadCurrentAgent(key)
  loadAgentConfigVersions(key)
}

function openAttachmentPreview(att) {
  if (!att) return
  if (att.type === 'image' || att.type === 'video') {
    if (!getAttThumbUrl(att) && !att.previewUrl) return
  } else if (att.type === 'document') {
    if (!att.parsedText && !att.previewUrl) {
      message.warning('暂无可预览内容')
      return
    }
  } else {
    return
  }
  attachmentPreviewAtt.value = att
  attachmentPreviewOpen.value = true
}

function messagePlainText(content) {
  if (!content) return ''
  return content
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/[#*_~>[\]()!]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function speakMessage(msg, index) {
  if (!showTtsBtn.value) return
  const text = messagePlainText(msg.content)
  if (!text) return
  if (!window.speechSynthesis) {
    message.warning('当前浏览器不支持语音朗读')
    return
  }
  if (speakingMsgKey.value === index) {
    window.speechSynthesis.cancel()
    speakingMsgKey.value = null
    return
  }
  window.speechSynthesis.cancel()
  const utter = new SpeechSynthesisUtterance(text)
  utter.lang = 'zh-CN'
  utter.rate = 1
  speakingMsgKey.value = index
  utter.onend = () => { speakingMsgKey.value = null }
  utter.onerror = () => { speakingMsgKey.value = null }
  window.speechSynthesis.speak(utter)
}

async function loadChatCapabilities(agentId, configVersion) {
  if (!agentId) {
    chatCapabilities.value = {}
    return
  }
  try {
    const res = await getAgentChatCapabilities(agentId, configVersion ?? 0)
    chatCapabilities.value = res.data || {}
  } catch {
    chatCapabilities.value = {}
  }
  if (!showFileUploadBtn.value) {
    pendingAttachments.value = []
  }
  if (!showVoiceInputBtn.value && voiceListening.value) {
    stopVoiceInput()
  }
}

async function onConfigVersionChange(version) {
  if (loading.value) return
  selectedConfigVersion.value = version
  if (selectedAgentId.value) {
    await loadChatCapabilities(selectedAgentId.value, version)
  }
}

async function loadAgentConfigVersions(agentId) {
  if (!agentId) {
    configVersionOptions.value = []
    selectedConfigVersion.value = 0
    return
  }
  try {
    const opts = [{
      value: 0,
      versionLabel: '暂存草稿',
      selectLabel: '暂存草稿',
      badge: 'draft',
    }]
    const res = await listAgentVersions(agentId)
    const versions = res.data || []
    for (const v of versions) {
      const num = v.version
      if (num == null || num <= 0) continue
      const ver = `v${num}`
      opts.push({
        value: num,
        versionLabel: ver,
        selectLabel: v.current ? `${ver} · 线上` : ver,
        badge: v.current ? 'online' : null,
      })
    }
    configVersionOptions.value = opts
    const currentPublished = versions.find(v => v.current)
    selectedConfigVersion.value = currentPublished?.version ?? 0
    await loadChatCapabilities(agentId, selectedConfigVersion.value)
  } catch {
    configVersionOptions.value = [{
      value: 0,
      versionLabel: '暂存草稿',
      selectLabel: '暂存草稿',
      badge: 'draft',
    }]
    selectedConfigVersion.value = 0
    await loadChatCapabilities(agentId, 0)
  }
}

/**
 * 从消息metadata中解析RAG引用
 */
function getMsgRagRefs(msg) {
  if (!msg.metadata) return []
  try {
    const metadata = typeof msg.metadata === 'string' ? safeJsonParse(msg.metadata) : msg.metadata
    return metadata?.ragReferences || []
  } catch {
    return []
  }
}

/**
 * 判断某个引用是否展开
 */
function isReferenceExpanded(msg, index) {
  const msgIndex = messages.value.indexOf(msg)
  const key = `${msgIndex}-${index}`
  return expandedRefsMap.value.has(key)
}

/**
 * 切换引用展开状态
 */
function toggleReference(msg, index) {
  const msgIndex = messages.value.indexOf(msg)
  const key = `${msgIndex}-${index}`
  const newMap = new Map(expandedRefsMap.value)
  if (newMap.has(key)) {
    newMap.delete(key)
  } else {
    newMap.set(key, true)
  }
  expandedRefsMap.value = newMap
}

function parseAttachmentsFromMetadata(metadata) {
  if (!metadata) return []
  try {
    const meta = typeof metadata === 'string' ? safeJsonParse(metadata) : metadata
    return Array.isArray(meta?.attachments) ? meta.attachments : []
  } catch {
    return []
  }
}

function getMsgAttachments(msg) {
  return msg._attachments || []
}

function getAttThumbUrl(att) {
  if (!att) return ''
  if (att.type === 'image') return att.thumbnailUrl || att.previewUrl || ''
  if (att.type === 'video') return att.thumbnailUrl || ''
  return ''
}

async function enrichMessagesAttachments(msgs) {
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

function parseMessage(m) {
  let toolEvents = []
  let workflowEvents = []
  let toolBlockOffsets = []
  let reasoningContent = ''
  let sensitiveBlock = null
  let attachments = []
  let requestId = null
  if (m.metadata) {
    try {
      const metadata = typeof m.metadata === 'string' ? safeJsonParse(m.metadata) : m.metadata
      if (metadata?.toolEvents) toolEvents = metadata.toolEvents
      if (metadata?.workflowEvents) workflowEvents = metadata.workflowEvents
      if (metadata?.toolBlockOffsets) toolBlockOffsets = metadata.toolBlockOffsets
      if (metadata?.reasoningContent) reasoningContent = metadata.reasoningContent
      if (metadata?.sensitiveBlock) sensitiveBlock = metadata.sensitiveBlock
      if (metadata?.requestId) requestId = metadata.requestId
      attachments = parseAttachmentsFromMetadata(metadata)
    } catch {}
  }
  const roleRaw = m.role?.code || m.role
  const role = roleRaw != null ? String(roleRaw).toLowerCase() : ''
  return {
    role,
    content: m.content,
    metadata: m.metadata,
    _attachments: attachments,
    _toolEvents: toolEvents,
    _workflowEvents: workflowEvents,
    _toolBlockOffsets: toolBlockOffsets,
    _toolBlocksDone: [],
    _toolExpanded: false,
    _toolsDone: true,
    _reasoningContent: reasoningContent,
    _reasoningExpanded: true,
    _reasoningDone: true,
    _sensitiveBlock: sensitiveBlock,
    _requestId: requestId,
  }
}

/** 仅最后一条助手回复可重新生成（其后无用户新消息） */
function canRegenerate(index) {
  if (loading.value || streaming.value) return false
  const msg = messages.value[index]
  if (!msg || msg.role !== 'assistant' || msg._streaming || msg._sensitiveBlock) return false
  return index === messages.value.length - 1
}

async function loadHistory() {
  // 流式对话进行中不加载历史，避免替换 messages 数组破坏 stream 闭包引用
  if (streaming.value) return

  if (!sessionId.value) {
    messages.value = []
    selectedAgentId.value = null
    currentAgent.value = null
    lastReplyElapsed.value = null
    return
  }
  // 切换对话时先清空旧内容，避免旧消息在加载期间残留
  messages.value = []
  lastReplyElapsed.value = null
  loadingHistory.value = true
  messagePage.value = 1
  try {
    // 并行加载消息（第1页）和会话详情
    const [msgRes, sessionRes] = await Promise.all([
      getSessionMessages(sessionId.value, { pageNum: 1, pageSize: 10 }),
      getSession(sessionId.value),
    ])
    const records = msgRes.data?.records || []
    // API 按创建时间倒序返回，前端正序显示（旧→新）
    const parsed = records.reverse().map(m => parseMessage(m))
    await enrichMessagesAttachments(parsed)
    messages.value = parsed
    hasMoreMessages.value = records.length === 10

    // 从会话中恢复 agentId
    const session = sessionRes.data
    if (session?.agentId) {
      selectedAgentId.value = session.agentId
      // 先加载版本列表（会设置 selectedConfigVersion），再加载 agent 详情
      await loadAgentConfigVersions(session.agentId)
      await loadCurrentAgent(session.agentId)
    }
    isNearBottom.value = true
    scrollToBottom()
  } catch (e) {
    messages.value = []
  } finally {
    loadingHistory.value = false
  }
}

async function loadOlderMessages() {
  if (loadingOlder.value || !hasMoreMessages.value || streaming.value) return
  // 新对话时 sessionId 为 null，不应请求
  if (!sessionId.value) return

  const container = messagesRef.value
  const oldScrollHeight = container?.scrollHeight || 0

  loadingOlder.value = true
  try {
    messagePage.value++
    const res = await getSessionMessages(sessionId.value, {
      pageNum: messagePage.value,
      pageSize: 10,
    })
    const records = res.data?.records || []
    if (records.length > 0) {
      const olderMessages = records.reverse().map(m => parseMessage(m))
      await enrichMessagesAttachments(olderMessages)
      messages.value = [...olderMessages, ...messages.value]
      hasMoreMessages.value = records.length === 10
      // 保持滚动位置（虚拟滚动下通过偏移量修正）
      await nextTick()
      if (container) {
        const newScrollHeight = container.scrollHeight
        container.scrollTop = newScrollHeight - oldScrollHeight
      }
    } else {
      hasMoreMessages.value = false
    }
  } catch {
    // 静默失败
  } finally {
    loadingOlder.value = false
  }
}

async function loadCurrentAgent(agentId) {
  if (!agentId) {
    currentAgent.value = null
    chatCapabilities.value = {}
    return
  }
  try {
    const res = await getAgentDetail(agentId)
    currentAgent.value = res.data?.agent || null
    await loadChatCapabilities(agentId, selectedConfigVersion.value)
  } catch {
    currentAgent.value = null
    chatCapabilities.value = {}
  }
}

function triggerFileUpload() {
  fileInputRef.value?.click()
}

async function onFileSelected(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file || !selectedAgentId.value) return

  const countCheck = validateAttachmentCount(pendingAttachments.value.length, chatCapabilities.value)
  if (!countCheck.ok) {
    message.warning(countCheck.message)
    return
  }
  const validation = validateChatAttachmentFile(file, chatCapabilities.value)
  if (!validation.ok) {
    message.warning(validation.message)
    return
  }
  const mixCheck = validateAttachmentMix(pendingAttachments.value, validation.type)
  if (!mixCheck.ok) {
    message.warning(mixCheck.message)
    return
  }

  uploading.value = true
  try {
    const res = await uploadChatAttachment(selectedAgentId.value, sessionId.value, file)
    const att = { ...res.data }
    if (att.type === 'video') {
      try {
        att.thumbnailUrl = await captureVideoThumbnail(file, { maxWidth: 112, maxHeight: 72 })
      } catch {
        if (att.previewUrl) {
          try {
            att.thumbnailUrl = await captureVideoThumbnail(att.previewUrl, { maxWidth: 112, maxHeight: 72 })
          } catch { /* 跨域等 */ }
        }
      }
    }
    pendingAttachments.value.push(att)
  } catch {
    // 业务/网络错误提示由 request 拦截器统一展示，避免重复 toast
  } finally {
    uploading.value = false
  }
}

function removeAttachment(index) {
  pendingAttachments.value.splice(index, 1)
}

function toggleVoiceInput() {
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SR) {
    message.warning('当前浏览器不支持语音识别，请使用 Chrome/Edge')
    return
  }
  if (voiceListening.value && speechRecognition) {
    stopVoiceInput()
    return
  }
  voiceInputBase.value = input.value
  speechRecognition = new SR()
  speechRecognition.lang = 'zh-CN'
  speechRecognition.interimResults = true
  speechRecognition.continuous = true
  speechRecognition.onstart = () => { voiceListening.value = true }
  speechRecognition.onend = () => { stopVoiceInput() }
  speechRecognition.onerror = () => { stopVoiceInput() }
  speechRecognition.onresult = (event) => {
    let finalText = ''
    let interimText = ''
    for (let i = 0; i < event.results.length; i++) {
      const part = event.results[i][0].transcript
      if (event.results[i].isFinal) {
        finalText += part
      } else {
        interimText += part
      }
    }
    const base = voiceInputBase.value
    const merged = `${base}${base && finalText ? ' ' : ''}${finalText}${interimText}`
    input.value = merged.trim() ? merged : base
    autoResize()
    nextTick(() => inputRef.value?.focus())
  }
  speechRecognition.start()
}

function stopVoiceInput() {
  voiceListening.value = false
  try {
    speechRecognition?.stop()
  } catch {
    /* ignore */
  }
}

async function sendMessage() {
  const text = input.value.trim()
  const attachments = [...pendingAttachments.value]
  if ((!text && attachments.length === 0) || loading.value) return
  const mixCheck = validatePendingAttachmentMix(attachments)
  if (!mixCheck.ok) {
    message.warning(mixCheck.message)
    return
  }

  const displayContent = text || (attachments.length ? '[附件]' : '')
  const sentAttachments = attachments.map(a => ({ ...a }))
  await enrichVideoThumbnails(sentAttachments)
  messages.value.push({ role: 'user', content: displayContent, _attachments: sentAttachments })
  input.value = ''
  pendingAttachments.value = []
  autoResize()
  isNearBottom.value = true
  scrollToBottom()

  await runChatStream({
    message: text,
    attachments: sentAttachments,
    regenerate: false,
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
  messages.value.pop()
  isNearBottom.value = true
  scrollToBottom()
  await runChatStream({
    message: userMsg.content === '[附件]' ? '' : (userMsg.content || ''),
    attachments: userMsg._attachments || [],
    regenerate: true,
  })
}

async function runChatStream({ message, attachments, regenerate }) {
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

  try {
    let sid = sessionId.value
    const currentAgentId = selectedAgentId.value

    if (!sid) {
      const res = await createSession(currentAgentId || undefined)
      sid = res.data.id
      skipNextWatch.value = true
      router.replace(`/app/chat/${sid}`)
    }

    const chatPayload = {
      message: message || undefined,
      sessionId: sid,
      agentId: currentAgentId || undefined,
      configVersion: selectedConfigVersion.value ?? 0,
      regenerate: regenerate || undefined,
      attachments: attachments?.length ? attachments.map(a => ({
        id: a.id,
        type: a.type,
        mimeType: a.mimeType,
        objectKey: a.objectKey,
        previewUrl: a.previewUrl,
        fileName: a.fileName,
        parsedText: a.parsedText,
        parsedTextTruncated: a.parsedTextTruncated,
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
        // onChunk: 文本内容
        onChunk: (chunk) => {
          if (!pushed) {
            messages.value.push({ role: 'assistant', content: '', _streaming: true, _toolsDone: false, _toolEvents: [], _workflowEvents: [], _toolBlockOffsets: [], _toolBlocksDone: [], _toolExpanded: false, _reasoningContent: '', _reasoningExpanded: true, _reasoningDone: false })
            assistantMsg = messages.value[messages.value.length - 1]
            attachRequestId(assistantMsg)
            pushed = true
            hasStreamContent.value = true
          }
          assistantMsg.content += chunk
          scrollToBottom()
        },
        // onStatus: 状态消息
        onStatus: (status) => {
          currentStatus.value = status
          scrollToBottom()
        },
        // onToolEvent: 工具调用/结果/状态事件
        onToolEvent: (event) => {
          if (!pushed) {
            messages.value.push({ role: 'assistant', content: '', _streaming: true, _toolsDone: false, _toolEvents: [], _workflowEvents: [], _toolBlockOffsets: [], _toolBlocksDone: [], _toolExpanded: true, _reasoningContent: '', _reasoningExpanded: true, _reasoningDone: false })
            assistantMsg = messages.value[messages.value.length - 1]
            attachRequestId(assistantMsg)
            pushed = true
            hasStreamContent.value = true
          }
          if (event.type === 'tool_complete') {
            const offset = event.contentOffset ?? assistantMsg._currentToolOffset
            markToolBlockDone(assistantMsg, offset)
            return
          }
          if (event.type === 'reasoning_content') {
            assistantMsg._reasoningContent = (assistantMsg._reasoningContent || '') + event.content
            assistantMsg._reasoningDone = true
            scrollToBottom()
            return
          }
          // 敏感词拦截事件：标记消息为拦截状态
          if (event.type === 'sensitive_block') {
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
          // 工作流 LLM 流式输出：逐 token 追加到消息内容
          if (event.type === 'workflow_llm_chunk') {
            if (!pushed) {
              messages.value.push({ role: 'assistant', content: '', _streaming: true, _toolsDone: false, _toolEvents: [], _workflowEvents: [], _toolBlockOffsets: [], _toolBlocksDone: [], _toolExpanded: false, _reasoningContent: '', _reasoningExpanded: true, _reasoningDone: false })
              assistantMsg = messages.value[messages.value.length - 1]
              attachRequestId(assistantMsg)
              pushed = true
              hasStreamContent.value = true
            }
            assistantMsg.content += (event.content || '')
            scrollToBottom()
            return
          }
          // 工作流节点执行事件（实时推送，无需等待最终回复）
          if (event.type === 'workflow_node_start' || event.type === 'workflow_node_complete' || event.type === 'workflow_complete') {
            if (!assistantMsg._workflowEvents) assistantMsg._workflowEvents = []
            assistantMsg._workflowEvents.push(event)
            hasStreamContent.value = true
            if (event.type === 'workflow_node_start') {
              currentStatus.value = `正在执行: ${event.nodeLabel || event.nodeType || '节点'}`
            } else if (event.type === 'workflow_node_complete') {
              const label = event.nodeLabel || event.nodeType || '节点'
              const dur = event.durationMs != null ? ` (${event.durationMs}ms)` : ''
              currentStatus.value = event.success === false
                ? `${label} 执行失败`
                : `${label} 已完成${dur}`
            } else if (event.type === 'workflow_complete') {
              currentStatus.value = '工作流执行完成，正在整理回复…'
            }
            scrollToBottom()
            return
          }

          const offset = event.contentOffset ?? assistantMsg.content.length
          if (event.contentOffset == null) {
            event.contentOffset = offset
          }
          if (event.type === 'skill_active') {
            assistantMsg._toolEvents.push(event)
            hasStreamContent.value = true
            currentStatus.value = `已启用 ${(event.skills || []).length} 个 Skill`
            scrollToBottom()
            return
          }
          if (event.type === 'subagent_call' || event.type === 'subagent_result') {
            assistantMsg._toolEvents.push(event)
            if (event.type === 'subagent_call') {
              assistantMsg._toolExpanded = true
              assistantMsg._currentToolOffset = offset
              registerToolBlockOffset(assistantMsg, offset)
              currentStatus.value = `委派 SubAgent: ${event.displayName || event.subagentName || ''}`
            }
            hasStreamContent.value = true
            scrollToBottom()
            return
          }
          if (event.type === 'tool_call') {
            assistantMsg._toolExpanded = true
            assistantMsg._currentToolOffset = offset
            registerToolBlockOffset(assistantMsg, offset)
          } else if (assistantMsg._currentToolOffset == null || assistantMsg._currentToolOffset < 0) {
            assistantMsg._currentToolOffset = offset
            registerToolBlockOffset(assistantMsg, offset)
          }

          assistantMsg._toolEvents.push(event)
          toolEvents.value.push(event)
          if (event.type === 'tool_status' && event.message) {
            currentStatus.value = event.message
          }
          scrollToBottom()
        },
        // onMetadata: metadata消息（含工具事件与 offset，每轮工具调用后更新）
        onMetadata: (metadataStr) => {
          if (!assistantMsg) return
          applyToolMetadata(assistantMsg, safeJsonParse(metadataStr))
        },
        // onDone: 完成
        onDone: () => {
          if (assistantMsg) {
            if (!assistantMsg._toolBlockOffsets?.length) {
              assistantMsg._toolBlockOffsets = getToolBlockOffsets(assistantMsg)
            }
            assistantMsg._streaming = false
            assistantMsg._toolsDone = true
            assistantMsg._toolExpanded = false
          }
          loading.value = false
          streaming.value = false
          hasStreamContent.value = false
          currentStatus.value = ''
          lastReplyElapsed.value = Date.now() - sendStartTime
          abortController.value = null
          // 轮询等待标题生成完成
          pollSessionTitle(sid)
        },
      },
      abortController.value?.signal
    )
  } catch (e) {
    // 用户主动中断
    if (e.name === 'AbortError') {
      if (!assistantMsg) {
        messages.value.push({ role: 'assistant', content: '', _streaming: false, _toolsDone: true, _toolEvents: [], _workflowEvents: [], _toolBlockOffsets: [], _toolBlocksDone: [], _toolExpanded: false, _reasoningContent: '', _reasoningExpanded: true, _reasoningDone: true })
        assistantMsg = messages.value[messages.value.length - 1]
      }
      assistantMsg.content += '\n\n*AI 输出已终止*'
      assistantMsg._streaming = false
    } else {
      if (!assistantMsg) {
        messages.value.push({ role: 'assistant', content: '', _streaming: false, _toolsDone: true, _toolEvents: [], _workflowEvents: [], _toolBlockOffsets: [], _toolBlocksDone: [], _toolExpanded: false, _reasoningContent: '', _reasoningExpanded: true, _reasoningDone: true })
        assistantMsg = messages.value[messages.value.length - 1]
      }
      assistantMsg.content = 'AI 大模型调用失败，请检查模型配置是否正确。\n\n错误详情：' + (e.message || '未知错误')
      assistantMsg._streaming = false
      assistantMsg._toolsDone = true
    }
    loading.value = false
    streaming.value = false
    hasStreamContent.value = false
    currentStatus.value = ''
    abortController.value = null
  }
}

function copyMessage(msg) {
  navigator.clipboard.writeText(msg.content).then(() => {
    msg._copied = true
    setTimeout(() => { msg._copied = false }, 2000)
  })
}

function copyRequestId(msg) {
  if (!msg._requestId) return
  navigator.clipboard.writeText(msg._requestId).then(() => {
    msg._requestIdCopied = true
    message.success('Request ID 已复制')
    setTimeout(() => { msg._requestIdCopied = false }, 2000)
  })
}

function stopGenerating() {
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
}

function registerToolBlockOffset(msg, offset) {
  if (offset == null || offset < 0) return
  if (!msg._toolBlockOffsets) msg._toolBlockOffsets = []
  if (!msg._toolBlockOffsets.includes(offset)) {
    msg._toolBlockOffsets.push(offset)
    msg._toolBlockOffsets.sort((a, b) => a - b)
  }
}

const CAPABILITY_EVENT_TYPES = new Set(['skill_active', 'subagent_call', 'subagent_result'])

function getCapabilityEvents(msg) {
  return (msg._toolEvents || []).filter(e => CAPABILITY_EVENT_TYPES.has(e.type))
}

function getTopCapabilityEvents(msg) {
  return getCapabilityEvents(msg).filter(e => e.type === 'skill_active')
}

function getCapabilityEventsForOffset(msg, offset) {
  return getCapabilityEvents(msg).filter(e => e.type !== 'skill_active' && e.contentOffset === offset)
}

function getInlineCapabilityEvents(msg) {
  const offsets = getToolBlockOffsets(msg)
  if (offsets.length > 0) return []
  return getCapabilityEvents(msg).filter(e => e.type !== 'skill_active')
}

function getPureToolEvents(events) {
  return (events || []).filter(e => !CAPABILITY_EVENT_TYPES.has(e.type))
}

function getToolBlockOffsets(msg) {
  if (msg._toolBlockOffsets?.length > 0) return msg._toolBlockOffsets
  const fromEvents = [...new Set(
    (msg._toolEvents || [])
      .filter(e => e.type === 'tool_call' || e.type === 'subagent_call')
      .map(e => e.contentOffset)
      .filter(o => o != null && o >= 0)
  )]
  return fromEvents.sort((a, b) => a - b)
}

function getToolEventsForOffset(msg, offset) {
  const events = msg._toolEvents || []
  const matched = events.filter(e => e.contentOffset === offset)
  if (matched.length > 0) return matched
  const offsets = getToolBlockOffsets(msg)
  if (offsets.length === 1 && offsets[0] === offset) {
    return events.filter(e => e.contentOffset == null)
  }
  return matched
}

function isToolBlockDone(msg, offset) {
  if (msg._toolBlocksDone?.includes(offset)) return true
  if (!msg._streaming) return true
  const atOffset = getToolEventsForOffset(msg, offset)
  return atOffset.some(e => e.type === 'tool_result' || e.type === 'subagent_result')
}

function markToolBlockDone(msg, offset) {
  if (offset == null || offset < 0) return
  if (!msg._toolBlocksDone) msg._toolBlocksDone = []
  if (!msg._toolBlocksDone.includes(offset)) {
    msg._toolBlocksDone.push(offset)
  }
}

function applyToolMetadata(msg, meta) {
  if (!meta) return
  msg.metadata = { ...(msg.metadata || {}), ...meta }
  if (meta.toolEvents?.length) {
    msg._toolEvents = meta.toolEvents
  }
  // 流式阶段已实时推送的 workflow 事件不再被 metadata 整体替换，避免节点列表闪跳
  if (meta.workflowEvents?.length && !msg._workflowEvents?.length) {
    msg._workflowEvents = meta.workflowEvents
  }
  if (meta.toolBlockOffsets?.length) {
    msg._toolBlockOffsets = meta.toolBlockOffsets
  }
}

function splitContentByOffsets(msg) {
  const content = msg.content || ''
  const offsets = getToolBlockOffsets(msg)
  if (offsets.length === 0) return [{ type: 'text', text: content }]

  const segments = []
  let lastIdx = 0
  for (const offset of offsets) {
    if (offset > lastIdx && offset <= content.length) {
      segments.push({ type: 'text', text: content.substring(lastIdx, offset) })
    }
    segments.push({ type: 'tool', offset })
    lastIdx = offset
  }
  if (lastIdx < content.length) {
    segments.push({ type: 'text', text: content.substring(lastIdx) })
  }
  return segments
}

function formatElapsed(ms) {
  if (ms < 1000) return `耗时 ${ms}ms`
  return `耗时 ${(ms / 1000).toFixed(1)}s`
}

function goToKnowledge(knowledgeId, documentId) {
  const query = documentId ? { docId: String(documentId) } : {}
  router.push({ path: `/app/knowledge/${knowledgeId}`, query })
}

// scrollToBottom 已在虚拟滚动区域定义

function agentVersionLabel(a) {
  const status = a.status?.code || a.status || 'draft'
  const ver = a.version || 0
  if (status === 'published' && ver > 0) return `v${ver}`
  if (status === 'published_editing' && ver > 0) return `v${ver}·编辑中`
  if (status === 'draft') return '草稿'
  return ''
}

async function loadAgents(preferredAgentId) {
  try {
    const res = await getAgents({ pageNum: 1, pageSize: 100 })
    agents.value = res.data.records || []

    // 新对话时选中 Agent：优先 URL 指定，否则默认 Agent
    if (!sessionId.value) {
      if (preferredAgentId) {
        selectedAgentId.value = String(preferredAgentId)
        await loadAgentConfigVersions(preferredAgentId)
      } else {
        const defaultAgent = agents.value.find(a => a.isDefault)
        if (defaultAgent) {
          selectedAgentId.value = String(defaultAgent.id)
        } else if (agents.value.length > 0) {
          selectedAgentId.value = String(agents.value[0].id)
        }
        if (selectedAgentId.value) {
          await loadAgentConfigVersions(selectedAgentId.value)
        }
      }
    }
  } catch (e) {
    // ignore
  }
}

/** 轮询等待会话标题生成完成（轻量接口，跳过缓存） */
function pollSessionTitle(sid) {
  if (!sid) return
  let count = 0
  const maxRetries = 8
  const interval = 2000
  const timer = setInterval(async () => {
    try {
      const res = await getSessionTitle(sid)
      const title = res.data
      if (title && title !== '新对话') {
        clearInterval(timer)
        window.dispatchEvent(new CustomEvent('session-title-updated'))
      }
    } catch {
      // ignore
    }
    count++
    if (count >= maxRetries) clearInterval(timer)
  }, interval)
}

onUnmounted(() => {
  window.speechSynthesis?.cancel()
  stopVoiceInput()
})

onMounted(async () => {
  const queryAgentId = route.query.agentId
  loadHistory()
  await loadAgents(queryAgentId || undefined)
  if (queryAgentId) {
    await loadCurrentAgent(queryAgentId)
    await loadAgentConfigVersions(queryAgentId)
    router.replace({ path: '/app/chat' })
  } else if (selectedAgentId.value) {
    await loadAgentConfigVersions(selectedAgentId.value)
  }

  // 滚动到顶部自动加载更早的消息 + 虚拟滚动距离检测
  const container = messagesRef.value
  if (container) {
    container.addEventListener('scroll', () => {
      handleScroll()
      if (container.scrollTop < 50 && hasMoreMessages.value && !loadingOlder.value && !streaming.value) {
        loadOlderMessages()
      }
    })
  }
})

watch(() => route.params.sessionId, (newVal, oldVal) => {
  // sendMessage 内部 router.replace 触发的 watcher，跳过避免重新加载消息
  if (skipNextWatch.value) {
    skipNextWatch.value = false
    return
  }
  // 流式对话进行中，中断当前流
  if (streaming.value && abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
  // 切换对话时清空展开状态
  expandedRefsMap.value = new Map()
  loadHistory()
})

// 切换 Agent 时更新欢迎语
watch(selectedAgentId, (newId) => {
  if (newId && !sessionId.value) {
    loadCurrentAgent(newId)
  } else if (!newId) {
    currentAgent.value = null
  }
})

// 新建对话时重新查询 agent 列表，确保默认 agent 被选中
watch(sessionId, (newVal, oldVal) => {
  if (!newVal && oldVal) {
    loadAgents()
  }
})
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #ffffff;
}

/* ===== 消息列表 ===== */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
}
.chat-messages::-webkit-scrollbar {
  width: 6px;
}
.chat-messages::-webkit-scrollbar-thumb {
  background: #d4d4d8;
  border-radius: 3px;
}

.load-more-area {
  text-align: center;
  padding: 12px 0;
}

/* 虚拟滚动容器 */
.virtual-list-container {
  width: 100%;
  max-width: 800px;
  margin: 0 auto;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 40px;
}
.empty-logo {
  height: 64px;
  margin-bottom: 24px;
  object-fit: contain;
}
.welcome-content {
  text-align: center;
  max-width: 600px;
  margin-bottom: 24px;
  font-size: 15px;
  line-height: 1.7;
  color: #171717;
}
.welcome-content :deep(h1),
.welcome-content :deep(h2) {
  font-size: 24px;
  font-weight: 600;
  color: #171717;
  margin-bottom: 8px;
}
.welcome-content :deep(p) {
  margin: 0 0 8px;
  color: #71717a;
}
.recommended-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  max-width: 600px;
}
.btn-question {
  padding: 8px 16px;
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 100px;
  font-size: 13px;
  color: #52525b;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-question:hover {
  border-color: #0070f3;
  color: #0070f3;
  background: #f0f7ff;
}
.no-default-hint {
  font-size: 13px;
  color: #a1a1aa;
  margin-top: 8px;
}
.no-default-hint a {
  color: #0070f3;
  text-decoration: none;
}
.no-default-hint a:hover {
  text-decoration: underline;
}

/* 消息 */
.message {
  padding: 12px 32px;
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}
.message-body {
  min-width: 0;
}
.message.user .message-body {
  text-align: right;
}
.message-meta {
  font-size: 12px;
  color: #a1a1aa;
  margin-bottom: 6px;
}
.message-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}
.btn-action-text {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  font-size: 12px;
}
.btn-action-text span {
  line-height: 1;
}
.message-content-wrapper {
  position: relative;
}
/* 用户消息：附件在上、气泡在下，整体靠右与头像侧对齐 */
.message.user .message-content-wrapper.user-message-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  width: 100%;
}
.message.user .user-message-attachments {
  display: inline-flex;
  flex-direction: row-reverse;
  flex-wrap: wrap-reverse;
  justify-content: flex-end;
  align-items: flex-end;
  gap: 8px;
  margin-bottom: 6px;
  max-width: 80%;
}
.message-content {
  font-size: 15px;
  line-height: 1.7;
  color: #171717;
  word-break: break-word;
}
.message.user .message-content {
  display: inline-block;
  background: #f4f4f5;
  padding: 10px 16px;
  border-radius: 12px 12px 2px 12px;
  text-align: left;
  max-width: 80%;
}
/* 用户消息气泡内紧凑样式 */
.message.user .message-content :deep(.markdown-preview p) {
  margin: 0;
}
.message.user .message-content :deep(.markdown-preview) {
  line-height: 1.5;
}

/* 复制按钮 */
.btn-copy {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 6px;
  padding: 4px 6px;
  background: none;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  color: #a1a1aa;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s, color 0.15s;
}
.message:hover .btn-copy,
.message-actions:hover .btn-copy {
  opacity: 1;
}
.message-actions .btn-copy {
  opacity: 1;
}
.message.assistant .message-actions {
  justify-content: flex-start;
}
.message.user .message-actions {
  justify-content: flex-end;
}
.btn-copy:hover {
  color: #52525b;
}
.btn-copy.copied {
  color: #16a34a;
  opacity: 1;
}

/* Markdown 渲染 */
.message-content :deep(h1),
.message-content :deep(h2),
.message-content :deep(h3),
.message-content :deep(h4),
.message-content :deep(h5),
.message-content :deep(h6) {
  margin: 16px 0 8px;
  font-weight: 600;
  line-height: 1.4;
  color: #171717;
}
.message-content :deep(h1) { font-size: 1.5em; }
.message-content :deep(h2) { font-size: 1.3em; }
.message-content :deep(h3) { font-size: 1.15em; }
.message-content :deep(h4),
.message-content :deep(h5),
.message-content :deep(h6) { font-size: 1em; }
.message-content :deep(p) {
  margin: 0 0 12px;
}
.message-content :deep(code) {
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'Geist Mono', 'Menlo', monospace;
}
.message-content :deep(pre) {
  background: #171717;
  border-radius: 8px;
  padding: 16px;
  overflow-x: auto;
  margin: 12px 0;
}
.message-content :deep(pre code) {
  background: transparent;
  color: #e4e4e7;
  padding: 0;
  font-size: 13px;
  line-height: 1.6;
}
.message-content :deep(ul),
.message-content :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}
.message-content :deep(li > p),
.message-content :deep(ol > p),
.message-content :deep(ul > p) {
  margin: 2px 0;
}
.message-content :deep(li) {
  margin: 2px 0;
}
.message-content :deep(blockquote) {
  border-left: 3px solid #0070f3;
  padding-left: 12px;
  margin: 12px 0;
  color: #52525b;
}
.message-content :deep(table) {
  border-collapse: collapse;
  margin: 12px 0;
  width: 100%;
}
.message-content :deep(th),
.message-content :deep(td) {
  border: 1px solid #e4e4e7;
  padding: 8px 12px;
  text-align: left;
  font-size: 14px;
}
.message-content :deep(th) {
  background: #f4f4f5;
}

/* 输入区 */
.chat-input-wrapper {
  padding: 0 32px 24px;
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}
.chat-input-shell {
  border: 1px solid #e4e4e7;
  border-radius: 12px;
  background: #fff;
  overflow: hidden;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.chat-input-shell:focus-within {
  border-color: #0070f3;
  box-shadow: 0 0 0 3px rgba(0, 112, 243, 0.08);
}
.chat-input-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}
.chat-toolbar-agent-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  color: #3f3f46;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-input {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 8px 8px 8px 4px;
  background: #fff;
}
.chat-input-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

/* Agent 选择按钮 */
.btn-agent {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: none;
  background: #f4f4f5;
  color: #52525b;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 16px;
  transition: background 0.15s;
  overflow: hidden;
}
.btn-agent:hover {
  background: #e4e4e7;
}
.btn-agent-avatar {
  width: 36px;
  height: 36px;
  object-fit: cover;
}
.btn-agent-initial {
  font-size: 14px;
  font-weight: 600;
}

/* Agent 下拉菜单项 */
.agent-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 160px;
}
.agent-menu-icon {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  background: #f4f4f5;
  color: #52525b;
  flex-shrink: 0;
}
.agent-menu-icon.default-icon {
  background: #0070f3;
  color: #fff;
}
.agent-menu-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.agent-version-tag {
  font-size: 10px;
  padding: 1px 6px;
  background: #f4f4f5;
  color: #71717a;
  border-radius: 100px;
  flex-shrink: 0;
}
.agent-default-tag {
  font-size: 10px;
  padding: 1px 6px;
  background: #eff6ff;
  color: #2563eb;
  border-radius: 100px;
  flex-shrink: 0;
}
.empty-agent-tip {
  font-size: 13px;
  color: #52525b;
  white-space: nowrap;
}
.empty-agent-tip a {
  color: #0070f3;
  font-weight: 500;
}

.config-version-select {
  margin-left: auto;
  flex-shrink: 0;
  min-width: 128px;
  max-width: 200px;
}
.version-option-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.version-option-num {
  font-size: 13px;
  color: #171717;
}
.version-status-tag {
  margin: 0;
  font-size: 11px;
  line-height: 18px;
  padding: 0 6px;
}
.version-status-tag.draft {
  background: #f4f4f5;
  color: #52525b;
}
.message-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
}
.btn-copy.speaking {
  color: #0070f3;
}

.input-textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-size: 15px;
  line-height: 1.5;
  font-family: inherit;
  color: #171717;
  background: transparent;
  max-height: 200px;
  min-height: 36px;
  display: flex;
  align-items: center;
}
.input-textarea::placeholder {
  color: #a1a1aa;
}
.hidden-file-input {
  display: none;
}
.btn-attach,
.btn-voice {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: none;
  background: #f4f4f5;
  color: #52525b;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 16px;
}
.btn-attach:hover:not(:disabled),
.btn-voice:hover:not(:disabled) {
  background: #e4e4e7;
}
.btn-attach:disabled,
.btn-voice:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-attach--uploading {
  background: #eff6ff;
  color: #0070f3;
  animation: attach-btn-pulse 1s ease-in-out infinite;
}
@keyframes attach-btn-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(0, 112, 243, 0.35); }
  50% { box-shadow: 0 0 0 6px rgba(0, 112, 243, 0); }
}
.btn-voice.listening {
  background: #fef2f2;
  color: #ef4444;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.25);
}
.voice-listening-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 8px;
  background: #fef2f2;
  border: 1px solid #fecaca;
}
.voice-listening-text {
  font-size: 12px;
  color: #ef4444;
  white-space: nowrap;
  user-select: none;
}
.msg-att-thumb {
  position: relative;
  width: 52px;
  height: 52px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #e4e4e7;
  flex-shrink: 0;
  display: block;
  background: #f4f4f5;
  padding: 0;
  cursor: pointer;
}
.msg-att-hover-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  background: rgba(0, 0, 0, 0.48);
  color: #fff;
  opacity: 0;
  transition: opacity 0.15s ease;
  pointer-events: none;
}
.msg-att-thumb:hover .msg-att-hover-mask,
.att-thumb-wrap:hover .msg-att-hover-mask {
  opacity: 1;
}
.msg-att-hover-mask .mask-icon {
  font-size: 16px;
}
.msg-att-hover-mask .mask-text {
  font-size: 11px;
  line-height: 1;
}
.msg-att-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.msg-att-thumb--video {
  background: #18181b;
}
.msg-att-play-badge {
  position: absolute;
  right: 3px;
  bottom: 3px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.92);
  line-height: 1;
  pointer-events: none;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.45);
}
.msg-att-play-badge.sm {
  font-size: 12px;
}
.msg-att-file-tag {
  font-size: 12px;
  color: #52525b;
  padding: 4px 10px;
  background: #f4f4f5;
  border-radius: 6px;
}
.pending-attachments {
  margin-top: 8px;
}
.pending-att-count {
  font-size: 12px;
  color: #71717a;
  display: block;
  margin-bottom: 6px;
}
.pending-att-thumbs {
  display: flex;
  flex-direction: row-reverse;
  flex-wrap: wrap-reverse;
  justify-content: flex-end;
  gap: 6px;
}
.pending-att-item {
  position: relative;
  flex-shrink: 0;
}
.att-thumb-wrap {
  position: relative;
  width: 48px;
  height: 48px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #e4e4e7;
  padding: 0;
  background: #f4f4f5;
  cursor: pointer;
}
.att-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.pending-att-item .att-name {
  font-size: 12px;
  color: #52525b;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pending-att-item .att-remove {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff;
  border: 1px solid #e4e4e7;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  padding: 0;
  cursor: pointer;
  color: #71717a;
  z-index: 1;
}
.att-remove {
  border: none;
  background: transparent;
  color: #71717a;
  cursor: pointer;
  padding: 0;
  line-height: 1;
}
.btn-send {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: none;
  background: #0070f3;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.15s;
}
.btn-send:hover:not(:disabled) {
  background: #005bc4;
}
.btn-send:disabled {
  background: #d4d4d8;
  cursor: not-allowed;
}
.btn-stop {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: none;
  background: #ef4444;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.15s;
  font-size: 18px;
}
.btn-stop:hover {
  background: #dc2626;
}

/* 工具块内联容器 */
.tool-block-inline {
  margin: 8px 0;
}

.workflow-block-inline {
  margin: 8px 0;
}

/* 深度思考面板 */
.reasoning-panel {
  margin-bottom: 8px;
  border: 1px solid #fef3c7;
  border-radius: 8px;
  overflow: hidden;
}
.reasoning-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #fefce8;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  color: #a16207;
  transition: background 0.15s;
}
.reasoning-header:hover {
  background: #fef9c3;
}
.reasoning-icon {
  color: #eab308;
  font-size: 14px;
}
.reasoning-title {
  font-weight: 500;
}
.reasoning-spinner {
  color: #eab308;
  font-size: 12px;
  animation: spin 1s linear infinite;
}
.reasoning-header .tool-expand-icon {
  margin-left: auto;
  font-size: 12px;
  color: #ca8a04;
  transition: transform 0.2s ease;
}
.reasoning-header .tool-expand-icon.expanded {
  transform: rotate(90deg);
}
.reasoning-content {
  padding: 10px 12px;
  background: #fffbeb;
  font-size: 13px;
  color: #71717a;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 300px;
  overflow-y: auto;
}

/* 敏感词拦截提示 */
.sensitive-block-alert {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.6;
  animation: fadeIn 0.3s ease;
}
.sensitive-block-alert.user_input {
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #991b1b;
}
.sensitive-block-alert.user_input .sensitive-block-icon {
  color: #ef4444;
}
.sensitive-block-alert.ai_output {
  background: #fff7ed;
  border: 1px solid #fed7aa;
  color: #9a3412;
}
.sensitive-block-alert.ai_output .sensitive-block-icon {
  color: #f97316;
}
.sensitive-block-icon {
  font-size: 16px;
  flex-shrink: 0;
  margin-top: 2px;
}
.sensitive-block-text {
  flex: 1;
}
.input-hint {
  text-align: center;
  font-size: 12px;
  color: #a1a1aa;
  margin-top: 8px;
}
/* RAG 引用样式 */
.rag-references {
  margin-top: 12px;
  padding: 12px;
  background: #f9fafb;
  border-radius: 8px;
  border: 1px solid #e4e4e7;
}
.rag-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: #52525b;
  margin-bottom: 8px;
}
.rag-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.rag-item {
  background: #fff;
  border-radius: 6px;
  border: 1px solid #e4e4e7;
  overflow: hidden;
}
.rag-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 0.15s;
}
.rag-item-header:hover {
  background: #f4f4f5;
}
.rag-item-header .anticon {
  font-size: 10px;
  color: #71717a;
  transition: transform 0.2s;
}
.rag-item-header .anticon.expanded {
  transform: rotate(90deg);
}
.rag-doc-name {
  flex: 1;
  font-size: 13px;
  color: #3f3f46;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rag-qa-tag {
  flex-shrink: 0;
  font-size: 12px;
}
.rag-score {
  font-size: 12px;
  color: #10b981;
  font-weight: 500;
}
.rag-nav-btn {
  font-size: 12px;
  color: #6366f1;
  margin-left: 4px;
  cursor: pointer;
  transition: color 0.2s;
}
.rag-nav-btn:hover {
  color: #4f46e5;
}
.rag-item-content {
  padding: 12px;
  background: #f9fafb;
  border-top: 1px solid #e4e4e7;
  font-size: 12px;
  color: #71717a;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}

/* 状态加载样式 */
.status-content {
  display: flex;
  align-items: center;
}
.status-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #f4f4f5;
  border-radius: 12px;
  animation: fadeIn 0.3s ease;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}
.status-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid #e4e4e7;
  border-top-color: #0070f3;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.status-text {
  font-size: 13px;
  color: #52525b;
}

/* 耗时显示 */
.reply-elapsed {
  margin-top: 8px;
  font-size: 12px;
  color: #a1a1aa;
}
</style>

<style>
.chat-upload-tooltip .chat-upload-hint {
  display: block;
  white-space: pre-line;
  line-height: 1.5;
}
</style>
