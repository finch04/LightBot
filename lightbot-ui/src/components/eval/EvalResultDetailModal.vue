<template>
  <a-modal
    :open="open"
    width="1300px"
    :footer="null"
    :bodyStyle="{ maxHeight: '75vh', overflowY: 'auto' }"
    @cancel="$emit('update:open', false)"
  >
    <template #title>
      <span>评估结果详情</span>
      <QuestionCircleOutlined class="title-help-icon" @click="metricHelpVisible = true" />
    </template>
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

      <!-- AI 评语 -->
      <div v-if="result.analysis" class="result-analysis">
        <h4>AI 评语</h4>
        <div class="analysis-content">
          <MarkdownPreview :content="result.analysis" />
        </div>
      </div>

      <!-- 检索指标 -->
      <div v-if="retrievalMetrics" class="result-metrics">
        <h4>检索指标</h4>
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
          @resizeColumn="handleResizeColumn"
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
              <span v-if="record.answerScore != null" :style="{ color: record.answerScore >= 1 ? '#52c41a' : '#ff4d4f', fontWeight: 500 }">
                {{ record.answerScore.toFixed(1) }}
              </span>
              <span v-else>-</span>
            </template>
            <template v-if="column.key === 'retrievalScores'">
              <span>{{ formatRecallScore(record.retrievalScores) }}</span>
            </template>
          </template>
        </a-table>
      </div>
    </div>

    <!-- 指标帮助弹窗 -->
    <a-modal
      v-model:open="metricHelpVisible"
      title="评估指标说明"
      :footer="null"
      width="680px"
      :bodyStyle="{ maxHeight: '60vh', overflowY: 'auto' }"
    >
      <div class="metric-help-content">
        <div class="metric-help-item">
          <h4>Precision@K（精确率@K）</h4>
          <p>前K个检索结果中，命中标准片段的比例。</p>
          <p class="metric-formula">Precision@K = 命中数 / K</p>
          <p class="metric-example">例如：K=5时，返回5个结果中有3个是标准片段，Precision@5 = 3/5 = 60%</p>
        </div>
        <div class="metric-help-item">
          <h4>Recall@K（召回率@K）</h4>
          <p>标准片段中，被前K个检索结果命中的比例。</p>
          <p class="metric-formula">Recall@K = 命中数 / 标准片段总数</p>
          <p class="metric-example">例如：有4个标准片段，K=5时命中3个，Recall@5 = 3/4 = 75%</p>
        </div>
        <div class="metric-help-item">
          <h4>F1@K（F1分数@K）</h4>
          <p>Precision和Recall的调和平均数，综合衡量检索质量。</p>
          <p class="metric-formula">F1@K = 2 × Precision@K × Recall@K / (Precision@K + Recall@K)</p>
        </div>
        <div class="metric-help-item">
          <h4>MRR（平均倒数排名）</h4>
          <p>标准片段在检索结果中排名的倒数的平均值，衡量检索结果的排序质量。</p>
          <p class="metric-formula">MRR = (1/|Q|) × Σ(1/排名i)</p>
        </div>
        <div class="metric-help-item">
          <h4>Hit Rate（命中率）</h4>
          <p>至少命中一个标准片段的查询比例，衡量检索的覆盖能力。</p>
        </div>

        <a-divider style="margin: 8px 0" />

        <div class="metric-help-item">
          <h4>答案评分（0.0 ~ 1.0）</h4>
          <p>由 LLM 评判模型对 AI 生成答案与标准答案进行事实一致性评分，采用连续分值：</p>
          <div class="score-rubric">
            <div class="score-rubric-row"><span class="score-range">1.0</span><span class="score-desc">核心事实完全正确，与标准答案一致</span></div>
            <div class="score-rubric-row"><span class="score-range">0.8 ~ 0.9</span><span class="score-desc">核心事实正确，有少量非关键信息遗漏或多余</span></div>
            <div class="score-rubric-row"><span class="score-range">0.5 ~ 0.7</span><span class="score-desc">部分核心事实正确，但有明显遗漏或部分错误</span></div>
            <div class="score-rubric-row"><span class="score-range">0.2 ~ 0.4</span><span class="score-desc">仅少部分事实正确，大部分关键信息缺失或错误</span></div>
            <div class="score-rubric-row"><span class="score-range">0.0</span><span class="score-desc">完全错误、无关，或 AI 回答"无法回答"</span></div>
          </div>
          <p class="metric-example">评判时忽略措辞、格式差异，只关注核心事实是否准确。</p>
        </div>
      </div>
    </a-modal>
  </a-modal>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { message } from 'ant-design-vue'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
import { getEvalResultDetail } from '../../api/knowledgeEval'
import MarkdownPreview from '../MarkdownPreview.vue'

const props = defineProps({
  open: Boolean,
  knowledgeId: { type: String, required: true },
  result: Object,
})
const emit = defineEmits(['update:open'])

const details = ref([])
const detailLoading = ref(false)
const errorOnly = ref(false)
const detailPagination = ref({ current: 1, pageSize: 10, total: 0, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` })
const metricHelpVisible = ref(false)

const statusMap = { RUNNING: '运行中', COMPLETED: '已完成', FAILED: '失败' }
const statusColor = { RUNNING: 'blue', COMPLETED: 'green', FAILED: 'red' }

const detailColumns = [
  { title: '问题', dataIndex: 'query', key: 'query', width: 220, minWidth: 120, ellipsis: true },
  { title: 'AI 答案', dataIndex: 'generatedAnswer', key: 'generatedAnswer', width: 260, minWidth: 120, ellipsis: true },
  { title: '检索指标', key: 'retrievalScores', width: 120, minWidth: 80 },
  { title: '答案评分', key: 'answerScore', width: 90, minWidth: 70 },
  { title: '评分理由', dataIndex: 'answerReasoning', key: 'answerReasoning', minWidth: 120, ellipsis: true },
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

/** 拖动列宽 */
function handleResizeColumn(w, col) {
  col.width = w
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
.title-help-icon {
  margin-left: 8px;
  color: #999;
  font-size: 14px;
  cursor: pointer;
  transition: color 0.2s;
}
.title-help-icon:hover { color: #1890ff; }
.metrics-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.metrics-col { display: flex; flex-direction: column; gap: 4px; }
.metrics-row { display: flex; align-items: center; gap: 8px; }
.metric-label { min-width: 80px; font-size: 12px; color: #666; }
.result-detail-table h4 { margin: 0; }
.result-detail-table :deep(.ant-table-resize-handle) {
  cursor: col-resize;
}
.result-detail-table :deep(.ant-table-resize-handle:hover) {
  background-color: #1890ff;
}
.error-text { color: #dc2626; font-size: 13px; word-break: break-all; }
.cell-ellipsis {
  display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical;
  overflow: hidden; text-overflow: ellipsis; word-break: break-all;
}
.result-analysis {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 6px;
  padding: 12px 16px;
}
.result-analysis h4 {
  margin: 0 0 8px;
  color: #389e0d;
  font-size: 14px;
}
.analysis-content {
}
.metric-help-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.metric-help-item h4 {
  margin: 0 0 4px;
  font-size: 14px;
  color: #262626;
}
.metric-help-item p {
  margin: 2px 0;
  font-size: 13px;
  color: #595959;
  line-height: 1.6;
}
.metric-formula {
  font-family: 'Courier New', monospace;
  background: #f5f5f5;
  padding: 4px 8px;
  border-radius: 4px;
  color: #1890ff !important;
  font-size: 12px !important;
}
.metric-example {
  color: #8c8c8c !important;
  font-size: 12px !important;
}
.metric-help-note {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}
.metric-help-note p {
  font-size: 12px;
  color: #8c8c8c;
}
.metric-help-note a {
  color: #1890ff;
  text-decoration: none;
}
.metric-help-note a:hover {
  text-decoration: underline;
}
.score-rubric {
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  overflow: hidden;
  margin: 8px 0;
}
.score-rubric-row {
  display: flex;
  align-items: baseline;
  border-bottom: 1px solid #f0f0f0;
}
.score-rubric-row:last-child { border-bottom: none; }
.score-range {
  width: 80px;
  flex-shrink: 0;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #1890ff;
  background: #fafafa;
  text-align: center;
}
.score-desc {
  flex: 1;
  padding: 6px 12px;
  font-size: 12px;
  color: #595959;
}
</style>
