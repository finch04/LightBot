<template>
  <div class="dashboard">
    <!-- 顶部统计概览 -->
    <div class="stats-overview">
      <div class="stat-card">
        <div class="stat-icon agent-icon"><RobotOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ basic.agentCount ?? '-' }}</div>
          <div class="stat-label">Agent</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon knowledge-icon"><DatabaseOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ basic.knowledgeCount ?? '-' }}</div>
          <div class="stat-label">知识库</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon session-icon"><MessageOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ basic.sessionCount ?? '-' }}</div>
          <div class="stat-label">对话会话</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon message-icon"><FileTextOutlined /></div>
        <div class="stat-info">
          <div class="stat-value">{{ basic.messageCount ?? '-' }}</div>
          <div class="stat-label">消息总数</div>
        </div>
      </div>
    </div>

    <!-- 对话趋势（整行） -->
    <div class="trend-row">
      <div class="panel panel-trend">
        <div class="panel-header panel-header--trend">
          <h3>对话趋势</h3>
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
              :key="i"
              class="bar-col bar-col--trend"
              :style="trendColStyle"
            >
              <div class="bar-value">{{ d.count }}</div>
              <div class="bar-track" :style="trendTrackStyle">
                <div class="bar-fill" :style="{ height: barHeight(d.count) + '%' }"></div>
              </div>
              <div class="bar-label">{{ formatDay(d.date) }}</div>
            </div>
            <div v-if="chatTrend.length === 0" class="chart-empty">暂无数据</div>
          </div>
        </div>
        <div class="chat-summary">
          <span>总会话: <b>{{ chatStats.totalSessions ?? '-' }}</b></span>
          <span>总消息: <b>{{ chatStats.totalMessages ?? '-' }}</b></span>
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { RobotOutlined, DatabaseOutlined, MessageOutlined, FileTextOutlined } from '@ant-design/icons-vue'
import { getDashboardBasic, getDashboardAgents, getDashboardKnowledge, getDashboardChat } from '../api/dashboard'

const basic = ref({})
const agentStats = ref({})
const knowledgeStats = ref({})
const chatStats = ref({})

const trendQuickDays = [7, 14, 30]
const trendDays = ref(7)
const trendMode = ref('days')
const trendDateRange = ref(null)
const trendScrollRef = ref(null)
const trendContainerWidth = ref(0)
let trendResizeObserver = null

const chatTrend = computed(() => chatStats.value.messagesPerDay || [])

const chatTrendMax = computed(() => {
  const counts = chatTrend.value.map(d => d.count || 0)
  return Math.max(...counts, 1)
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
  const [b, a, k] = await Promise.all([
    getDashboardBasic().catch(() => ({ data: {} })),
    getDashboardAgents().catch(() => ({ data: {} })),
    getDashboardKnowledge().catch(() => ({ data: {} })),
  ])
  basic.value = b.data || {}
  agentStats.value = a.data || {}
  knowledgeStats.value = k.data || {}
  await loadChatStats()
  await nextTick()
  updateTrendContainerWidth()
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
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fafafa;
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
  background: #fff;
  border: 1px solid #ebebeb;
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
.agent-icon { background: #ede9fe; color: #7c3aed; }
.knowledge-icon { background: #dbeafe; color: #2563eb; }
.session-icon { background: #dcfce7; color: #16a34a; }
.message-icon { background: #fef3c7; color: #d97706; }
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #171717;
  line-height: 1.2;
}
.stat-label {
  font-size: 13px;
  color: #71717a;
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
.bar-chart-scroll::-webkit-scrollbar {
  height: 6px;
}
.bar-chart-scroll::-webkit-scrollbar-thumb {
  background: #d4d4d8;
  border-radius: 3px;
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
  color: #a1a1aa;
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
  background: #fff;
  border: 1px solid #ebebeb;
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
  border: 1px solid #e4e4e7;
  border-radius: 6px;
  background: #fff;
  color: #52525b;
  cursor: pointer;
  transition: all 0.15s;
}
.trend-quick-btn:hover {
  border-color: #0070f3;
  color: #0070f3;
}
.trend-quick-btn.active {
  border-color: #0070f3;
  background: #eff6ff;
  color: #0070f3;
  font-weight: 500;
}
.panel-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: #171717;
  margin: 0;
}
.panel-tip {
  font-size: 12px;
  color: #a1a1aa;
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
  color: #52525b;
}
.bar-track {
  width: 100%;
  height: 140px;
  background: #f4f4f5;
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
}
.bar-label {
  font-size: 11px;
  color: #a1a1aa;
}
.chart-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a1a1aa;
  font-size: 13px;
}
.chat-summary {
  display: flex;
  gap: 24px;
  padding-top: 12px;
  border-top: 1px solid #f4f4f5;
  font-size: 13px;
  color: #71717a;
}
.chat-summary b {
  color: #171717;
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
  color: #71717a;
  flex-shrink: 0;
}
.status-track {
  flex: 1;
  height: 8px;
  background: #f4f4f5;
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
  color: #171717;
}

/* ===== 最近列表 ===== */
.recent-list {
  margin-top: auto;
}
.recent-title {
  font-size: 12px;
  font-weight: 500;
  color: #a1a1aa;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid #f4f4f5;
}
.recent-item:last-child { border-bottom: none; }
.recent-name {
  font-size: 13px;
  color: #171717;
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
.recent-tag.draft { background: #f4f4f5; color: #71717a; }
.recent-tag.published { background: #dcfce7; color: #16a34a; }
.recent-tag.published_editing { background: #dbeafe; color: #2563eb; }
.recent-tag.archived { background: #fef3c7; color: #d97706; }

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
  background: #f9fafb;
  border-radius: 8px;
}
.metric-label {
  font-size: 13px;
  color: #71717a;
}
.metric-value {
  font-size: 16px;
  font-weight: 600;
  color: #171717;
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
  background: #f9fafb;
  border-radius: 8px;
}
.model-metric-value {
  font-size: 24px;
  font-weight: 700;
  color: #171717;
}
.model-metric-label {
  font-size: 12px;
  color: #71717a;
  margin-top: 4px;
}
</style>
