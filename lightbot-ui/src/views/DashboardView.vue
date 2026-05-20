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

    <!-- Grid 布局 -->
    <div class="dashboard-grid">
      <!-- 对话统计（2列） -->
      <div class="grid-item chat-stats">
        <div class="panel">
          <div class="panel-header">
            <h3>对话趋势</h3>
            <span class="panel-tip">近7天消息量</span>
          </div>
          <div class="bar-chart">
            <div v-for="(d, i) in chatTrend" :key="i" class="bar-col">
              <div class="bar-value">{{ d.count }}</div>
              <div class="bar-track">
                <div class="bar-fill" :style="{ height: barHeight(d.count) + '%' }"></div>
              </div>
              <div class="bar-label">{{ formatDay(d.date) }}</div>
            </div>
            <div v-if="chatTrend.length === 0" class="chart-empty">暂无数据</div>
          </div>
          <div class="chat-summary">
            <span>总会话: <b>{{ chatStats.totalSessions ?? '-' }}</b></span>
            <span>总消息: <b>{{ chatStats.totalMessages ?? '-' }}</b></span>
          </div>
        </div>
      </div>

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
              <span :class="['recent-tag', a.status]">{{ statusLabel(a.status) }}</span>
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
          <div class="status-bars" v-if="docStatusList.length">
            <div class="recent-title">文档状态</div>
            <div v-for="s in docStatusList" :key="s.key" class="status-row">
              <span class="status-label">{{ s.label }}</span>
              <div class="status-track">
                <div class="status-fill" :class="s.key" :style="{ width: s.percent + '%' }"></div>
              </div>
              <span class="status-count">{{ s.count }}</span>
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
import { ref, computed, onMounted } from 'vue'
import { RobotOutlined, DatabaseOutlined, MessageOutlined, FileTextOutlined } from '@ant-design/icons-vue'
import { getDashboardBasic, getDashboardAgents, getDashboardKnowledge, getDashboardChat } from '../api/dashboard'

const basic = ref({})
const agentStats = ref({})
const knowledgeStats = ref({})
const chatStats = ref({})

const chatTrend = computed(() => chatStats.value.messagesPerDay || [])

const chatTrendMax = computed(() => {
  const counts = chatTrend.value.map(d => d.count || 0)
  return Math.max(...counts, 1)
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
  const sc = agentStats.value.statusCounts || {}
  const total = agentStats.value.total || 1
  const labels = { draft: '草稿', published: '已发布', archived: '已归档' }
  return Object.entries(sc).map(([key, count]) => ({
    key,
    label: labels[key] || key,
    count,
    percent: total > 0 ? (count / total) * 100 : 0,
  }))
})

const docStatusList = computed(() => {
  const sc = knowledgeStats.value.documentStatusCounts || {}
  const total = Object.values(sc).reduce((a, b) => a + b, 0) || 1
  const labels = { pending: '待处理', processing: '处理中', completed: '已完成', failed: '失败' }
  return Object.entries(sc).map(([key, count]) => ({
    key,
    label: labels[key] || key,
    count,
    percent: total > 0 ? (count / total) * 100 : 0,
  }))
})

function statusLabel(s) {
  const map = { draft: '草稿', published: '已发布', archived: '已归档' }
  return map[s] || s || '草稿'
}

async function loadAll() {
  const [b, a, k, c] = await Promise.all([
    getDashboardBasic().catch(() => ({ data: {} })),
    getDashboardAgents().catch(() => ({ data: {} })),
    getDashboardKnowledge().catch(() => ({ data: {} })),
    getDashboardChat().catch(() => ({ data: {} })),
  ])
  basic.value = b.data || {}
  agentStats.value = a.data || {}
  knowledgeStats.value = k.data || {}
  chatStats.value = c.data || {}
}

onMounted(loadAll)
</script>

<style scoped>
.dashboard {
  padding: 24px 32px;
  height: 100vh;
  overflow-y: auto;
  background: #fafafa;
}

/* ===== 顶部统计概览 ===== */
.stats-overview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
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

/* ===== Grid 布局 ===== */
.dashboard-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
}
.grid-item { min-height: 300px; }

/* ===== 面板通用 ===== */
.panel {
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
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
  min-height: 180px;
  padding-bottom: 8px;
}
.bar-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
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
.status-fill.archived { background: #d97706; }
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
}
.recent-tag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 100px;
}
.recent-tag.draft { background: #f4f4f5; color: #71717a; }
.recent-tag.published { background: #dcfce7; color: #16a34a; }
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
