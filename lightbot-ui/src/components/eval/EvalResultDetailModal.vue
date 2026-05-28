<template>
  <a-modal
    :open="open"
    title="评估结果详情"
    width="1300px"
    :footer="null"
    @cancel="$emit('update:open', false)"
  >
    <div v-if="result" class="result-detail">
      <!-- 基本信息 -->
      <div class="result-info">
        <a-descriptions :column="2" size="small" bordered>
          <a-descriptions-item label="基准名称" :span="2">{{ result.benchmarkName }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="statusColor[result.status]">{{ statusMap[result.status] || result.status }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="综合评分">
            <span v-if="result.overallScore != null" :style="{ color: scoreColor(result.overallScore) }">
              {{ (result.overallScore * 100).toFixed(1) }}%
            </span>
            <span v-else>-</span>
          </a-descriptions-item>
          <a-descriptions-item label="答题准确率">
            <a-progress
              v-if="answerMetrics"
              :percent="Math.round((answerMetrics.accuracy || 0) * 100)"
              size="small"
              :stroke-color="scoreColor(answerMetrics.accuracy || 0)"
            />
            <span v-else>-</span>
          </a-descriptions-item>
          <a-descriptions-item label="答对/总数">
            <span v-if="answerMetrics">{{ answerMetrics.correct || 0 }} / {{ answerMetrics.total || 0 }}</span>
            <span v-else>-</span>
          </a-descriptions-item>
          <a-descriptions-item label="耗时">{{ formatDuration(result.durationMs) }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ formatTime(result.createTime) }}</a-descriptions-item>
          <a-descriptions-item v-if="result.status === 'FAILED' && result.error" label="错误信息" :span="2">
            <span class="error-text">{{ result.error }}</span>
          </a-descriptions-item>
        </a-descriptions>
      </div>

      <!-- 检索指标 -->
      <div v-if="retrievalMetrics" class="result-metrics">
        <h4>
          检索指标
          <a-tooltip :overlayStyle="{ maxWidth: '360px' }">
            <template #title>
              Precision@K：前K个检索结果中命中的比例<br/>
              Recall@K：标准片段中被前K个检索结果命中的比例<br/>
              F1@K：Precision 和 Recall 的调和平均
            </template>
            <QuestionCircleOutlined class="metric-help-icon" />
          </a-tooltip>
        </h4>
        <div class="metrics-columns">
          <div class="metrics-col">
            <div class="metrics-row" v-for="(val, key) in retrievalLeft" :key="key">
              <span class="metric-label">{{ key }}</span>
              <a-progress :percent="Math.round(val * 100)" size="small" :stroke-color="scoreColor(val)" />
            </div>
          </div>
          <div class="metrics-col">
            <div class="metrics-row" v-for="(val, key) in retrievalRight" :key="key">
              <span class="metric-label">{{ key }}</span>
              <a-progress :percent="Math.round(val * 100)" size="small" :stroke-color="scoreColor(val)" />
            </div>
          </div>
        </div>
      </div>

      <!-- 明细表格 -->
      <div class="result-detail-table">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
          <h4 style="margin: 0;">评估明细</h4>
          <a-checkbox v-model:checked="errorOnly" @change="loadDetails">仅显示错误</a-checkbox>
        </div>
        <a-table
          :dataSource="details"
          :columns="detailColumns"
          :pagination="detailPagination"
          @change="handleTableChange"
          size="small"
          :loading="detailLoading"
          :scroll="{ y: 400, x: 1200 }"
          resizable
        >
          <template #bodyCell="{ column, text, record }">
            <template v-if="column.key === 'query'">
              <a-tooltip placement="topLeft" :overlayStyle="{ maxWidth: '480px' }">
                <template #title>{{ text }}</template>
                <span class="cell-ellipsis">{{ text }}</span>
              </a-tooltip>
            </template>
            <template v-if="column.key === 'generatedAnswer'">
              <a-tooltip placement="topLeft" :overlayStyle="{ maxWidth: '480px' }">
                <template #title>{{ text }}</template>
                <span class="cell-ellipsis">{{ text }}</span>
              </a-tooltip>
            </template>
            <template v-if="column.key === 'answerReasoning'">
              <a-tooltip placement="topLeft" :overlayStyle="{ maxWidth: '480px' }">
                <template #title>{{ text }}</template>
                <span class="cell-ellipsis">{{ text }}</span>
              </a-tooltip>
            </template>
            <template v-if="column.key === 'answerScore'">
              <a-tag v-if="record.answerScore != null" :color="record.answerScore >= 1 ? 'green' : 'red'">
                {{ record.answerScore >= 1 ? '正确' : '错误' }}
              </a-tag>
              <span v-else>-</span>
            </template>
            <template v-if="column.key === 'retrievalScores'">
              <span>{{ formatRecallScore(record.retrievalScores) }}</span>
            </template>
          </template>
        </a-table>
      </div>
    </div>
  </a-modal>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { message } from 'ant-design-vue'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
import { getEvalResultDetail } from '../../api/knowledgeEval'

const props = defineProps({
  open: Boolean,
  knowledgeId: { type: String, required: true },
  result: Object,
})
const emit = defineEmits(['update:open'])

const details = ref([])
const detailLoading = ref(false)
const errorOnly = ref(false)
const detailPagination = ref({ current: 1, pageSize: 10, total: 0 })

const statusMap = { RUNNING: '运行中', COMPLETED: '已完成', FAILED: '失败' }
const statusColor = { RUNNING: 'blue', COMPLETED: 'green', FAILED: 'red' }

const detailColumns = [
  { title: '#', dataIndex: 'sortOrder', width: 50, customRender: ({ index }) => index + 1 },
  { title: '问题', dataIndex: 'query', key: 'query', width: 220, ellipsis: true },
  { title: 'AI 答案', dataIndex: 'generatedAnswer', key: 'generatedAnswer', width: 260, ellipsis: true },
  { title: '检索指标', key: 'retrievalScores', width: 120 },
  { title: '答案评分', key: 'answerScore', width: 80 },
  { title: '评分理由', dataIndex: 'answerReasoning', key: 'answerReasoning', ellipsis: true },
]

const retrievalMetrics = computed(() => {
  if (!props.result?.retrievalJson) return null
  try { return JSON.parse(props.result.retrievalJson) } catch { return null }
})

const answerMetrics = computed(() => {
  if (!props.result?.answerJson) return null
  try { return JSON.parse(props.result.answerJson) } catch { return null }
})

/** 将检索指标拆为左右两列（前半 / 后半） */
function splitMetrics(obj) {
  if (!obj) return [{}, {}]
  const entries = Object.entries(obj)
  const mid = Math.ceil(entries.length / 2)
  return [Object.fromEntries(entries.slice(0, mid)), Object.fromEntries(entries.slice(mid))]
}

const retrievalLeft = computed(() => splitMetrics(retrievalMetrics.value)[0])
const retrievalRight = computed(() => splitMetrics(retrievalMetrics.value)[1])

function parseScores(json) {
  if (!json || json === '{}') return null
  try { return JSON.parse(json) } catch { return null }
}

function formatRecallScore(json) {
  const scores = parseScores(json)
  if (!scores || scores['recall@5'] == null) return '-'
  return 'R@5: ' + (scores['recall@5'] * 100).toFixed(0) + '%'
}

function scoreColor(score) {
  if (score >= 0.8) return '#52c41a'
  if (score >= 0.5) return '#faad14'
  return '#ff4d4f'
}

function formatDuration(ms) {
  if (!ms) return '-'
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(1) + 's'
}

function formatTime(time) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

async function loadDetails() {
  if (!props.result) return
  detailLoading.value = true
  try {
    const res = await getEvalResultDetail(props.knowledgeId, props.result.id, {
      pageNum: detailPagination.value.current,
      pageSize: detailPagination.value.pageSize,
      errorOnly: errorOnly.value,
    })
    details.value = res.data?.records || []
    detailPagination.value.total = res.data?.total || 0
  } catch (e) {
    message.error('加载明细失败')
  } finally {
    detailLoading.value = false
  }
}

function handleTableChange(pag) {
  detailPagination.value.current = pag.current
  detailPagination.value.pageSize = pag.pageSize
  loadDetails()
}

watch(() => props.open, (val) => {
  if (val && props.result) {
    detailPagination.value.current = 1
    errorOnly.value = false
    loadDetails()
  }
})
</script>

<style scoped>
.result-detail { display: flex; flex-direction: column; gap: 16px; }
.result-metrics h4 { margin: 0 0 8px; }
.metric-help-icon { margin-left: 4px; color: #999; font-size: 13px; cursor: help; }
.metric-help-icon:hover { color: #1890ff; }
.metrics-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.metrics-col { display: flex; flex-direction: column; gap: 4px; }
.metrics-row { display: flex; align-items: center; gap: 8px; }
.metric-label { min-width: 80px; font-size: 12px; color: #666; }
.result-detail-table h4 { margin: 0; }
.error-text { color: #dc2626; font-size: 13px; word-break: break-all; }
.cell-ellipsis {
  display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical;
  overflow: hidden; text-overflow: ellipsis; word-break: break-all;
}
</style>
