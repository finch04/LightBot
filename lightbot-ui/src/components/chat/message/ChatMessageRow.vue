<template>
  <div
    v-if="msg?._businessPageCallback"
    class="message business-page-callback"
  >
    <div class="business-page-callback-tip">
      {{ callbackTipText }}
    </div>
  </div>
  <div v-else :class="['message', msg?.role, { 'message-highlight': highlightMessageId === msg?._id }]">
    <div class="message-body">
      <!-- 编辑模式：独立于 message-content-wrapper，占满整行 -->
      <ChatMessageEditBar
        v-if="viewState.mode === 'edit'"
        ref="editBarRef"
        :edit-content="editContent"
        :edit-mentions="editMentions"
        :selected-agent-id="selectedAgentId"
        :selected-agent-version-id="selectedAgentVersionId"
        :loading="loading"
        @cancel-edit="$emit('cancel-edit')"
        @submit-edit="$emit('submit-edit')"
        @update:edit-content="$emit('update:editContent', $event)"
      />
      <div v-else class="message-content-wrapper" :class="{ 'user-message-stack': msg?.role === 'user' }">
        <ChatReplyQuote
          v-if="viewState.showReplyQuote"
          :reply-to-info="replyToInfo"
          :has-reply-target="hasReplyTargetVal"
          :reply-to-message-id="msg._replyToMessageId"
          @scroll-to-reply="$emit('scroll-to-reply', $event)"
        />
        <ChatMessageBody
          :msg="msg"
          :index="index"
          :loading="loading"
          :selected-agent-id="selectedAgentId"
          :selected-agent-version-id="selectedAgentVersionId"
          :get-att-thumb-url="getAttThumbUrl"
          @preview-attachment="$emit('preview-attachment', $event)"
          @height-change="$emit('height-change', $event)"
          @workflow-confirm-submit="$emit('workflow-confirm-submit', $event)"
          @workflow-confirm-abandon="$emit('workflow-confirm-abandon')"
          @reasoning-toggle="$emit('reasoning-toggle')"
        />
        <ChatMessageErrors v-if="viewState.showErrors" :msg="msg" />
        <ChatMessageActions
          v-if="viewState.showActions"
          :msg="msg"
          :index="index"
          :loading="loading"
          :can-regenerate="canRegenerate"
          :is-last-user-message="isLastUserMessage"
          :speaking-msg-key="speakingMsgKey"
          :show-tts-btn="showTtsBtn"
          :feedback-type="feedbackType"
          @copy="$emit('copy')"
          @regenerate="$emit('regenerate')"
          @edit="$emit('edit')"
          @reply="$emit('reply')"
          @delete="$emit('delete')"
          @star="$emit('star')"
          @feedback-like="$emit('feedback-like')"
          @show-dislike="$emit('show-dislike')"
          @speak="$emit('speak')"
          @view-raw="$emit('view-raw')"
          @copy-request-id="$emit('copy-request-id')"
          @copy-message-id="$emit('copy-message-id')"
        />
      </div>
      <ChatRagReferences
        v-if="viewState.showRagRefs || viewState.showReplyElapsed"
        :msg="msg"
        :rag-refs="ragRefs"
        :refs-section-expanded="refsSectionExpanded"
        :is-ref-expanded="isRefExpanded"
        :show-reply-elapsed="viewState.showReplyElapsed"
        :last-reply-elapsed="lastReplyElapsed"
        @rag-toggle="$emit('rag-toggle', $event)"
        @go-knowledge="$emit('go-knowledge', $event)"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { buildMessageViewState } from '../../../utils/chat/messageViewState.js'
import {
  getReplyToInfo,
  hasReplyTarget,
  getMsgRagRefs,
  getMsgMentions,
  isMessageEditing,
} from '../../../composables/chat/useChatMessageModel.js'
import ChatMessageEditBar from './ChatMessageEditBar.vue'
import ChatReplyQuote from './ChatReplyQuote.vue'
import ChatMessageBody from './ChatMessageBody.vue'
import ChatMessageErrors from './ChatMessageErrors.vue'
import ChatMessageActions from './ChatMessageActions.vue'
import ChatRagReferences from './ChatRagReferences.vue'

const props = defineProps({
  msg: { type: Object, required: true },
  index: { type: Number, required: true },
  loading: { type: Boolean, default: false },
  streaming: { type: Boolean, default: false },
  editingMessageId: { type: [String, Number], default: null },
  editContent: { type: String, default: '' },
  highlightMessageId: { type: [String, Number], default: null },
  feedbackType: { type: String, default: null },
  canRegenerate: { type: Boolean, default: false },
  isLastUserMessage: { type: Boolean, default: false },
  speakingMsgKey: { type: [Number, String], default: null },
  showTtsBtn: { type: Boolean, default: false },
  lastReplyElapsed: { type: Number, default: null },
  selectedAgentId: { type: [String, Number], default: null },
  selectedAgentVersionId: { type: [String, Number], default: null },
  getAttThumbUrl: { type: Function, required: true },
  messages: { type: Array, default: () => [] },
  messagesLength: { type: Number, default: 0 },
  refsSectionExpanded: { type: Boolean, default: true },
  isRefExpanded: { type: Function, default: () => () => false },
})

defineEmits([
  'cancel-edit',
  'submit-edit',
  'update:editContent',
  'scroll-to-reply',
  'preview-attachment',
  'height-change',
  'copy',
  'regenerate',
  'edit',
  'reply',
  'delete',
  'star',
  'feedback-like',
  'feedback-dislike',
  'show-dislike',
  'speak',
  'view-raw',
  'copy-request-id',
  'copy-message-id',
  'workflow-confirm-submit',
  'workflow-confirm-abandon',
  'rag-toggle',
  'reasoning-toggle',
  'go-knowledge',
])

const viewState = computed(() => buildMessageViewState(props.msg, {
  index: props.index,
  editingMessageId: props.editingMessageId,
  messages: props.messages,
  messagesLength: props.messagesLength,
  lastReplyElapsed: props.lastReplyElapsed,
  getMsgRagRefs,
  isMessageEditing: (msg, index) => isMessageEditing(msg, index, props.editingMessageId),
}))

const replyToInfo = computed(() => getReplyToInfo(props.msg, props.messages))

const hasReplyTargetVal = computed(() => hasReplyTarget(props.msg, props.messages))

const ragRefs = computed(() => getMsgRagRefs(props.msg))

const editMentions = computed(() => getMsgMentions(props.msg))

const callbackTipText = computed(() => {
  const status = props.msg?._businessPageCallbackStatus
  const pageType = props.msg?._businessPageCallbackPageType
  if (status === 'cancelled') {
    return pageType ? `已取消业务办理（${pageType}）` : '已取消业务办理'
  }
  return pageType ? `已提交业务办理（${pageType}）` : '已提交业务办理'
})

const editBarRef = ref(null)

function getEditMentionInput() {
  const raw = editBarRef.value?.editInputRef
  if (Array.isArray(raw)) return raw.find(Boolean) || null
  return raw?.value ?? raw ?? null
}

defineExpose({ getEditMentionInput })
</script>

<style scoped>
.message {
  padding: 12px 32px;
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}
.message-body {
  min-width: 0;
  width: 100%;
}
.message-content-wrapper {
  position: relative;
  width: 100%;
}
.message-content-wrapper.user-message-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  width: 100%;
}
.message-highlight {
  animation: reply-highlight-flash 2s ease-out;
}
@keyframes reply-highlight-flash {
  0% { background: rgba(0, 112, 243, 0.18); }
  30% { background: rgba(0, 112, 243, 0.12); }
  100% { background: transparent; }
}
.business-page-callback {
  display: flex;
  justify-content: center;
  padding: 4px 32px 8px;
}
.business-page-callback-tip {
  font-size: 12px;
  line-height: 1.4;
  color: var(--text-tertiary, #8c8c8c);
  background: var(--bg-secondary, rgba(0, 0, 0, 0.04));
  border-radius: 999px;
  padding: 4px 12px;
}
</style>

<style>
.message.user .message-body {
  text-align: right;
}
.message.user .edit-message-outer {
  text-align: left;
}
.message:hover .btn-copy {
  opacity: 1;
}
.message-actions:hover .btn-copy {
  opacity: 1;
}
</style>
