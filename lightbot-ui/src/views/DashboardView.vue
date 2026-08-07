<template>
  <div class="dashboard">
    <!-- 骨架屏 -->
    <div v-if="loading" class="dashboard-skeleton">
      <!-- 顶部统计卡片骨架 -->
      <div class="stats-overview">
        <div class="stat-card" v-for="i in 4" :key="i">
          <div class="sk-block sk-icon"></div>
          <div class="sk-info">
            <div class="sk-block sk-line sk-value"></div>
            <div class="sk-block sk-line sk-label"></div>
          </div>
        </div>
      </div>
      <!-- 对话趋势面板骨架 -->
      <div class="trend-row">
        <div class="panel panel-trend">
          <div class="sk-block sk-line sk-title"></div>
          <div class="sk-block sk-chart"></div>
        </div>
      </div>
      <!-- 三列网格骨架 -->
      <div class="dashboard-grid">
        <div class="panel" v-for="i in 3" :key="i">
          <div class="sk-block sk-line sk-title"></div>
          <div class="sk-block sk-line sk-row" v-for="j in 3" :key="j"></div>
        </div>
      </div>
    </div>

    <!-- 真实内容 -->
    <template v-else>
    <!-- 顶部统计概览 -->
    <div class="stats-overview">
      <LbStatCard :icon="RobotOutlined" accent="blue" :value="basic.agentCount ?? '-'" label="智能体" />
      <LbStatCard :icon="DatabaseOutlined" accent="green" :value="basic.knowledgeCount ?? '-'" label="知识库" />
      <LbStatCard :icon="MessageOutlined" accent="purple" :value="basic.sessionCount ?? '-'" label="对话会话" />
      <LbStatCard :icon="FileTextOutlined" accent="orange" :value="basic.messageCount ?? '-'" label="消息总数" />
    </div>

    <!-- 企业 Token 概览 -->
    <div class="token-overview-row">
      <div class="panel panel-token">
        <div class="panel-header panel-header--token">
          <h3>
            企业 Token
            <span class="trend-metric-hint">今日消耗</span>
          </h3>
          <router-link v-if="isAdmin" class="token-manage-link" to="/app/settings?tab=token">限额与排行</router-link>
        </div>
        <div class="token-overview-body">
          <div class="token-overview-stat">
            <div class="token-overview-label">全局已用</div>
            <div class="token-overview-value">{{ formatToken(tokenStats.globalUsed) }}</div>
            <div class="token-overview-sub">/ {{ formatToken(tokenStats.globalLimit) }}</div>
          </div>
          <a-progress
            :percent="tokenUsagePercent"
            :stroke-color="tokenUsagePercent > 80 ? '#ef4444' : '#10b981'"
            :show-info="false"
            size="small"
          />
          <div class="token-overview-hint">
            企业维度统计；开放 API 另受各企业 API Key 日配额约束。管理员可在系统管理中配置限额。
          </div>
        </div>
      </div>
    </div>

    <!-- 对话趋势（整行） -->
    <div class="trend-row">
      <div class="panel panel-trend">
        <div class="panel-header panel-header--trend">
          <h3>
            对话趋势
            <span class="trend-metric-hint">每日消息数</span>
          </h3>
          <div class="trend-controls">
            <div class="trend-quick-btns">
              <button
                v-for="d in trendQuickDays"
                :key="d"
                type="button"
                class="trend-quick-btn"
                :class="{ active: trendMode === 'days' && trendDays === d }"
                @click="selectTrendDays(d)"
              >{{ d }}天</button>
            </div>
            <a-range-picker
              v-model:value="trendDateRange"
              size="small"
              :allow-clear="true"
              format="YYYY-MM-DD"
              :disabled-date="disabledTrendDate"
              @change="onTrendRangeChange"
            />
          </div>
        </div>
        <div class="bar-chart-scroll" ref="trendScrollRef">
          <div class="bar-chart bar-chart--trend" :style="trendChartStyle">
            <div
              v-for="(d, i) in chatTrend"
              :key="`${trendChartKey}-${d.date}`"
              class="bar-col bar-col--trend"
              :style="trendColStyle"
            >
              <div class="bar-value">{{ d.count }}</div>
              <div class="bar-track" :style="trendTrackStyle">
                <div class="bar-fill" :style="{ height: barHeight(d.count) + '%', '--bar-i': i }"></div>
              </div>
              <div class="bar-label">{{ formatDay(d.date) }}</div>
            </div>
            <div v-if="chatTrend.length === 0" class="chart-empty">暂无数据</div>
          </div>
        </div>
        <div class="chat-summary">
          <span>区间会话: <b>{{ chatStats.totalSessions ?? '-' }}</b></span>
          <span>区间消息: <b>{{ chatStats.totalMessages ?? '-' }}</b></span>
          <span v-if="chatStats.trendStartDate && chatStats.trendEndDate" class="trend-range-info">
            {{ chatStats.trendStartDate }} ~ {{ chatStats.trendEndDate }}
          </span>
        </div>
      </div>
    </div>

    <!-- Grid 布局 -->
    <div class="dashboard-grid">

      <!-- Agent 统计 -->
      <div class="grid-item agent-stats">
        <div class="panel">
          <div class="panel-header">
            <h3>Agent 概况</h3>
            <span class="panel-tip">{{ agentStats.total ?? 0 }} 个</span>
          </div>
          <div class="status-bars">
            <div v-for="s in agentStatusList" :key="s.key" class="status-row">
              <span class="status-label">{{ s.label }}</span>
              <div class="status-track">
                <div class="status-fill" :class="s.key" :style="{ width: s.percent + '%' }"></div>
              </div>
              <span class="status-count">{{ s.count }}</span>
            </div>
          </div>
          <div class="recent-list" v-if="agentStats.recent?.length">
            <div class="recent-title">最近创建</div>
            <div v-for="a in agentStats.recent" :key="a.id" class="recent-item">
              <span class="recent-name">{{ a.name }}</span>
              <span :class="['recent-tag', a.status]">{{ recentStatusLabel(a) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 知识库统计 -->
      <div class="grid-item knowledge-stats">
        <div class="panel">
          <div class="panel-header">
            <h3>知识库概况</h3>
            <span class="panel-tip">{{ knowledgeStats.totalKnowledge ?? 0 }} 个</span>
          </div>
          <div class="knowledge-metrics">
            <div class="metric-row">
              <span class="metric-label">文档总数</span>
              <span class="metric-value">{{ knowledgeStats.totalDocuments ?? '-' }}</span>
            </div>
            <div class="metric-row">
              <span class="metric-label">分块总数</span>
              <span class="metric-value">{{ knowledgeStats.totalChunks ?? '-' }}</span>
            </div>
          </div>
          <div class="recent-list" v-if="knowledgeStats.recentDocuments?.length">
            <div class="recent-title">最近文档</div>
            <div v-for="doc in knowledgeStats.recentDocuments" :key="doc.id" class="recent-item">
              <span class="recent-name" :title="doc.name">{{ doc.name }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 模型统计 -->
      <div class="grid-item model-stats">
        <div class="panel">
          <div class="panel-header">
            <h3>模型资源</h3>
          </div>
          <div class="model-metrics">
            <div class="model-metric-card">
              <div class="model-metric-value">{{ basic.providerCount ?? '-' }}</div>
              <div class="model-metric-label">模型提供商</div>
            </div>
            <div class="model-metric-card">
              <div class="model-metric-value">{{ basic.modelCount ?? '-' }}</div>
              <div class="model-metric-label">模型数量</div>
            </div>
          </div>
          <div class="model-metrics">
            <div class="model-metric-card">
              <div class="model-metric-value">{{ basic.documentCount ?? '-' }}</div>
              <div class="model-metric-label">文档总数</div>
            </div>
            <div class="model-metric-card">
              <div class="model-metric-value">{{ basic.chunkCount ?? '-' }}</div>
              <div class="model-metric-label">向量分块</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { RobotOutlined, DatabaseOutlined, MessageOutlined, FileTextOutlined } from '@ant-design/icons-vue'
import { getDashboardBasic, getDashboardAgents, getDashboardKnowledge, getDashboardChat } from '../api/dashboard'
import { getTokenBudgetStats } from '../api/tokenBudget'
import { useUserStore } from '../stores/user'
import LbStatCard from '../components/common/LbStatCard.vue'

const userStore = useUserStore()
const basic = ref({})
const agentStats = ref({})
const knowledgeStats = ref({})
const chatStats = ref({})
const tokenStats = ref({ globalUsed: 0, globalLimit: 0, date: '' })
const loading = ref(false)

const isAdmin = computed(() => {
  const role = userStore.user?.role
  return role === 'admin' || role === 'ADMIN'
})

const tokenUsagePercent = computed(() => {
  const limit = tokenStats.value.globalLimit
  if (!limit) return 0
  return Math.min(100, Math.round((tokenStats.value.globalUsed / limit) * 100))
})

function formatToken(val) {
  if (val == null) return '0'
  if (val >= 1_000_000) return (val / 1_000_000).toFixed(1) + 'M'
  if (val >= 1_000) return (val / 1_000).toFixed(1) + 'K'
  return String(val)
}

const trendQuickDays = [7, 14, 30]
const trendDays = ref(7)
const trendMode = ref('days')
const trendDateRange = ref(null)
const trendScrollRef = ref(null)
const trendContainerWidth = ref(0)
// 切换日期/快捷范围时自增，强制柱子重渲染以重放上升动画
const trendChartKey = ref(0)
let trendResizeObserver = null

const chatTrend = computed(() => chatStats.value.messagesPerDay || [])

const chatTrendMax = computed(() => {
  const counts = chatTrend.value.map(d => d.count || 0)
  return counts.reduce((max, c) => Math.max(max, c), 1)
})

const TREND_COL_MIN = 40
const TREND_GAP = 8

function updateTrendContainerWidth() {
  trendContainerWidth.value = trendScrollRef.value?.clientWidth || 0
}

const trendBarLayout = computed(() => {
  const n = chatTrend.value.length || 0
  if (n === 0) {
    return { fill: true, colWidth: 56, gap: TREND_GAP }
  }
  const gap = TREND_GAP
  const containerW = trendContainerWidth.value
  const minScrollWidth = n * TREND_COL_MIN + Math.max(0, n - 1) * gap

  // 未占满容器：均分拉宽柱体填满 X 轴
  if (!containerW || minScrollWidth <= containerW) {
    const colWidth = containerW > 0
      ? Math.floor((containerW - Math.max(0, n - 1) * gap) / n)
      : Math.min(80, Math.round(480 / n))
    return { fill: true, colWidth: Math.max(TREND_COL_MIN, colWidth), gap }
  }

  // 超出容器：固定最小柱宽 + 水平滚动
  const colWidth = TREND_COL_MIN
  const contentWidth = n * colWidth + Math.max(0, n - 1) * gap
  return { fill: false, colWidth, gap, contentWidth }
})

const trendChartStyle = computed(() => {
  const { fill, gap, contentWidth } = trendBarLayout.value
  if (fill) {
    return { width: '100%', gap: `${gap}px` }
  }
  return {
    width: `${contentWidth}px`,
    minWidth: '100%',
    gap: `${gap}px`,
  }
})

const trendColStyle = computed(() => {
  const { fill, colWidth } = trendBarLayout.value
  if (fill) {
    return { flex: '1 1 0', minWidth: `${TREND_COL_MIN}px` }
  }
  return {
    flex: `0 0 ${colWidth}px`,
    width: `${colWidth}px`,
    minWidth: `${colWidth}px`,
  }
})

const trendTrackStyle = computed(() => {
  const { fill, colWidth } = trendBarLayout.value
  const trackWidth = fill
    ? Math.max(28, Math.min(72, colWidth - 16))
    : Math.max(28, Math.min(48, colWidth - 12))
  return { width: `${trackWidth}px` }
})

function barHeight(count) {
  return Math.max(4, (count / chatTrendMax.value) * 100)
}

function formatDay(dateStr) {
  if (!dateStr) return ''
  // "2026-05-20" → "05/20"
  const parts = dateStr.split('-')
  return parts.slice(1).join('/')
}

const agentStatusList = computed(() => {
  const list = agentStats.value.statusList || []
  const total = agentStats.value.total || 1
  return list.map(item => ({
    key: item.code,
    label: item.label,
    count: item.count,
    percent: total > 0 ? (item.count / total) * 100 : 0,
  }))
})


function statusLabel(s) {
  const item = agentStats.value.statusList?.find(x => x.code === s)
  return item?.label || s || '草稿'
}

function recentStatusLabel(item) {
  return item?.statusLabel || statusLabel(item?.status)
}

function buildChatParams() {
  if (trendMode.value === 'range' && trendDateRange.value?.length === 2) {
    return {
      startDate: trendDateRange.value[0].format('YYYY-MM-DD'),
      endDate: trendDateRange.value[1].format('YYYY-MM-DD'),
    }
  }
  return { days: trendDays.value }
}

async function loadChatStats() {
  const c = await getDashboardChat(buildChatParams()).catch(() => ({ data: {} }))
  chatStats.value = c.data || {}
  // 自增 key 触发 v-for 重渲染，让柱子从 0 重新动画上升
  trendChartKey.value++
  await nextTick()
  updateTrendContainerWidth()
}

function selectTrendDays(d) {
  trendMode.value = 'days'
  trendDays.value = d
  trendDateRange.value = null
  loadChatStats()
}

function onTrendRangeChange(dates) {
  if (!dates || dates.length !== 2) {
    trendMode.value = 'days'
    trendDays.value = 7
    loadChatStats()
    return
  }
  // 限制最多90天
  const start = dates[0]
  const end = dates[1]
  const days = end.diff(start, 'day') + 1
  if (days > 90) {
    message.warning('最多支持查询90天（3个月）的数据')
    trendDateRange.value = null
    trendMode.value = 'days'
    trendDays.value = 7
    loadChatStats()
    return
  }
  trendMode.value = 'range'
  loadChatStats()
}

function disabledTrendDate(current) {
  // 限制只能选择最近3个月内的日期
  if (!current) return false
  const now = new Date()
  const threeMonthsAgo = new Date(now.getFullYear(), now.getMonth() - 3, now.getDate(), 0, 0, 0)
  const todayEnd = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59)
  return current.valueOf() < threeMonthsAgo.getTime() || current.valueOf() > todayEnd.getTime()
}

async function loadAll() {
  loading.value = true
  try {
    const [b, a, k, t] = await Promise.all([
      getDashboardBasic().catch(() => ({ data: {} })),
      getDashboardAgents().catch(() => ({ data: {} })),
      getDashboardKnowledge().catch(() => ({ data: {} })),
      getTokenBudgetStats().catch(() => ({ data: {} })),
    ])
    basic.value = b.data || {}
    agentStats.value = a.data || {}
    knowledgeStats.value = k.data || {}
    tokenStats.value = t.data || { globalUsed: 0, globalLimit: 0, date: '' }
    await loadChatStats()
    await nextTick()
    updateTrendContainerWidth()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadAll()
  nextTick(() => {
    updateTrendContainerWidth()
    if (trendScrollRef.value && typeof ResizeObserver !== 'undefined') {
      trendResizeObserver = new ResizeObserver(updateTrendContainerWidth)
      trendResizeObserver.observe(trendScrollRef.value)
    }
  })
})

onUnmounted(() => {
  trendResizeObserver?.disconnect()
})
</script>

<style scoped>
.dashboard {
  padding: 24px 32px;
  height: var(--app-content-height);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-canvas-soft);
}

/* ===== 骨架屏 ===== */
.dashboard-skeleton {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}
.sk-block {
  background: linear-gradient(
    90deg,
    var(--color-canvas-soft-2) 0%,
    var(--color-hairline-strong) 50%,
    var(--color-canvas-soft-2) 100%
  );
  background-size: 200% 100%;
  animation: sk-shimmer 2.4s ease-in-out infinite;
  border-radius: 6px;
}
@keyframes sk-shimmer {
  0% { background-position: 150% 0; }
  100% { background-position: -50% 0; }
}
.sk-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sk-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  flex-shrink: 0;
}
.sk-line {
  height: 14px;
  border-radius: 4px;
}
.sk-value {
  width: 80px;
  height: 24px;
}
.sk-label {
  width: 48px;
  height: 12px;
}
.sk-title {
  width: 120px;
  height: 18px;
  margin-bottom: 16px;
}
.sk-chart {
  flex: 1;
  min-height: 200px;
  border-radius: 8px;
}
.sk-row {
  width: 100%;
  height: 18px;
  margin-bottom: 12px;
}

.token-overview-row {
  margin-bottom: 16px;
}
.panel-token {
  padding: 16px 20px;
}
.panel-header--token {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.panel-header--token h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.token-manage-link {
  font-size: 13px;
  color: var(--color-link);
  text-decoration: none;
}
.token-manage-link:hover {
  text-decoration: underline;
}
.token-overview-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.token-overview-stat {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.token-overview-label {
  font-size: 13px;
  color: var(--color-mute);
}
.token-overview-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-ink);
  font-variant-numeric: tabular-nums;
}
.token-overview-sub {
  font-size: 13px;
  color: var(--color-mute);
}
.token-overview-hint {
  font-size: 12px;
  color: var(--color-mute);
  line-height: 1.5;
}

/* ===== 顶部统计概览 ===== */
.stats-overview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
  flex-shrink: 0;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 20px;
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}
.agent-icon { background: var(--color-purple-bg); color: #7c3aed; }
.knowledge-icon { background: var(--color-info-bg); color: #2563eb; }
.session-icon { background: var(--color-success-bg); color: #16a34a; }
.message-icon { background: var(--color-warn-bg-deep); color: #d97706; }
[data-theme="dark"] .agent-icon { background: #2e1065; }
[data-theme="dark"] .knowledge-icon { background: #0c1a3d; }
[data-theme="dark"] .session-icon { background: #052e16; }
[data-theme="dark"] .message-icon { background: #422006; }
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-ink);
  line-height: 1.2;
}
.stat-label {
  font-size: 13px;
  color: var(--color-mute);
}

/* ===== 对话趋势整行 ===== */
.trend-row {
  margin-bottom: 16px;
}
.panel-trend {
  min-height: 320px;
  display: flex;
  flex-direction: column;
}
.bar-chart-scroll {
  flex: 1;
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 4px;
}
.bar-chart--trend {
  flex: none;
  display: flex;
  align-items: flex-end;
  justify-content: flex-start;
  min-height: 200px;
  min-width: 100%;
  padding-bottom: 8px;
  box-sizing: border-box;
}
.bar-chart--trend .bar-col {
  max-width: none;
}
.bar-chart--trend .bar-track {
  height: 180px;
}
.bar-chart--trend .bar-label {
  font-size: 11px;
  white-space: nowrap;
  text-align: center;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}
.bar-chart--trend .bar-value {
  text-align: center;
}
.trend-range-info {
  margin-left: auto;
  font-size: 12px;
  color: var(--color-mute);
}

/* ===== Grid 布局 ===== */
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  flex: 1;
  min-height: 0;
}
.grid-item { min-height: 0; }

/* ===== 面板通用 ===== */
.panel {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.panel-header--trend {
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-start;
}
.trend-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.trend-quick-btns {
  display: flex;
  gap: 6px;
}
.trend-quick-btn {
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 6px;
  background: var(--color-canvas);
  color: var(--color-body);
  cursor: pointer;
  transition: all 0.15s;
}
.trend-quick-btn:hover {
  border-color: var(--color-link);
  color: var(--color-link);
}
.trend-quick-btn.active {
  border-color: var(--color-link);
  background: var(--color-link-bg-soft);
  color: var(--color-link);
  font-weight: 500;
}
.panel-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}
.trend-metric-hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--color-mute);
  padding: 2px 8px;
  background: var(--color-canvas-soft-2);
  border-radius: var(--radius-pill);
  line-height: 1.4;
}
.panel-tip {
  font-size: 12px;
  color: var(--color-mute);
}

/* ===== 柱状图 ===== */
.bar-chart {
  flex: 1;
  display: flex;
  align-items: flex-end;
  gap: 12px;
  min-height: 0;
  padding-bottom: 8px;
}
.bar-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 0;
}
.bar-value {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-body);
}
.bar-track {
  width: 100%;
  height: 140px;
  background: var(--color-canvas-soft-2);
  border-radius: 6px 6px 0 0;
  display: flex;
  align-items: flex-end;
  overflow: hidden;
}
.bar-track--trend {
  height: 180px;
}
.bar-fill {
  width: 100%;
  background: linear-gradient(180deg, #0070f3, #005bc4);
  border-radius: 6px 6px 0 0;
  transition: height 0.6s ease;
  min-height: 4px;
  transform-origin: bottom;
  animation: bar-rise 0.55s cubic-bezier(0.22, 1, 0.36, 1) both;
  animation-delay: calc(var(--bar-i, 0) * 18ms);
}
@keyframes bar-rise {
  from {
    transform: scaleY(0);
    opacity: 0;
  }
  to {
    transform: scaleY(1);
    opacity: 1;
  }
}
.bar-label {
  font-size: 11px;
  color: var(--color-mute);
}
.chart-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-mute);
  font-size: 13px;
}
.chat-summary {
  display: flex;
  gap: 24px;
  padding-top: 12px;
  border-top: 1px solid var(--color-hairline);
  font-size: 13px;
  color: var(--color-mute);
}
.chat-summary b {
  color: var(--color-ink);
}

/* ===== 状态条 ===== */
.status-bars {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}
.status-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.status-label {
  width: 56px;
  font-size: 12px;
  color: var(--color-mute);
  flex-shrink: 0;
}
.status-track {
  flex: 1;
  height: 8px;
  background: var(--color-canvas-soft-2);
  border-radius: 4px;
  overflow: hidden;
}
.status-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.6s ease;
}
.status-fill.draft { background: #a1a1aa; }
.status-fill.published { background: #16a34a; }
.status-fill.published_editing { background: #3b82f6; }
.status-fill.archived { background: #d97706; }
.status-fill.uploading { background: #d97706; }
.status-fill.uploaded { background: #a1a1aa; }
.status-fill.pending { background: #a1a1aa; }
.status-fill.processing { background: #3b82f6; }
.status-fill.completed { background: #16a34a; }
.status-fill.failed { background: #ef4444; }
.status-count {
  width: 32px;
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
}

/* ===== 最近列表 ===== */
.recent-list {
  margin-top: auto;
}
.recent-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-mute);
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid var(--color-hairline);
}
.recent-item:last-child { border-bottom: none; }
.recent-name {
  font-size: 13px;
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}
.knowledge-stats .recent-item {
  justify-content: flex-start;
}
.recent-tag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 100px;
}
.recent-tag.draft { background: var(--color-canvas-soft-2); color: var(--color-mute); }
.recent-tag.published { background: var(--color-success-bg); color: #16a34a; }
.recent-tag.published_editing { background: var(--color-info-bg); color: #2563eb; }
.recent-tag.archived { background: var(--color-warn-bg-deep); color: #d97706; }
[data-theme="dark"] .recent-tag.published { background: #052e16; }
[data-theme="dark"] .recent-tag.published_editing { background: #0c1a3d; }
[data-theme="dark"] .recent-tag.archived { background: #422006; }

/* ===== 知识库指标 ===== */
.knowledge-metrics {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}
.metric-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--color-canvas-soft-2);
  border-radius: 8px;
}
.metric-label {
  font-size: 13px;
  color: var(--color-mute);
}
.metric-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
}

/* ===== 模型指标 ===== */
.model-metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}
.model-metric-card {
  text-align: center;
  padding: 16px 12px;
  background: var(--color-canvas-soft-2);
  border-radius: 8px;
}
.model-metric-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-ink);
}
.model-metric-label {
  font-size: 12px;
  color: var(--color-mute);
  margin-top: 4px;
}
</style>
