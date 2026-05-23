<template>
  <div class="chat-container">
    <!-- 消息列表 -->
    <div class="chat-messages" ref="messagesRef">
      <!-- 欢迎状态（新对话 + 无消息） -->
      <div v-if="!sessionId && messages.length === 0 && !loadingHistory" class="empty-state">
        <img src="/lightbot-logo-single.png" alt="LightBot" class="empty-logo" />
        <div class="welcome-content" v-html="renderMarkdown(currentWelcomeMessage)"></div>
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
          没有默认Agent，<router-link to="/agents">去创建</router-link>
        </div>
      </div>

      <!-- 消息列表 -->
      <div v-for="(msg, i) in messages" :key="i" :class="['message', msg.role]">
        <div class="message-avatar">
          <span v-if="msg.role === 'user'">{{ userInitial }}</span>
          <img v-else-if="currentAgent?.avatar" :src="currentAgent.avatar" alt="" class="bot-avatar-img" @error="currentAgent.avatar = ''" />
          <span v-else class="bot-avatar">LB</span>
        </div>
        <div class="message-body">
          <div class="message-meta">
            {{ msg.role === 'user' ? '你' : 'LightBot' }}
          </div>
          <div class="message-content-wrapper">
            <!-- 工具调用展示 -->
            <div v-if="msg._toolEvents && msg._toolEvents.length > 0" class="tool-calls-group">
              <div class="tool-calls-header" @click="msg._toolExpanded = !msg._toolExpanded">
                <LoadingOutlined v-if="!msg._toolsDone" class="tool-icon spinning" />
                <CheckCircleOutlined v-else class="tool-icon tool-success" />
                <span class="tool-calls-title">
                  调用了 {{ getUniqueToolNames(msg._toolEvents).length }} 个工具
                </span>
                <span class="tool-calls-names">
                  {{ getUniqueToolNames(msg._toolEvents).join(', ') }}
                </span>
                <RightOutlined :class="{ expanded: msg._toolExpanded }" class="tool-expand-icon" />
              </div>
              <div v-show="msg._toolExpanded" class="tool-calls-list">
                <div v-for="(evt, ti) in msg._toolEvents" :key="ti" class="tool-call-item">
                  <div v-if="evt.type === 'tool_call'" class="tool-call-row">
                    <SearchOutlined v-if="evt.toolName === 'query_knowledge'" class="tool-icon tool-query" />
                    <LoadingOutlined v-else class="tool-icon spinning" />
                    <span class="tool-name">{{ evt.toolName }}</span>
                    <span class="tool-args">{{ formatToolArgs(evt.args) }}</span>
                  </div>
                  <div v-else-if="evt.type === 'tool_result'" class="tool-result-row">
                    <CheckCircleOutlined class="tool-icon tool-success" />
                    <span class="tool-name">{{ evt.toolName }} 完成</span>
                    <div class="tool-result-summary" @click="toggleToolResult(msg, ti)">
                      <span v-if="parseToolResultCount(evt.result) !== null">
                        找到 {{ parseToolResultCount(evt.result) }} 条相关内容
                      </span>
                      <span v-else>执行完成</span>
                      <RightOutlined :class="{ expanded: isToolResultExpanded(msg, ti) }" class="tool-expand-icon" />
                    </div>
                    <div v-if="isToolResultExpanded(msg, ti)" class="tool-result-content">{{ evt.result }}</div>
                  </div>
                  <div v-else-if="evt.type === 'tool_status'" class="tool-status-row">
                    <CheckCircleOutlined v-if="msg._toolsDone" class="tool-icon tool-success" />
                    <LoadingOutlined v-else class="tool-icon spinning" />
                    <span class="tool-status-text">{{ evt.message }}</span>
                  </div>
                </div>
              </div>
            </div>
            <!-- 流式阶段 & 结束后：统一渲染 Markdown -->
            <div class="message-content" v-html="renderMarkdown(msg.content)" />
            <!-- 复制按钮（仅流式结束后显示） -->
            <a-tooltip v-if="!msg._streaming && msg.content" :title="msg._copied ? '已复制' : '复制'">
              <button
                class="btn-copy"
                :class="{ copied: msg._copied }"
                @click="copyMessage(msg)"
              >
                <CheckOutlined v-if="msg._copied" />
                <CopyOutlined v-else />
              </button>
            </a-tooltip>
          </div>
          <!-- RAG引用列表（从消息metadata中解析） -->
          <div v-if="msg.role === 'assistant' && getMsgRagRefs(msg).length > 0 && !msg._streaming" class="rag-references">
            <div class="rag-header">
              <FileTextOutlined />
              <span>参考文献 ({{ getMsgRagRefs(msg).length }})</span>
            </div>
            <div class="rag-list">
              <div v-for="(ref, ri) in getMsgRagRefs(msg)" :key="ri" class="rag-item">
                <div class="rag-item-header" @click="toggleReference(msg, ri)">
                  <RightOutlined :class="{ expanded: isReferenceExpanded(msg, ri) }" />
                  <span class="rag-doc-name">{{ ref.documentName }}</span>
                  <span class="rag-score">{{ (ref.score * 100).toFixed(1) }}%</span>
                  <a-tooltip v-if="ref.knowledgeId" title="查看知识库">
                    <LinkOutlined class="rag-nav-btn" @click.stop="goToKnowledge(ref.knowledgeId, ref.documentId)" />
                  </a-tooltip>
                </div>
                <div v-if="isReferenceExpanded(msg, ri)" class="rag-item-content">
                  {{ ref.contentPreview }}
                </div>
              </div>
            </div>
          </div>
          <!-- 耗时显示（仅最后一条AI消息且流式结束后显示） -->
          <div v-if="msg.role === 'assistant' && i === messages.length - 1 && !msg._streaming && lastReplyElapsed !== null" class="reply-elapsed">
            {{ formatElapsed(lastReplyElapsed) }}
          </div>
        </div>
      </div>

      <!-- 加载中（等待第一个 chunk）- 显示AI头像和加载动画 -->
      <div v-if="loading && !streaming" class="message assistant">
        <div class="message-avatar">
          <img v-if="currentAgent?.avatar" :src="currentAgent.avatar" alt="" class="bot-avatar-img" @error="currentAgent.avatar = ''" />
          <span v-else class="bot-avatar">LB</span>
        </div>
        <div class="message-body">
          <div class="message-meta">LightBot</div>
          <div class="message-content status-content">
            <div class="status-loading">
              <span class="status-spinner"></span>
              <span class="status-text">{{ currentStatus || '正在思考...' }}</span>
            </div>
          </div>
        </div>
      </div>
      <!-- 流式输出中但还没有内容时，也显示加载动画 -->
      <div v-if="loading && streaming && !hasStreamContent" class="message assistant">
        <div class="message-avatar">
          <img v-if="currentAgent?.avatar" :src="currentAgent.avatar" alt="" class="bot-avatar-img" @error="currentAgent.avatar = ''" />
          <span v-else class="bot-avatar">LB</span>
        </div>
        <div class="message-body">
          <div class="message-meta">LightBot</div>
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
      <div class="chat-input">
        <!-- Agent 选择按钮 -->
        <a-dropdown :trigger="['click']" placement="topLeft">
          <a-tooltip :title="currentAgent?.name || '选择 Agent'">
            <button class="btn-agent">
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
                  <span>{{ a.name }}</span>
                  <span v-if="a.isDefault" class="agent-default-tag">默认</span>
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
          :disabled="!input.trim()"
          @click="sendMessage"
        >
          <SendOutlined />
        </button>
      </div>
      <div class="input-hint">LightBot 可能会犯错，请核实重要信息。</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import { SendOutlined, CopyOutlined, CheckOutlined, RobotOutlined, FileTextOutlined, RightOutlined, LinkOutlined, PauseCircleOutlined, LoadingOutlined, CheckCircleOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { chatStream } from '../api/chat'
import { getSessionMessages, getSession, createSession } from '../api/chatSession'
import { getAgents, getAgent } from '../api/agent'
import { useUserStore } from '../stores/user'
import { safeJsonParse } from '../utils/request'

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
const currentStatus = ref('')
const lastReplyElapsed = ref(null)
const hasStreamContent = ref(false)
const abortController = ref(null)
const toolEvents = ref([])
// 用于存储每条消息的展开状态，key为消息索引，value为Set<refIndex>
const expandedRefsMap = ref(new Map())

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

// 配置 marked (v15 API)
marked.use(
  markedHighlight({
    langPrefix: 'hljs language-',
    highlight(code, lang) {
      if (lang && hljs.getLanguage(lang)) {
        return hljs.highlight(code, { language: lang }).value
      }
      return hljs.highlightAuto(code).value
    },
  }),
  {
    renderer: {
      link({ href, title, tokens }) {
        const text = this.parser.parseInline(tokens)
        const titleAttr = title ? ` title="${title}"` : ''
        return `<a href="${href}"${titleAttr} target="_blank" rel="noopener noreferrer">${text}</a>`
      },
    },
    breaks: true,
    gfm: true,
  }
)

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
  selectedAgentId.value = key
  loadCurrentAgent(key)
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
  try {
    // 并行加载消息和会话详情
    const [msgRes, sessionRes] = await Promise.all([
      getSessionMessages(sessionId.value),
      getSession(sessionId.value),
    ])
    messages.value = (msgRes.data || []).map(m => {
      // 从 metadata 恢复 toolEvents（持久化的工具调用链路）
      let toolEvents = []
      if (m.metadata) {
        try {
          const metadata = typeof m.metadata === 'string' ? safeJsonParse(m.metadata) : m.metadata
          if (metadata?.toolEvents) toolEvents = metadata.toolEvents
        } catch {}
      }
      return {
        role: m.role?.code || m.role,
        content: m.content,
        metadata: m.metadata,
        _toolEvents: toolEvents,
        _toolsDone: true,
      }
    })

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
  hasStreamContent.value = false
  lastReplyElapsed.value = null
  currentStatus.value = '正在思考...'
  toolEvents.value = []
  const sendStartTime = Date.now()
  autoResize()
  scrollToBottom()

  // 延迟创建 assistant 消息，等第一个 chunk 到达时再 push，避免空头像
  let assistantMsg = null
  let pushed = false

  // 创建 AbortController 用于中断流式请求
  abortController.value = new AbortController()

  try {
    let sid = sessionId.value
    const currentAgentId = selectedAgentId.value

    // 新对话：先创建会话，再发送消息
    if (!sid) {
      const res = await createSession(currentAgentId || undefined)
      sid = res.data.id
      // 更新 URL 为 /chat/{sessionId}，跳过 watcher 避免重新加载消息
      skipNextWatch.value = true
      router.replace(`/chat/${sid}`)
    }

    await chatStream(
      { message: text, sessionId: sid, agentId: currentAgentId || undefined },
      {
        // onChunk: 文本内容
        onChunk: (chunk) => {
          if (!pushed) {
            messages.value.push({ role: 'assistant', content: '', _streaming: true, _toolsDone: false, _toolEvents: [] })
            assistantMsg = messages.value[messages.value.length - 1]
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
            messages.value.push({ role: 'assistant', content: '', _streaming: true, _toolsDone: false, _toolEvents: [] })
            assistantMsg = messages.value[messages.value.length - 1]
            pushed = true
          }
          // tool_complete 是标记事件，不加入 _toolEvents
          if (event.type === 'tool_complete') {
            assistantMsg._toolsDone = true
            return
          }
          assistantMsg._toolEvents.push(event)
          toolEvents.value.push(event)
          // 工具状态事件实时更新顶部状态栏
          if (event.type === 'tool_status' && event.message) {
            currentStatus.value = event.message
          }
          scrollToBottom()
        },
        // onMetadata: metadata消息（包含RAG引用等）
        onMetadata: (metadataStr) => {
          if (assistantMsg) {
            assistantMsg.metadata = safeJsonParse(metadataStr)
          }
        },
        // onDone: 完成
        onDone: () => {
          if (assistantMsg) {
            assistantMsg._streaming = false
            assistantMsg._toolsDone = true
          }
          loading.value = false
          streaming.value = false
          hasStreamContent.value = false
          currentStatus.value = ''
          lastReplyElapsed.value = Date.now() - sendStartTime
          abortController.value = null
          // 通知侧边栏刷新会话标题（异步生成，侧边栏会重试刷新）
          setTimeout(() => {
            window.dispatchEvent(new CustomEvent('session-title-updated'))
        }, 1000)
        },
      },
      abortController.value?.signal
    )
  } catch (e) {
    // 用户主动中断
    if (e.name === 'AbortError') {
      if (!assistantMsg) {
        messages.value.push({ role: 'assistant', content: '', _streaming: false, _toolsDone: true, _toolEvents: [] })
        assistantMsg = messages.value[messages.value.length - 1]
      }
      assistantMsg.content += '\n\n*AI 输出已终止*'
      assistantMsg._streaming = false
    } else {
      if (!assistantMsg) {
        messages.value.push({ role: 'assistant', content: '' })
        assistantMsg = messages.value[messages.value.length - 1]
      }
      assistantMsg.content = 'AI 大模型调用失败，请检查模型配置是否正确。\n\n错误详情：' + (e.message || '未知错误')
      assistantMsg._streaming = false
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

function stopGenerating() {
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
}

function getUniqueToolNames(events) {
  const names = new Set()
  events.forEach(e => { if (e.toolName) names.add(e.toolName) })
  return [...names]
}

function formatToolArgs(args) {
  if (!args) return ''
  try {
    const obj = JSON.parse(args)
    const entries = Object.entries(obj)
    if (entries.length === 0) return ''
    return entries.map(([k, v]) => `${k}=${typeof v === 'string' && v.length > 50 ? v.substring(0, 50) + '...' : v}`).join(', ')
  } catch {
    return args.length > 80 ? args.substring(0, 80) + '...' : args
  }
}

function truncateResult(result) {
  if (!result) return ''
  return result.length > 300 ? result.substring(0, 300) + '...' : result
}

/**
 * 解析工具返回结果中的数量（如"找到 1 条相关内容"）
 */
function parseToolResultCount(result) {
  if (!result) return null
  const match = result.match(/找到\s*(\d+)\s*条/)
  return match ? parseInt(match[1]) : null
}

// 用于存储每条消息的工具结果展开状态
const expandedToolResultsMap = ref(new Map())

function isToolResultExpanded(msg, index) {
  const msgIndex = messages.value.indexOf(msg)
  const key = `${msgIndex}-tool-${index}`
  return expandedToolResultsMap.value.has(key)
}

function toggleToolResult(msg, index) {
  const msgIndex = messages.value.indexOf(msg)
  const key = `${msgIndex}-tool-${index}`
  const newMap = new Map(expandedToolResultsMap.value)
  if (newMap.has(key)) {
    newMap.delete(key)
  } else {
    newMap.set(key, true)
  }
  expandedToolResultsMap.value = newMap
}

function formatElapsed(ms) {
  if (ms < 1000) return `耗时 ${ms}ms`
  return `耗时 ${(ms / 1000).toFixed(1)}s`
}

function goToKnowledge(knowledgeId, documentId) {
  const query = documentId ? { docId: String(documentId) } : {}
  router.push({ path: `/knowledge/${knowledgeId}`, query })
}

function scrollToBottom() {
  requestAnimationFrame(() => {
    const el = messagesRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function loadAgents() {
  try {
    const res = await getAgents({ pageNum: 1, pageSize: 100 })
    agents.value = res.data.records || []

    // 新对话时，自动选中默认Agent（由 selectedAgentId watcher 统一调用 loadCurrentAgent）
    if (!sessionId.value) {
      const defaultAgent = agents.value.find(a => a.isDefault)
      if (defaultAgent) {
        selectedAgentId.value = String(defaultAgent.id)
      } else if (agents.value.length > 0) {
        selectedAgentId.value = String(agents.value[0].id)
      }
    }
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
  // 流式对话进行中，中断当前流
  if (streaming.value && abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
  // 切换对话时清空展开状态
  expandedRefsMap.value = new Map()
  expandedToolResultsMap.value = new Map()
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
.agent-default-tag {
  font-size: 10px;
  padding: 1px 6px;
  background: #eff6ff;
  color: #2563eb;
  border-radius: 100px;
  margin-left: auto;
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

/* 工具调用样式 */
.tool-calls-group {
  margin-bottom: 8px;
  border: 1px solid #e4e4e7;
  border-radius: 8px;
  overflow: hidden;
}
.tool-calls-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #f9fafb;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  color: #52525b;
  transition: background 0.15s;
}
.tool-calls-header:hover {
  background: #f4f4f5;
}
.tool-calls-title {
  font-weight: 500;
}
.tool-calls-names {
  color: #a1a1aa;
  margin-left: 4px;
}
.tool-expand-icon {
  margin-left: auto;
  font-size: 10px;
  transition: transform 0.2s;
}
.tool-expand-icon.expanded {
  transform: rotate(90deg);
}
.tool-calls-list {
  border-top: 1px solid #e4e4e7;
}
.tool-call-item {
  padding: 6px 12px;
  border-bottom: 1px solid #f4f4f5;
}
.tool-call-item:last-child {
  border-bottom: none;
}
.tool-call-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 13px;
}
.tool-result-row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 6px;
  font-size: 13px;
}
.tool-icon {
  flex-shrink: 0;
  margin-top: 2px;
}
.tool-icon.tool-success {
  color: #10b981;
}
.tool-icon.tool-query {
  color: #6366f1;
}
.tool-name {
  font-weight: 500;
  color: #171717;
  flex-shrink: 0;
}
.tool-args {
  color: #71717a;
  font-family: 'SF Mono', 'Menlo', monospace;
  font-size: 12px;
  word-break: break-all;
}
.tool-result-summary {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #0070f3;
  font-size: 13px;
}
.tool-result-summary:hover {
  color: #005bc4;
}
.tool-result-content {
  flex-basis: 100%;
  margin-top: 4px;
  padding: 8px;
  background: #f9fafb;
  border-radius: 4px;
  font-size: 12px;
  color: #52525b;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}
.tool-status-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 13px;
  color: #0070f3;
}
.tool-status-text {
  color: #52525b;
  font-size: 12px;
}
.spinning {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
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
