<template>
  <div class="chat-input-wrapper">
    <div class="chat-input-shell">
      <!-- 切换会话加载遮罩 -->
      <div v-if="switchingSession" class="toolbar-loading-mask">
        <LoadingOutlined spin class="toolbar-loading-icon" />
      </div>
      <ChatInputToolbar
        :agents="agents"
        :selected-agent-id="selectedAgentId"
        :current-agent="currentAgent"
        :config-version-options="configVersionOptions"
        :selected-config-version="selectedConfigVersion"
        :session-token-count="sessionTokenCount"
        :loading="loading"
        @agent-select="$emit('agent-select', $event)"
        @config-version-change="$emit('config-version-change', $event)"
      />
      <ChatReplyPreviewBar
        :reply-to="replyTo"
        @cancel-reply="$emit('cancel-reply')"
      />
      <div v-if="workflowConfirmBlocked" class="workflow-confirm-send-block">
        <PauseCircleOutlined class="workflow-confirm-send-icon" />
        <span>等待人工确认中，请先完成上方表单后再发送新消息</span>
      </div>
      <div v-if="askUserBlocked && !workflowConfirmBlocked" class="workflow-confirm-send-block">
        <PauseCircleOutlined class="workflow-confirm-send-icon" />
        <span>AI 正等待您完成上方提问，请提交回答后再继续对话</span>
      </div>
      <div v-if="readOnly" class="workflow-confirm-send-block">
        <PauseCircleOutlined class="workflow-confirm-send-icon" />
        <span>此为 API 集成 / 自动化会话，仅供排障只读；请在「会话管理」查看详情</span>
      </div>
      <div class="chat-input">
        <input
          ref="imageInputRef"
          type="file"
          class="hidden-file-input"
          :accept="imageAcceptTypes"
          @change="onFileSelected"
        />
        <input
          ref="fileInputRef"
          type="file"
          class="hidden-file-input"
          :accept="documentAcceptTypes || fileAcceptTypes"
          @change="onFileSelected"
        />
        <a-popover
          v-if="showFileUploadBtn"
          trigger="click"
          placement="topLeft"
          overlay-class-name="chat-attach-popover"
          v-model:open="attachMenuOpen"
        >
          <template #content>
            <div class="attach-menu">
              <button
                v-if="showImageOption"
                type="button"
                class="attach-menu-item"
                @click="onPickImage"
              >
                <PictureOutlined class="attach-menu-icon" />
                <span class="attach-menu-body">
                  <span class="attach-menu-title">上传图片</span>
                  <span class="attach-menu-desc">{{ imageOptionDesc }}</span>
                </span>
              </button>
              <button
                v-if="showDocumentOption"
                type="button"
                class="attach-menu-item"
                @click="onPickFile"
              >
                <PaperClipOutlined class="attach-menu-icon" />
                <span class="attach-menu-body">
                  <span class="attach-menu-title">添加附件</span>
                  <span class="attach-menu-desc">{{ documentOptionDesc }}</span>
                </span>
              </button>
            </div>
          </template>
          <button
            type="button"
            class="btn-attach"
            :class="{ 'btn-attach--uploading': uploading }"
            :disabled="readOnly || loading || uploading || workflowConfirmBlocked || askUserBlocked"
          >
            <LoadingOutlined v-if="uploading" spin />
            <PaperClipOutlined v-else />
          </button>
        </a-popover>
        <ChatMentionInput
          ref="inputRef"
          :model-value="input"
          :agent-id="selectedAgentId"
          :agent-version-id="selectedAgentVersionId"
          :disabled="readOnly || loading || workflowConfirmBlocked || askUserBlocked"
          :placeholder="readOnly ? '只读排障模式，不可发送' : inputPlaceholder"
          @update:model-value="$emit('update:input', $event)"
          @send="$emit('send')"
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
              :disabled="readOnly || loading"
              @click="$emit('toggle-voice')"
            >
              <AudioOutlined />
            </button>
          </a-tooltip>
          <button
            v-if="loading"
            class="btn-stop"
            @click="$emit('stop')"
            title="停止生成"
          >
            <PauseCircleOutlined />
          </button>
          <button
            v-else
            class="btn-send"
            :disabled="readOnly || !canSend || askUserBlocked"
            @click="$emit('send')"
          >
            <SendOutlined />
          </button>
        </div>
      </div>
    </div>
    <ChatPendingAttachments
      :uploading="uploading"
      :pending-attachments="pendingAttachments"
      :get-att-thumb-url="getAttThumbUrl"
      @remove-attachment="$emit('remove-attachment', $event)"
      @preview="$emit('attachment-preview', $event)"
    />
    <ChatInputHint
      :show-input-disclaimer="showInputDisclaimer"
      :input-hint-questions="inputHintQuestions"
      :question-rotate-index="questionRotateIndex"
      @apply-question="$emit('apply-question', $event)"
    />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import {
  LoadingOutlined,
  PaperClipOutlined,
  PictureOutlined,
  AudioOutlined,
  SendOutlined,
  PauseCircleOutlined,
} from '@ant-design/icons-vue'
import ChatMentionInput from '../../ChatMentionInput.vue'
import VoiceMicVisualizer from '../../VoiceMicVisualizer.vue'
import ChatInputToolbar from './ChatInputToolbar.vue'
import ChatReplyPreviewBar from './ChatReplyPreviewBar.vue'
import ChatPendingAttachments from './ChatPendingAttachments.vue'
import ChatInputHint from './ChatInputHint.vue'

const props = defineProps({
  input: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  canSend: { type: Boolean, default: false },
  workflowConfirmBlocked: { type: Boolean, default: false },
  askUserBlocked: { type: Boolean, default: false },
  /** 企业 API / 自动化会话只读排障 */
  readOnly: { type: Boolean, default: false },
  switchingSession: { type: Boolean, default: false },
  agents: { type: Array, default: () => [] },
  selectedAgentId: { type: [String, Number], default: null },
  selectedAgentVersionId: { type: [String, Number], default: null },
  currentAgent: { type: Object, default: null },
  configVersionOptions: { type: Array, default: () => [] },
  selectedConfigVersion: { type: [String, Number], default: 0 },
  sessionTokenCount: { type: Number, default: 0 },
  showFileUploadBtn: { type: Boolean, default: false },
  fileUploadHint: { type: String, default: '' },
  imageUploadHint: { type: String, default: '' },
  fileAcceptTypes: { type: String, default: '' },
  imageAcceptTypes: { type: String, default: '' },
  documentAcceptTypes: { type: String, default: '' },
  uploading: { type: Boolean, default: false },
  showVoiceInputBtn: { type: Boolean, default: false },
  voiceListening: { type: Boolean, default: false },
  replyTo: {
    type: Object,
    default: () => ({ active: false, content: '' }),
  },
  pendingAttachments: { type: Array, default: () => [] },
  getAttThumbUrl: { type: Function, default: () => '' },
  showInputDisclaimer: { type: Boolean, default: true },
  inputHintQuestions: { type: Array, default: () => [] },
  questionRotateIndex: { type: Number, default: 0 },
})

const emit = defineEmits([
  'update:input',
  'send',
  'stop',
  'agent-select',
  'config-version-change',
  'trigger-file-upload',
  'file-selected',
  'remove-attachment',
  'attachment-preview',
  'toggle-voice',
  'cancel-reply',
  'apply-question',
])

const fileInputRef = ref(null)
const imageInputRef = ref(null)
const inputRef = ref(null)
const attachMenuOpen = ref(false)

const showImageOption = computed(() => Boolean(props.imageAcceptTypes))
const showDocumentOption = computed(() => Boolean(props.documentAcceptTypes || props.fileAcceptTypes))
const documentOptionDesc = computed(() => props.fileUploadHint || '上传文档、视频等附件')
const imageOptionDesc = computed(() => {
  const limit = props.imageUploadHint ? `（${props.imageUploadHint}）` : ''
  return `上传图片${limit}`
})

const inputPlaceholder = computed(() => {
  if (props.workflowConfirmBlocked) return '请先完成工作流人工确认表单…'
  if (props.askUserBlocked) return '请先完成 AI 提问'
  return '输入消息... (Enter 发送, Shift+Enter 换行, @ 提及资源)'
})

function onPickImage() {
  attachMenuOpen.value = false
  emit('trigger-file-upload')
  imageInputRef.value?.click()
}

function onPickFile() {
  attachMenuOpen.value = false
  emit('trigger-file-upload')
  fileInputRef.value?.click()
}

function onFileSelected(e) {
  emit('file-selected', e)
}

defineExpose({
  focusInput: () => inputRef.value?.focus(),
  inputRef,
  fileInputRef,
})
</script>

<style scoped>
.workflow-confirm-send-block {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--color-warning-deep);
  background: var(--color-warn-bg);
  border: 1px solid var(--color-warning-soft);
}
.workflow-confirm-send-icon {
  flex-shrink: 0;
  color: var(--color-warning);
}
.chat-input-wrapper {
  padding: 0 32px 24px;
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}
.chat-input-shell {
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  background: var(--color-canvas);
  overflow: hidden;
  transition: border-color 0.15s, box-shadow 0.15s;
  position: relative;
}
.chat-input-shell:focus-within {
  border-color: var(--color-link);
  box-shadow: 0 0 0 3px rgba(0, 112, 243, 0.08);
}
.toolbar-loading-mask {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
  border-radius: inherit;
}
.toolbar-loading-icon {
  font-size: 16px;
  color: var(--color-link);
}
.chat-input {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 8px 8px 8px 4px;
  background: var(--color-canvas);
}
.chat-input-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.hidden-file-input {
  display: none;
}
.attach-menu {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 220px;
}
.attach-menu-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s;
}
.attach-menu-item:hover {
  background: var(--color-canvas-soft-2);
}
.attach-menu-icon {
  font-size: 16px;
  color: var(--color-link);
  margin-top: 2px;
  flex-shrink: 0;
}
.attach-menu-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.attach-menu-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-ink);
}
.attach-menu-desc {
  font-size: 11px;
  color: var(--color-mute);
  line-height: 1.4;
  white-space: pre-line;
}
.btn-attach,
.btn-voice {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: none;
  background: var(--color-canvas-soft-2);
  color: var(--color-mute);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 16px;
}
.btn-attach:hover:not(:disabled),
.btn-voice:hover:not(:disabled) {
  background: var(--color-hairline);
}
.btn-attach:disabled,
.btn-voice:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-attach--uploading {
  background: var(--color-info-bg);
  color: var(--color-link);
  animation: attach-btn-pulse 1s ease-in-out infinite;
}
@keyframes attach-btn-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(0, 112, 243, 0.35); }
  50% { box-shadow: 0 0 0 6px rgba(0, 112, 243, 0); }
}
.btn-voice.listening {
  background: var(--color-error-bg);
  color: #ef4444;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.25);
}
.voice-listening-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 8px;
  background: var(--color-error-bg);
  border: 1px solid var(--color-error-soft);
}
.voice-listening-text {
  font-size: 12px;
  color: #ef4444;
  white-space: nowrap;
  user-select: none;
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
  background: var(--color-hairline-strong);
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
</style>

<style>
[data-theme="dark"] .toolbar-loading-mask {
  background: rgba(24, 24, 27, 0.7);
}
[data-theme="dark"] .chat-input-shell:focus-within {
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.2);
}
</style>
