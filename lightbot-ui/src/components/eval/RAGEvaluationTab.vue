<template>
  <div class="rag-eval-tab">
    <div class="eval-desc">
      基于评估基准对知识库的检索质量和回答准确性进行量化评估，支持 Precision/Recall/F1@K 指标和 LLM 评判
    </div>
    <!-- 工具栏 -->
    <div class="eval-toolbar">
      <a-select
        v-model:value="selectedBenchmarkId"
        placeholder="选择评估基准"
        style="width: 200px"
        :loading="benchmarksLoading"
        allow-clear
        show-search
        :filter-option="filterBenchmark"
      >
        <a-select-option v-for="bm in benchmarks" :key="bm.id" :value="bm.id">
          {{ bm.name }} ({{ bm.questionCount }}题)
        </a-select-option>
      </a-select>
      <div class="eval-toolbar-right">
        <!-- <a-button size="small" @click="refreshAll" :loading="benchmarksLoading || resultsLoading">
          <template #icon><ReloadOutlined /></template>
        </a-button> -->
        <a-button type="primary" size="small" :disabled="!canRun" :loading="runLoading" @click="handleRun"
          :style="!canRun ? { background: '#d9d9d9', borderColor: '#d9d9d9', color: '#fff' } : {}">
          <template #icon><PlayCircleOutlined /></template> 开始评估
        </a-button>
      </div>
    </div>
    <!-- 模型选择（同一行） -->
    <div class="eval-model-row">
      <a-row :gutter="16">
        <a-col :span="12">
          <div class="eval-model-item">
            <label class="eval-model-label">答案生成模型{{ selectedBenchmarkHasGoldAnswer ? '（可选）' : '' }}</label>
            <ModelSelect
              v-model="answerProviderId"
              model-type="llm"
              placeholder="不选则仅评估检索质量"
            />
          </div>
        </a-col>
        <a-col :span="12">
          <div class="eval-model-item">
            <label class="eval-model-label">答案评判模型{{ selectedBenchmarkHasGoldAnswer ? '（可选）' : '' }}</label>
            <ModelSelect
              v-model="judgeProviderId"
              model-type="llm"
              placeholder="不选则仅评估检索质量"
            />
          </div>
        </a-col>
      </a-row>
    </div>

    <!-- 评估历史 -->
    <div class="eval-history">
      <div class="eval-history-header">
        <span class="eval-history-title">评估历史</span>
        <a-button size="small" @click="loadResults" :loading="resultsLoading">
          <template #icon><ReloadOutlined /></template>
        </a-button>
      </div>
      <a-table
        :dataSource="results"
        :columns="resultColumns"
        :pagination="resultPagination"
        @change="handleResultTableChange"
        size="small"
        :loading="resultsLoading"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'overallScore'">
            <a-tag v-if="record.overallScore != null" :color="scoreColor(record.overallScore)">
              {{ (record.overallScore * 100).toFixed(1) }}%
            </a-tag>
            <span v-else>-</span>
          </template>
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'COMPLETED' ? 'green' : record.status === 'FAILED' ? 'red' : 'blue'">
              {{ record.status === 'RUNNING' ? '运行中' : record.status === 'COMPLETED' ? '已完成' : '失败' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'createTime'">
            {{ formatTime(record.createTime) }}
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-tooltip title="查看详情">
                <EyeOutlined class="action-icon" @click="viewDetail(record)" />
              </a-tooltip>
              <a-popconfirm title="确定删除？" @confirm="handleDeleteResult(record)">
                <DeleteOutlined class="action-icon action-danger" />
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <!-- 结果详情弹窗 -->
    <EvalResultDetailModal
      v-model:open="detailVisible"
      :knowledge-id="knowledgeId"
      :result="selectedResult"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { ReloadOutlined, PlayCircleOutlined, EyeOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import {
  listBenchmarks, listEvalResults, runEvaluation, deleteEvalResult,
} from '../../api/knowledgeEval'
import ModelSelect from '../ModelSelect.vue'
import EvalResultDetailModal from './EvalResultDetailModal.vue'

const props = defineProps({ knowledgeId: { type: String, required: true } })

// 基准
const benchmarks = ref([])
const benchmarksLoading = ref(false)
const selectedBenchmarkId = ref(null)

// 模型选择（复合值 "providerId:modelId"）
const answerProviderId = ref(null)
const judgeProviderId = ref(null)

// 运行
const runLoading = ref(false)

const canRun = computed(() => !!selectedBenchmarkId.value)

const selectedBenchmarkHasGoldAnswer = computed(() => {
  const bm = benchmarks.value.find(b => b.id === selectedBenchmarkId.value)
  return bm?.hasGoldAnswer
})

// 结果
const results = ref([])
const resultsLoading = ref(false)
const resultPagination = ref({ current: 1, pageSize: 10, total: 0 })

// 详情
const detailVisible = ref(false)
const selectedResult = ref(null)

const resultColumns = [
  { title: '基准名称', dataIndex: 'benchmarkName', ellipsis: true },
  { title: '状态', key: 'status', width: 80 },
  { title: '综合评分', key: 'overallScore', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 160 },
  { title: '操作', key: 'action', width: 80 },
]

function formatTime(time) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

function scoreColor(score) {
  if (score >= 0.8) return 'green'
  if (score >= 0.5) return 'orange'
  return 'red'
}

function filterBenchmark(input, option) {
  return option.children?.[0]?.children?.toLowerCase().includes(input.toLowerCase())
}

async function refreshAll() {
  await Promise.all([loadBenchmarks(), loadResults()])
}

async function loadBenchmarks() {
  benchmarksLoading.value = true
  try {
    const res = await listBenchmarks(props.knowledgeId)
    benchmarks.value = res.data || []
  } catch (e) {
    message.error('加载基准列表失败')
  } finally {
    benchmarksLoading.value = false
  }
}

async function loadResults() {
  resultsLoading.value = true
  try {
    const res = await listEvalResults(props.knowledgeId, {
      pageNum: resultPagination.value.current,
      pageSize: resultPagination.value.pageSize,
    })
    results.value = res.data?.records || []
    resultPagination.value.total = res.data?.total || 0
  } catch (e) {
    message.error('加载评估历史失败')
  } finally {
    resultsLoading.value = false
  }
}

async function handleRun() {
  if (!selectedBenchmarkId.value) return message.warning('请先选择评估基准')
  // 两个模型必须同时选或同时不选
  const hasAnswer = !!answerProviderId.value
  const hasJudge = !!judgeProviderId.value
  if (hasAnswer !== hasJudge) {
    return message.warning('生成模型和评判模型必须同时选择或同时不选')
  }
  if (!hasAnswer) {
    try {
      await new Promise((resolve, reject) => {
        Modal.confirm({
          title: '仅评估检索质量',
          content: '未选择答案生成/评判模型，将只评估检索指标（Precision/Recall/F1@K），是否继续？',
          onOk: resolve,
          onCancel: reject,
        })
      })
    } catch { return }
  }
  runLoading.value = true
  try {
    const params = { benchmarkId: selectedBenchmarkId.value }
    if (answerProviderId.value) {
      const [pid, mid] = answerProviderId.value.split(':')
      params.answerProviderId = pid
      params.answerModelId = mid
    }
    if (judgeProviderId.value) {
      const [pid, mid] = judgeProviderId.value.split(':')
      params.judgeProviderId = pid
      params.judgeModelId = mid
    }
    await runEvaluation(props.knowledgeId, params)
    message.success('评估任务已提交，请在任务中心查看进度')
    loadResults()
  } catch (e) {
    message.error('启动评估失败: ' + (e.message || '未知错误'))
  } finally {
    runLoading.value = false
  }
}

function viewDetail(record) {
  selectedResult.value = record
  detailVisible.value = true
}

async function handleDeleteResult(record) {
  try {
    await deleteEvalResult(props.knowledgeId, record.id)
    message.success('已删除')
    loadResults()
  } catch (e) {
    message.error('删除失败')
  }
}

function handleResultTableChange(pag) {
  resultPagination.value.current = pag.current
  resultPagination.value.pageSize = pag.pageSize
  loadResults()
}

onMounted(() => {
  loadBenchmarks()
  loadResults()
})
</script>

<style scoped>
.rag-eval-tab { display: flex; flex-direction: column; gap: 12px; height: 100%; }
.eval-desc { color: #999; font-size: 12px; line-height: 1.6; padding: 0 2px; }
.eval-toolbar { display: flex; justify-content: space-between; align-items: center; }
.eval-toolbar-right { display: flex; gap: 8px; align-items: center; }
.eval-model-row { padding: 0 2px; }
.eval-model-item { display: flex; flex-direction: column; gap: 4px; }
.eval-model-label { font-size: 13px; color: #374151; font-weight: 500; }
.eval-history { flex: 1; overflow-y: auto; }
.eval-history-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.eval-history-title { font-weight: 500; font-size: 14px; }
.action-icon { cursor: pointer; color: #666; font-size: 14px; }
.action-icon:hover { color: #1890ff; }
.action-danger:hover { color: #ff4d4f; }
</style>
