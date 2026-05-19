<template>
  <div class="chat-container">
    <!-- 消息列表 -->
    <div class="chat-messages" ref="messagesRef">
      <!-- 空状态 -->
      <div v-if="messages.length === 0" class="empty-state">
        <img src="/lightbot-logo.svg" alt="LightBot" class="empty-logo" />
        <h2 class="empty-title">你好，我是 LightBot</h2>
        <p class="empty-desc">基于通义千问大模型的智能助手，有什么可以帮你的？</p>
      </div>

      <!-- 消息列表 -->
      <div v-for="(msg, i) in messages" :key="i" :class="['message', msg.role]">
        <div class="message-avatar">
          <span v-if="msg.role === 'user'">{{ userInitial }}</span>
          <span v-else class="bot-avatar">LB</span>
        </div>
        <div class="message-body">
          <div class="message-meta">{{ msg.role === 'user' ? '你' : 'LightBot' }}</div>
          <div class="message-content" v-html="renderMarkdown(msg.content)" />
        </div>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="message assistant">
        <div class="message-avatar"><span class="bot-avatar">LB</span></div>
        <div class="message-body">
          <div class="message-meta">LightBot</div>
          <div class="message-content">
            <span class="typing-cursor">|</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="chat-input-wrapper">
      <div class="chat-input">
        <textarea
          ref="inputRef"
          v-model="input"
          class="input-textarea"
          placeholder="输入消息... (Enter 发送, Shift+Enter 换行)"
          rows="1"
          @keydown="handleKeydown"
          @input="autoResize"
        />
        <button
          class="btn-send"
          :disabled="!input.trim() || loading"
          @click="sendMessage"
        >
          <el-icon v-if="!loading"><Promotion /></el-icon>
          <span v-else class="sending-dot">...</span>
        </button>
      </div>
      <div class="input-hint">LightBot 可能会犯错，请核实重要信息。</div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import { Promotion } from '@element-plus/icons-vue'
import { chatStream } from '../api/chat'
import { getSessionMessages } from '../api/chatSession'
import { useUserStore } from '../stores/user'

const route = useRoute()
const userStore = useUserStore()

const input = ref('')
const loading = ref(false)
const messages = ref([])
const messagesRef = ref(null)
const inputRef = ref(null)

const userInitial = computed(() => {
  const name = userStore.user?.nickname || userStore.user?.username || 'U'
  return name[0].toUpperCase()
})

const sessionId = computed(() => {
  const id = route.params.sessionId
  return id ? Number(id) : null
})

// 配置 marked
marked.setOptions({
  highlight: (code, lang) => {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  },
  breaks: true,
  gfm: true,
})

function renderMarkdown(text) {
  if (!text) return ''
  return marked.parse(text)
}

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

async function loadHistory() {
  if (!sessionId.value) {
    messages.value = []
    return
  }
  try {
    const res = await getSessionMessages(sessionId.value)
    messages.value = (res.data || []).map(m => ({
      role: m.role?.code || m.role,
      content: m.content,
    }))
    scrollToBottom()
  } catch (e) {
    messages.value = []
  }
}

async function sendMessage() {
  const text = input.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', content: text })
  input.value = ''
  loading.value = true
  autoResize()
  scrollToBottom()

  const assistantMsg = { role: 'assistant', content: '' }
  messages.value.push(assistantMsg)

  try {
    await chatStream(
      { message: text, sessionId: sessionId.value },
      (chunk) => {
        assistantMsg.content += chunk
        scrollToBottom()
      },
      () => {
        loading.value = false
      }
    )
  } catch (e) {
    assistantMsg.content = '请求失败：' + (e.message || '未知错误')
    loading.value = false
  }
}

function scrollToBottom() {
  nextTick(() => {
    const el = messagesRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

onMounted(() => {
  loadHistory()
})

watch(() => route.params.sessionId, () => {
  loadHistory()
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

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 40px;
}
.empty-logo {
  width: 64px;
  height: 64px;
  margin-bottom: 24px;
  opacity: 0.6;
}
.empty-title {
  font-size: 24px;
  font-weight: 600;
  color: #171717;
  margin-bottom: 8px;
}
.empty-desc {
  font-size: 15px;
  color: #71717a;
}

/* 消息 */
.message {
  display: flex;
  gap: 16px;
  padding: 16px 32px;
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}
.message.user {
  flex-direction: row-reverse;
}
.message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
  background: #f4f4f5;
  color: #171717;
}
.bot-avatar {
  background: #0070f3;
  color: #fff;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
}
.message-body {
  flex: 1;
  min-width: 0;
}
.message.user .message-body {
  text-align: right;
}
.message-meta {
  font-size: 12px;
  color: #a1a1aa;
  margin-bottom: 4px;
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

/* Markdown 渲染 */
.message-content :deep(p) {
  margin: 0 0 12px;
}
.message-content :deep(p:last-child) {
  margin-bottom: 0;
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
.chat-input {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  border: 1px solid #e4e4e7;
  border-radius: 12px;
  padding: 8px 8px 8px 16px;
  background: #fff;
  transition: border-color 0.15s;
}
.chat-input:focus-within {
  border-color: #0070f3;
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
}
.input-textarea::placeholder {
  color: #a1a1aa;
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
.input-hint {
  text-align: center;
  font-size: 12px;
  color: #a1a1aa;
  margin-top: 8px;
}
.typing-cursor {
  animation: blink 0.8s infinite;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style>
