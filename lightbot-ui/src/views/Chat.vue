<template>
  <div class="chat-container">
    <!-- 消息列表 -->
    <div class="chat-messages" ref="messagesRef">
      <!-- 欢迎状态（新对话 + 无消息） -->
      <div v-if="!sessionId && messages.length === 0 && !loadingHistory" class="empty-state">
        <img src="/lightbot-logo-single.png" alt="LightBot" class="empty-logo" />
        <div class="welcome-content" v-html="renderMarkdown(currentWelcomeMessage)"></div>
        <!-- 推荐问题轮播 -->
        <div v-if="currentRecommendedQuestions.length > 0" class="recommended-questions">
          <transition name="fade" mode="out-in">
            <button
              :key="currentQuestionIndex"
              class="btn-question"
              @click="input = currentRecommendedQuestions[currentQuestionIndex]; $nextTick(() => $refs.inputRef?.focus())"
            >
              {{ currentRecommendedQuestions[currentQuestionIndex] }}
            </button>
          </transition>
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
            <!-- 流式阶段：显示原始文本 + 闪烁光标 -->
            <div v-if="msg._streaming" class="message-content streaming-content">
              <span class="streaming-text">{{ msg.content }}</span><span class="typing-cursor">▊</span>
            </div>
            <!-- 流式结束后：渲染 Markdown -->
            <div v-else class="message-content" v-html="renderMarkdown(msg.content)" />
            <!-- 复制按钮（仅AI消息） -->
            <a-tooltip :title="msg._copied ? '已复制' : '复制'">
              <button
                v-if="msg.role === 'assistant' && msg.content"
                class="btn-copy"
                :class="{ copied: msg._copied }"
                @click="copyMessage(msg)"
              >
                <CheckOutlined v-if="msg._copied" />
                <CopyOutlined v-else />
              </button>
            </a-tooltip>
          </div>
        </div>
      </div>

      <!-- 加载中（等待第一个 chunk） -->
      <div v-if="loading && !streaming" class="message assistant">
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
        <!-- Agent 选择按钮 -->
        <a-dropdown :trigger="['click']" placement="topLeft">
          <a-tooltip :title="currentAgent?.name || '默认 Agent'">
            <button class="btn-agent">
              <RobotOutlined v-if="!currentAgent" />
              <img v-else-if="currentAgent.avatar" :src="`http://localhost:9000/lightbot/${currentAgent.avatar}`" alt="" class="btn-agent-avatar" />
              <span v-else class="btn-agent-initial">{{ currentAgent.name[0] }}</span>
            </button>
          </a-tooltip>
          <template #overlay>
            <a-menu @click="handleAgentSelect" :selectedKeys="selectedAgentId ? [String(selectedAgentId)] : ['__default__']">
              <a-menu-item key="__default__">
                <div class="agent-menu-item">
                  <span class="agent-menu-icon default-icon">LB</span>
                  <span>默认 Agent</span>
                </div>
              </a-menu-item>
              <a-menu-divider />
              <a-menu-item v-for="a in agents" :key="String(a.id)">
                <div class="agent-menu-item">
                  <img v-if="a.avatar" :src="`http://localhost:9000/lightbot/${a.avatar}`" alt="" class="agent-menu-icon" />
                  <span v-else class="agent-menu-icon">{{ a.name[0] }}</span>
                  <span>{{ a.name }}</span>
                </div>
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>

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
import { ref, nextTick, onMounted, onUnmounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import { SendOutlined, CopyOutlined, CheckOutlined, RobotOutlined } from '@ant-design/icons-vue'
import { chatStream } from '../api/chat'
import { getSessionMessages, getSession, createSession } from '../api/chatSession'
import { getAgents, getAgent } from '../api/agent'
import { useUserStore } from '../stores/user'

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
const currentAgent = ref(null)
const currentQuestionIndex = ref(0)
let questionTimer = null

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
const renderer = new marked.Renderer()
renderer.link = function ({ href, title, text }) {
  const titleAttr = title ? ` title="${title}"` : ''
  return `<a href="${href}"${titleAttr} target="_blank" rel="noopener noreferrer">${text}</a>`
}

marked.setOptions({
  renderer,
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

function handleAgentSelect({ key }) {
  if (key === '__default__') {
    selectedAgentId.value = null
    currentAgent.value = null
  } else {
    selectedAgentId.value = key
    loadCurrentAgent(key)
  }
}

async function loadHistory() {
  // 流式对话进行中不加载历史，避免替换 messages 数组破坏 stream 闭包引用
  if (streaming.value) return

  if (!sessionId.value) {
    messages.value = []
    selectedAgentId.value = null
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
  streaming.value = true
  autoResize()
  scrollToBottom()

  // 延迟创建 assistant 消息，等第一个 chunk 到达时再 push，避免空头像
  let assistantMsg = null
  let pushed = false

  try {
    let sid = sessionId.value

    // 新对话：先创建会话，再发送消息
    if (!sid) {
      const res = await createSession(selectedAgentId.value || undefined)
      sid = res.data.id
      // 更新 URL 为 /chat/{sessionId}，跳过 watcher 避免重新加载消息
      skipNextWatch.value = true
      router.replace(`/chat/${sid}`)
    }

    await chatStream(
      { message: text, sessionId: sid, agentId: selectedAgentId.value || undefined },
      (chunk) => {
        if (!pushed) {
          assistantMsg = { role: 'assistant', content: '', _streaming: true }
          messages.value.push(assistantMsg)
          pushed = true
        }
        assistantMsg.content += chunk
        scrollToBottom()
      },
      () => {
        if (assistantMsg) assistantMsg._streaming = false
        loading.value = false
        streaming.value = false
        // 通知侧边栏刷新会话标题（异步生成，侧边栏会重试刷新）
        setTimeout(() => {
          window.dispatchEvent(new CustomEvent('session-title-updated'))
        }, 1000)
      }
    )
  } catch (e) {
    if (!assistantMsg) {
      assistantMsg = { role: 'assistant', content: '' }
      messages.value.push(assistantMsg)
    }
    assistantMsg.content = 'AI 大模型调用失败，请检查模型配置是否正确。\n\n错误详情：' + (e.message || '未知错误')
    assistantMsg._streaming = false
    loading.value = false
    streaming.value = false
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
    // 仅加载 Agent 详情用于展示欢迎语，不设置 selectedAgentId
    // 新对话创建会话时由后端决定默认 Agent
    loadCurrentAgent(queryAgentId)
    // 清除 URL 中的 agentId 参数，避免刷新页面时重复加载
    router.replace({ path: '/chat' })
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
  // 流式对话进行中不加载历史，避免替换 messages 数组破坏 stream 闭包引用
  if (streaming.value) return
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

// 推荐问题轮播：每 2 秒切换下一个
watch(currentRecommendedQuestions, (questions) => {
  currentQuestionIndex.value = 0
  clearInterval(questionTimer)
  if (questions.length > 1) {
    questionTimer = setInterval(() => {
      currentQuestionIndex.value = (currentQuestionIndex.value + 1) % questions.length
    }, 2000)
  }
}, { immediate: true })

onUnmounted(() => {
  clearInterval(questionTimer)
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
  height: 64px;
  margin-bottom: 24px;
  opacity: 0.6;
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
.chat-input {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #e4e4e7;
  border-radius: 12px;
  padding: 8px 8px 8px 4px;
  background: #fff;
  transition: border-color 0.15s;
}
.chat-input:focus-within {
  border-color: #0070f3;
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
.streaming-content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
}
.streaming-text {
  /* 保持与 .message-content 一致的样式 */
}
.streaming-content .typing-cursor {
  color: var(--primary-color, #6366f1);
  font-weight: bold;
  margin-left: 1px;
}

/* 推荐问题轮播过渡 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.4s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
