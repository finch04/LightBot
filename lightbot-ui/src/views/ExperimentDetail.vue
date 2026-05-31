<template>
  <div class="page">
    <div class="page-header">
      <div>
        <button class="btn-back" @click="router.push('/eval/experiments')">
          <ArrowLeftOutlined /> 返回
        </button>
        <h1 class="page-title">{{ experiment?.name || '实验详情' }}</h1>
        <p class="page-desc">{{ experiment?.description || '' }}</p>
      </div>
      <div class="page-header-actions">
        <a-tag :color="statusColor" style="font-size: 14px; padding: 4px 12px;">
          {{ statusLabel }}
        </a-tag>
        <button
          v-if="experimentStatus === 'running'"
          class="btn-outline"
          @click="handleStop"
        >
          <PauseCircleOutlined /> 停止
        </button>
        <button
          v-if="experimentStatus === 'stopped'"
          class="btn-outline"
          @click="handleRestart"
        >
          <PlayCircleOutlined /> 重启
        </button>
      </div>
    </div>

    <!-- 实验信息卡片 -->
    <div class="info-cards">
      <div class="info-card">
        <div class="info-label">评测集</div>
        <div class="info-value">{{ experiment?.datasetName || '-' }}</div>
      </div>
      <div class="info-card">
        <div class="info-label">Prompt</div>
        <div class="info-value">{{ experiment?.promptKey || '-' }} {{ experiment?.promptVersion ? 'v' + experiment.promptVersion : '' }}</div>
      </div>
      <div class="info-card">
        <div class="info-label">评估器</div>
        <div class="info-value">{{ experiment?.evaluatorName || '-' }} {{ experiment?.evaluatorVersion ? 'v' + experiment.evaluatorVersion : '' }}</div>
      </div>
      <div class="info-card">
        <div class="info-label">创建时间</div>
        <div class="info-value">{{ formatTime(experiment?.createTime) }}</div>
      </div>
    </div>

    <!-- Tab 切换 -->
    <a-tabs v-model:activeKey="activeTab" @change="onTabChange">
      <!-- 概览 Tab -->
      <a-tab-pane key="overview" tab="概览">
        <div class="evaluator-cards">
          <div
            v-for="ev in evaluatorResults"
            :key="ev.evaluatorName"
            class="evaluator-card"
          >
            <div class="evaluator-card-header">
              <span class="evaluator-name">{{ ev.evaluatorName }}</span>
              <span class="evaluator-version" v-if="ev.evaluatorVersion">v{{ ev.evaluatorVersion }}</span>
            </div>
            <div class="evaluator-score" :class="scoreClass(ev.avgScore)">
              <span class="score-value">{{ ev.avgScore?.toFixed(2) ?? '-' }}</span>
              <span class="score-label">平均分</span>
            </div>
            <a-progress
              :percent="Math.round((ev.avgScore || 0) * 100)"
              :stroke-color="progressColor(ev.avgScore)"
              :show-info="false"
            />
            <div class="evaluator-meta">
              <span>已评测 {{ ev.evaluatedCount ?? 0 }} / {{ ev.totalCount ?? 0 }}</span>
            </div>
          </div>
        </div>
        <div v-if="evaluatorResults.length === 0" class="empty-state">
          暂无评测结果
        </div>
      </a-tab-pane>

      <!-- 评测结果 Tab -->
      <a-tab-pane key="results" tab="评测结果">
        <a-tabs v-if="evaluatorResults.length" v-model:activeKey="resultEvaluatorTab" type="card" size="small">
          <a-tab-pane
            v-for="ev in evaluatorResults"
            :key="ev.evaluatorName"
            :tab="ev.evaluatorName"
          >
            <a-table
              :dataSource="getResultsForEvaluator(ev.evaluatorName)"
              :columns="resultColumns"
              :pagination="{ pageSize: 20, showTotal: (total) => `共 ${total} 条` }"
              rowKey="id"
              size="middle"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'input'">
                  <a-tooltip :title="record.input">
                    <span class="cell-preview">{{ truncate(record.input, 80) }}</span>
                  </a-tooltip>
                </template>
                <template v-if="column.key === 'actualOutput'">
                  <a-tooltip :title="record.actualOutput">
                    <span class="cell-preview">{{ truncate(record.actualOutput, 80) }}</span>
                  </a-tooltip>
                </template>
                <template v-if="column.key === 'referenceOutput'">
                  <a-tooltip :title="record.referenceOutput">
                    <span class="cell-preview">{{ truncate(record.referenceOutput, 80) }}</span>
                  </a-tooltip>
                </template>
                <template v-if="column.key === 'score'">
                  <span class="score-tag" :class="scoreClass(record.score)">
                    {{ record.score?.toFixed(2) ?? '-' }}
                  </span>
                </template>
                <template v-if="column.key === 'reason'">
                  <a-tooltip :title="record.reason">
                    <span class="cell-preview">{{ truncate(record.reason, 60) }}</span>
                  </a-tooltip>
                </template>
              </template>
            </a-table>
          </a-tab-pane>
        </a-tabs>
        <div v-else class="empty-state">暂无评测结果</div>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined, PauseCircleOutlined, PlayCircleOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import {
  getExperiment, stopExperiment, restartExperiment,
  getExperimentResults, getExperimentDetailResults,
} from '../api/experiment'

const route = useRoute()
const router = useRouter()
const experimentId = route.params.id
const experiment = ref(null)
const activeTab = ref('overview')
const resultEvaluatorTab = ref('')
const evaluatorResults = ref([])
const detailResults = ref([])

const resultColumns = [
  { title: '输入', dataIndex: 'input', key: 'input', width: 200 },
  { title: '实际输出', dataIndex: 'actualOutput', key: 'actualOutput', width: 200 },
  { title: '期望输出', dataIndex: 'referenceOutput', key: 'referenceOutput', width: 200 },
  { title: '评分', dataIndex: 'score', key: 'score', width: 80 },
  { title: '评分理由', dataIndex: 'reason', key: 'reason' },
]

const experimentStatus = computed(() => experiment.value?.status?.code || experiment.value?.status || '')

const statusColor = computed(() => {
  const map = { created: '#d9d9d9', running: '#1890ff', completed: '#52c41a', failed: '#ff4d4f', stopped: '#d9d9d9' }
  return map[experimentStatus.value] || '#d9d9d9'
})

const statusLabel = computed(() => {
  const map = { created: '已创建', running: '运行中', completed: '已完成', failed: '失败', stopped: '已停止' }
  return map[experimentStatus.value] || '未知'
})

onMounted(async () => {
  await loadExperiment()
  await loadResults()
})

async function loadExperiment() {
  const res = await getExperiment(experimentId)
  experiment.value = res.data || {}
}

async function loadResults() {
  try {
    const res = await getExperimentResults(experimentId)
    evaluatorResults.value = res.data || []
    if (evaluatorResults.value.length > 0) {
      resultEvaluatorTab.value = evaluatorResults.value[0].evaluatorName
    }
  } catch { /* ignore */ }
}

async function onTabChange(tab) {
  if (tab === 'results' && detailResults.value.length === 0) {
    try {
      const res = await getExperimentDetailResults(experimentId)
      detailResults.value = res.data?.records || []
    } catch { /* ignore */ }
  }
}

function getResultsForEvaluator(evaluatorName) {
  return detailResults.value.filter(r => r.evaluatorName === evaluatorName) || []
}

function handleStop() {
  Modal.confirm({
    title: '确认停止',
    content: '停止后实验将不再继续运行，是否继续？',
    okText: '确认停止',
    cancelText: '取消',
    async onOk() {
      await stopExperiment(experimentId)
      message.success('实验已停止')
      loadExperiment()
    },
  })
}

function handleRestart() {
  Modal.confirm({
    title: '确认重启',
    content: '将重新运行该实验，是否继续？',
    okText: '确认重启',
    cancelText: '取消',
    async onOk() {
      await restartExperiment(experimentId)
      message.success('实验已重启')
      loadExperiment()
    },
  })
}

function scoreClass(score) {
  if (score == null) return ''
  if (score >= 0.8) return 'score-high'
  if (score >= 0.5) return 'score-mid'
  return 'score-low'
}

function progressColor(score) {
  if (score == null) return '#d9d9d9'
  if (score >= 0.8) return '#52c41a'
  if (score >= 0.5) return '#faad14'
  return '#ff4d4f'
}

function truncate(str, len) {
  if (!str) return ''
  return str.length > len ? str.substring(0, len) + '...' : str
}

function formatTime(t) {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}
</script>

<style scoped>
.page {
  padding: 32px;
  height: 100vh;
  overflow-y: auto;
  background: #fafafa;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 32px;
}
.page-header-left {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.btn-back {
  background: none;
  border: none;
  color: #71717a;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
}
.btn-back:hover { color: #0070f3; }
.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #171717;
  margin-bottom: 4px;
}
.page-desc {
  font-size: 14px;
  color: #71717a;
}
.page-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.btn-outline {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: transparent;
  border: 1px solid #d9d9d9;
  border-radius: 100px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-outline:hover {
  border-color: #0070f3;
  color: #0070f3;
}

/* 信息卡片 */
.info-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 32px;
}
.info-card {
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  padding: 16px 20px;
}
.info-label {
  font-size: 13px;
  color: #71717a;
  margin-bottom: 4px;
}
.info-value {
  font-size: 15px;
  font-weight: 600;
  color: #171717;
}

/* 评估器卡片 */
.evaluator-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.evaluator-card {
  background: #fff;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  padding: 20px;
}
.evaluator-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.evaluator-name {
  font-size: 15px;
  font-weight: 600;
  color: #171717;
}
.evaluator-version {
  font-size: 12px;
  color: #0070f3;
  background: #e8f4ff;
  padding: 2px 8px;
  border-radius: 100px;
}
.evaluator-score {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 12px;
}
.evaluator-score .score-value {
  font-size: 32px;
  font-weight: 700;
  color: #171717;
}
.evaluator-score .score-label {
  font-size: 13px;
  color: #71717a;
}
.evaluator-score.score-high .score-value { color: #52c41a; }
.evaluator-score.score-mid .score-value { color: #faad14; }
.evaluator-score.score-low .score-value { color: #ff4d4f; }
.evaluator-meta {
  margin-top: 8px;
  font-size: 12px;
  color: #a1a1aa;
}

/* 评分标签 */
.score-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 100px;
  font-size: 13px;
  font-weight: 600;
}
.score-tag.score-high { background: #dcfce7; color: #16a34a; }
.score-tag.score-mid { background: #fef3c7; color: #d97706; }
.score-tag.score-low { background: #fee2e2; color: #dc2626; }

.cell-preview {
  font-size: 13px;
  color: #71717a;
}
.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #a1a1aa;
}
</style>
