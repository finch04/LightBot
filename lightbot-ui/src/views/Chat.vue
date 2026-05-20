<template>
  <div class="chat-container">
    <!-- 消息列表 -->
    <div class="chat-messages" ref="messagesRef">
      <!-- 欢迎状态（新对话 + 无消息） -->
      <div v-if="!sessionId && messages.length === 0 && !loadingHistory" class="empty-state">
        <img src="/lightbot-logo.png" alt="LightBot" class="empty-logo" />
        <div class="welcome-content" v-html="renderMarkdown(currentWelcomeMessage)"></div>
        <!-- 推荐问题 -->
        <div v-if="currentRecommendedQuestions.length > 0" class="recommended-questions">
          <button
            v-for="(q, i) in currentRecommendedQuestions"
            :key="i"
            class="btn-question"
            @click="input = q; $nextTick(() => $refs.inputRef?.focus())"
          >
            {{ q }}
          </button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div v-for="(msg, i) in messages" :key="i" :class="['message', msg.role]">
        <div class="message-avatar">
          <span v-if="msg.role === 'user'">{{ userInitial }}</span>
          <img v-else-if="currentAgent?.avatar" :src="`http://localhost:9000/lightbot/${currentAgent.avatar}`" alt="" class="bot-avatar-img" @error="currentAgent.avatar = ''" />
          <span v-else class="bot-avatar">LB</span>
        </div>
        <div class="message-body">
          <div class="message-meta">
            {{ msg.role === 'user' ? '你' : 'LightBot' }}
          </div>
          <div class="message-content-wrapper">
            <div class="message-content" v-html="renderMarkdown(msg.content)" />
            <!-- 复制按钮（仅AI消息） -->
            <button
              v-if="msg.role === 'assistant' && msg.content"
              class="btn-copy"
              :class="{ copied: msg._copied }"
              @click="copyMessage(msg)"
              :title="msg._copied ? '已复制' : '复制'"
            >
              <CheckOutlined v-if="msg._copied" />
              <CopyOutlined v-else />
            </button>
          </div>
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
      <!-- Agent 选择器 -->
      <div class="agent-selector">
        <span class="agent-label">Agent</span>
        <a-select
          v-model:value="selectedAgentId"
          placeholder="默认 Agent"
          allow-clear
          size="small"
          class="agent-select"
        >
          <a-select-option v-for="a in agents" :key="a.id" :value="a.id">
            {{ a.name }}
          </a-select-option>
        </a-select>
      </div>
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
          <SendOutlined v-if="!loading" />
          <span v-else class="sending-dot">...</span>
        </button>
      </div>
      <div class="input-hint">LightBot 可能会犯错，请核实重要信息。</div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import { SendOutlined, CopyOutlined, CheckOutlined } from '@ant-design/icons-vue'
import { chatStream } from '../api/chat'
import { getSessionMessages, getSession, createSession } from '../api/chatSession'
import { getAgents, getAgent } from '../api/agent'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const input = ref('')
const loading = ref(false)
const messages = ref([])
const messagesRef = ref(null)
const inputRef = ref(null)
const agents = ref([])
const selectedAgentId = ref(null)
const skipNextWatch = ref(false)
const loadingHistory = ref(false)
const currentAgent = ref(null)

const userInitial = computed(() => {
  const name = userStore.user?.nickname || userStore.user?.username || 'U'
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
    currentAgent.value = null
    return
  }
  loadingHistory.value = true
  try {
    // 并行加载消息和会话详情
    const [msgRes, sessionRes] = await Promise.all([
      getSessionMessages(sessionId.value),
      getSession(sessionId.value),
    ])
    messages.value = (msgRes.data || []).map(m => ({
      role: m.role?.code || m.role,
      content: m.content,
    }))

    // 从会话中恢复 agentId
    const session = sessionRes.data
    if (session?.agentId) {
      selectedAgentId.value = session.agentId
      // 加载 agent 详情以获取欢迎语和推荐问题
      loadCurrentAgent(session.agentId)
    }
    scrollToBottom()
  } catch (e) {
    messages.value = []
  } finally {
    loadingHistory.value = false
  }
}

async function loadCurrentAgent(agentId) {
  if (!agentId) {
    currentAgent.value = null
    return
  }
  try {
    const res = await getAgent(agentId)
    currentAgent.value = res.data
  } catch {
    currentAgent.value = null
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
    let sid = sessionId.value

    // 新对话：先创建会话，再发送消息
    if (!sid) {
      const res = await createSession(selectedAgentId.value)
      sid = res.data.id
      // 更新 URL 为 /chat/{sessionId}，跳过 watcher 避免重新加载消息
      skipNextWatch.value = true
      router.replace(`/chat/${sid}`)
    }

    await chatStream(
      { message: text, sessionId: sid, agentId: selectedAgentId.value },
      (chunk) => {
        assistantMsg.content += chunk
        scrollToBottom()
      },
      () => {
        loading.value = false
        // 通知侧边栏刷新会话标题（异步生成）
        setTimeout(() => {
          window.dispatchEvent(new CustomEvent('session-title-updated'))
        }, 2000)
      }
    )
  } catch (e) {
    assistantMsg.content = '请求失败：' + (e.message || '未知错误')
    loading.value = false
  }
}

function copyMessage(msg) {
  navigator.clipboard.writeText(msg.content).then(() => {
    msg._copied = true
    setTimeout(() => { msg._copied = false }, 2000)
  })
}

function scrollToBottom() {
  nextTick(() => {
    const el = messagesRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function loadAgents() {
  try {
    const res = await getAgents({ pageNum: 1, pageSize: 100 })
    agents.value = res.data.records || []
  } catch (e) {
    // ignore
  }
}

onMounted(() => {
  // 处理从 AgentDetail 带过来的 agentId 查询参数
  const queryAgentId = route.query.agentId
  if (queryAgentId) {
    selectedAgentId.value = Number(queryAgentId)
    loadCurrentAgent(Number(queryAgentId))
  }
  loadHistory()
  loadAgents()
})

watch(() => route.params.sessionId, (newVal, oldVal) => {
  // sendMessage 内部 router.replace 触发的 watcher，跳过避免重新加载消息
  if (skipNextWatch.value) {
    skipNextWatch.value = false
    return
  }
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
.bot-avatar-img {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
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
.message-content-wrapper {
  position: relative;
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
.message:hover .btn-copy {
  opacity: 1;
}
.btn-copy:hover {
  color: #52525b;
}
.btn-copy.copied {
  color: #16a34a;
  opacity: 1;
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
.agent-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.agent-label {
  font-size: 12px;
  color: #71717a;
  font-weight: 500;
  flex-shrink: 0;
}
.agent-select {
  flex: 1;
  max-width: 280px;
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
