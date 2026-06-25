<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">日志</h1>
        <p class="page-desc">实时查看系统日志，支持按级别过滤</p>
      </div>
      <div class="header-actions">
        <div class="connection-status" :class="{ connected: sseConnected, connecting: connecting }">
          <span class="status-dot"></span>
          {{ sseConnected ? '已连接' : connecting ? '连接中...' : '未连接' }}
        </div>
        <button v-if="!sseConnected" class="btn-primary" @click="connectSSE" :disabled="connecting">
          <LoadingOutlined v-if="connecting" :spin="true" />
          <LinkOutlined v-else />
          {{ connecting ? '连接中' : '连接' }}
        </button>
        <button v-else class="btn-cancel" @click="disconnectSSE">
          <DisconnectOutlined /> 断开
        </button>
      </div>
    </div>

    <!-- 过滤栏 -->
    <div class="filter-bar">
      <div class="level-filters">
        <button
          v-for="l in levels"
          :key="l.value"
          :class="['level-btn', l.value.toLowerCase(), { active: activeLevels.has(l.value) }]"
          @click="toggleLevel(l.value)"
        >
          {{ l.label }}
        </button>
      </div>
      <a-input
        v-model:value="searchText"
        placeholder="搜索日志内容..."
        allow-clear
        style="width: 280px"
      >
        <template #prefix><SearchOutlined /></template>
      </a-input>
      <button class="btn-cancel" @click="clearLogs">清空</button>
    </div>

    <!-- 日志表格 -->
    <a-spin :spinning="historyLoading">
    <div class="log-table-wrapper">
      <table class="log-table">
        <thead>
          <tr>
            <th style="width: 180px">时间</th>
            <th style="width: 80px">级别</th>
            <th style="width: 200px">来源</th>
            <th>内容</th>
          </tr>
        </thead>
      </table>
      <div class="log-table-body" ref="logBodyRef">
        <table class="log-table">
          <tbody>
            <tr
              v-for="(log, idx) in filteredLogs"
              :key="idx"
              :class="['log-row', log.level?.toLowerCase()]"
              @click="showDetail(log)"
            >
              <td style="width: 180px">{{ formatTime(log.timestamp) }}</td>
              <td style="width: 80px">
                <span :class="['level-tag', log.level?.toLowerCase()]">{{ log.level }}</span>
              </td>
              <td style="width: 200px" class="logger-cell" :title="log.logger">{{ shortLogger(log.logger) }}</td>
              <td class="message-cell" :title="log.message">{{ log.message }}</td>
            </tr>
            <tr v-if="filteredLogs.length === 0">
              <td colspan="4" class="empty-cell">暂无日志数据</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    </a-spin>

    <div class="log-count">共 {{ filteredLogs.length }} 条日志</div>

    <!-- 日志详情弹窗 -->
    <a-modal v-model:open="detailVisible" title="日志详情" :width="700" :footer="null">
      <div v-if="detailLog" class="log-detail">
        <div class="detail-row">
          <span class="detail-label">时间</span>
          <span>{{ formatTime(detailLog.timestamp) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">级别</span>
          <span :class="['level-tag', detailLog.level?.toLowerCase()]">{{ detailLog.level }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">来源</span>
          <span>{{ detailLog.logger }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">内容</span>
          <pre class="detail-message">{{ detailLog.message }}</pre>
        </div>
        <div v-if="detailLog.stackTrace" class="detail-row">
          <span class="detail-label">异常堆栈</span>
          <pre class="detail-stack">{{ detailLog.stackTrace }}</pre>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { LinkOutlined, DisconnectOutlined, SearchOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import { getRecentLogs } from '../api/log'
import { sseFetch } from '../utils/sseFetch'

const levels = [
  { value: 'INFO', label: 'INFO' },
  { value: 'DEBUG', label: 'DEBUG' },
  { value: 'WARN', label: 'WARN' },
  { value: 'ERROR', label: 'ERROR' },
]

const logs = ref([])
const historyLoading = ref(false)
const activeLevels = ref(new Set(['INFO', 'DEBUG', 'WARN', 'ERROR']))
const searchText = ref('')
const sseConnected = ref(false)
const connecting = ref(false)
const logBodyRef = ref(null)
const detailVisible = ref(false)
const detailLog = ref(null)
let eventSource = null
const LOG_SSE_MAX_RETRIES = 10
const LOG_SSE_BASE_DELAY = 3000

const filteredLogs = computed(() => {
  let result = logs.value.filter(l => activeLevels.value.has(l.level))
  if (searchText.value) {
    const keyword = searchText.value.toLowerCase()
    result = result.filter(l =>
      l.message?.toLowerCase().includes(keyword) ||
      l.logger?.toLowerCase().includes(keyword)
    )
  }
  return result
})

function toggleLevel(level) {
  const s = new Set(activeLevels.value)
  if (s.has(level)) {
    s.delete(level)
  } else {
    s.add(level)
  }
  activeLevels.value = s
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${String(d.getMilliseconds()).padStart(3, '0')}`
}

function shortLogger(logger) {
  if (!logger) return ''
  const parts = logger.split('.')
  return parts.length > 2 ? `...${parts[parts.length - 2]}.${parts[parts.length - 1]}` : logger
}

function showDetail(log) {
  detailLog.value = log
  detailVisible.value = true
}

function clearLogs() {
  logs.value = []
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const res = await getRecentLogs({ limit: 500 })
    logs.value = res.data || []
    await nextTick()
    scrollToBottom()
  } catch (e) {
    // ignore
  } finally {
    historyLoading.value = false
  }
}

function connectSSE() {
  if (eventSource || connecting.value) return

  connecting.value = true
  const token = localStorage.getItem('token') || ''

  eventSource = sseFetch(`/api/logs/stream`, {
    token,
    maxRetries: LOG_SSE_MAX_RETRIES,
    retryDelay: LOG_SSE_BASE_DELAY,
    onEvent({ event, data }) {
      if (event === 'log') {
        try {
          const logEvent = JSON.parse(data)
          logs.value.push(logEvent)
          if (logs.value.length > 5000) {
            logs.value = logs.value.slice(-3000)
          }
          nextTick(() => scrollToBottom())
        } catch { /* ignore parse error */ }
      }
      if (connecting.value) {
        sseConnected.value = true
        connecting.value = false
      }
    },
    onDone() {
      sseConnected.value = false
    },
    onError() {
      sseConnected.value = false
      connecting.value = false
      eventSource = null
    },
  })
}

function disconnectSSE() {
  eventSource?.close()
  eventSource = null
  sseConnected.value = false
}

function scrollToBottom() {
  if (logBodyRef.value) {
    logBodyRef.value.scrollTop = logBodyRef.value.scrollHeight
  }
}

onMounted(() => {
  loadHistory()
  connectSSE()
})

onUnmounted(() => {
  disconnectSSE()
})
</script>

<style scoped>
.page {
  padding: 32px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-canvas-soft);
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  flex-shrink: 0;
}
.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 4px;
}
.page-desc {
  font-size: 14px;
  color: var(--color-mute);
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.connection-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #ef4444;
}
.connection-status.connected {
  color: #10b981;
}
.connection-status.connecting {
  color: #f59e0b;
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ef4444;
}
.connection-status.connected .status-dot {
  background: #10b981;
}
.connection-status.connecting .status-dot {
  background: #f59e0b;
  animation: pulse 1s infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.btn-primary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
}
.btn-primary:hover { background: #27272a; }
.btn-cancel {
  padding: 8px 16px;
  background: var(--color-canvas);
  color: var(--color-mute);
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
}
.btn-cancel:hover { border-color: var(--color-ink); color: var(--color-ink); }

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-shrink: 0;
}
.level-filters {
  display: flex;
  gap: 6px;
}
.level-btn {
  padding: 4px 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  background: var(--color-canvas);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  color: var(--color-mute);
}
.level-btn.active { color: #fff; border-color: transparent; }
.level-btn.info.active { background: #3b82f6; }
.level-btn.debug.active { background: #8b5cf6; }
.level-btn.warn.active { background: #f59e0b; }
.level-btn.error.active { background: #ef4444; }

.log-table-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  overflow: hidden;
}
.log-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}
.log-table thead {
  background: var(--color-canvas-soft-2);
}
.log-table th {
  padding: 10px 12px;
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-body);
  border-bottom: 1px solid var(--color-hairline);
  white-space: nowrap;
}
.log-table-body {
  flex: 1;
  overflow-y: auto;
}
.log-table td {
  padding: 8px 12px;
  font-size: 13px;
  color: var(--color-ink);
  border-bottom: 1px solid var(--color-hairline);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.log-row {
  cursor: pointer;
  transition: background 0.1s;
}
.log-row:hover {
  background: var(--color-canvas-soft);
}
.log-row.error {
  background: var(--color-error-bg);
}
.log-row.error:hover {
  background: var(--color-error-bg);
}
.log-row.warn {
  background: var(--color-warn-bg);
}

.level-tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}
.level-tag.info { background: var(--color-info-bg); color: #1d4ed8; }
.level-tag.debug { background: var(--color-purple-bg); color: #6d28d9; }
.level-tag.warn { background: var(--color-warn-bg-deep); color: #b45309; }
.level-tag.error { background: var(--color-error-bg); color: #dc2626; }

.logger-cell {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  color: var(--color-mute);
}
.message-cell {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
}
.empty-cell {
  text-align: center;
  color: var(--color-mute);
  padding: 48px 12px !important;
}

.log-count {
  flex-shrink: 0;
  padding: 8px 0;
  font-size: 12px;
  color: var(--color-mute);
  text-align: right;
}

.log-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.detail-row {
  display: flex;
  gap: 12px;
}
.detail-label {
  width: 70px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-mute);
  flex-shrink: 0;
}
.detail-message {
  margin: 0;
  padding: 12px;
  background: var(--color-canvas-soft-2);
  border-radius: 6px;
  font-size: 13px;
  color: var(--color-ink);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
  flex: 1;
}
.detail-stack {
  margin: 0;
  padding: 12px;
  background: var(--color-error-bg);
  border-radius: 6px;
  font-size: 12px;
  color: #dc2626;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
  flex: 1;
}
</style>
